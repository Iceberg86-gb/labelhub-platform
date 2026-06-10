package com.labelhub.api.module.ai.providerconfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderBaseUrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1/v1",                       // IPv4 loopback literal
        "http://localhost/v1",                       // resolves to loopback
        "http://[::1]/v1",                           // IPv6 loopback literal
        "http://2130706433/v1",                      // decimal-encoded 127.0.0.1
        "http://169.254.169.254/latest/meta-data",   // link-local cloud metadata
        "http://10.0.0.5/v1",                        // private (site-local)
        "http://192.168.1.10/v1",                    // private (site-local)
        "http://172.16.0.1/v1",                      // private (site-local)
        "http://100.64.0.1/v1",                      // CGNAT (not covered by isSiteLocal)
        "http://0.0.0.0/v1",                         // any-local
    })
    void rejects_internal_targets(String baseUrl) {
        assertThatThrownBy(() -> LlmProviderBaseUrlValidator.validate(baseUrl))
            .isInstanceOf(InvalidLlmProviderConfigException.class);
    }

    @Test
    void rejects_non_http_scheme() {
        assertThatThrownBy(() -> LlmProviderBaseUrlValidator.validate("ftp://example.com/v1"))
            .isInstanceOf(InvalidLlmProviderConfigException.class)
            .hasMessageContaining("http");
    }

    @Test
    void rejects_embedded_credentials() {
        assertThatThrownBy(() -> LlmProviderBaseUrlValidator.validate("https://attacker@169.254.169.254/v1"))
            .isInstanceOf(InvalidLlmProviderConfigException.class);
    }

    @Test
    void rejects_malformed_url() {
        assertThatThrownBy(() -> LlmProviderBaseUrlValidator.validate("not a url"))
            .isInstanceOf(InvalidLlmProviderConfigException.class);
    }

    @Test
    void allows_unresolvable_hostname() {
        // Mirrors the existing provider-config tests (https://api.deepseek.test/v1): a host that does
        // not resolve is allowed so offline/not-yet-provisioned configs stay usable.
        assertThatCode(() -> LlmProviderBaseUrlValidator.validate("https://api.deepseek.test/v1"))
            .doesNotThrowAnyException();
    }

    @Test
    void allows_null_and_blank() {
        assertThatCode(() -> LlmProviderBaseUrlValidator.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> LlmProviderBaseUrlValidator.validate("  ")).doesNotThrowAnyException();
    }
}
