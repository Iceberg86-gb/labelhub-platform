package com.labelhub.api.module.ai.providerconfig;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates an admin-supplied LLM provider base URL to mitigate SSRF. The server later sends the
 * provider's API secret to this URL, so it must not be coercible into reaching internal services or
 * cloud-metadata endpoints. Rejects non-http(s) schemes, embedded credentials, and hosts that
 * resolve to loopback / private / link-local (incl. 169.254 metadata) / CGNAT / IPv6-ULA addresses.
 *
 * <p>Best-effort by design: it resolves DNS at validation time (a rebinding attacker could still
 * flip records before the actual outbound request — closing that needs IP pinning, out of scope).
 * It deliberately ALLOWS hostnames that do not resolve so offline/CI configs and not-yet-provisioned
 * hosts remain usable. Throws {@link InvalidLlmProviderConfigException} (HTTP 400) on rejection.
 */
public final class LlmProviderBaseUrlValidator {

    private LlmProviderBaseUrlValidator() {
    }

    /** No-op when {@code baseUrl} is null/blank (the field is optional); otherwise validates it. */
    public static void validate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidLlmProviderConfigException("baseUrl is not a valid URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidLlmProviderConfigException("baseUrl must use http or https");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidLlmProviderConfigException("baseUrl must not contain embedded credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidLlmProviderConfigException("baseUrl host is required");
        }

        InetAddress[] addresses;
        try {
            // getAllByName handles hostnames (DNS), IP literals, and numeric forms (e.g. decimal
            // 2130706433 -> 127.0.0.1) uniformly; every resolved address is checked.
            addresses = InetAddress.getAllByName(stripBrackets(host));
        } catch (UnknownHostException ex) {
            return;
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new InvalidLlmProviderConfigException(
                    "baseUrl must not target a private, loopback, link-local, or metadata address");
            }
        }
    }

    private static String stripBrackets(String host) {
        if (host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (b.length == 4) {
            // 100.64.0.0/10 CGNAT — not covered by isSiteLocalAddress().
            return (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
        }
        if (b.length == 16) {
            // fc00::/7 IPv6 unique-local — not covered by isSiteLocalAddress() for IPv6.
            return (b[0] & 0xfe) == 0xfc;
        }
        return false;
    }
}
