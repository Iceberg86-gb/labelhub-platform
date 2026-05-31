package com.labelhub.api.module.ai.providerconfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class SecretRedactor {

    private SecretRedactor() {
    }

    public static Map<String, Object> redact(Map<String, Object> payload) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        if (payload == null) {
            return redacted;
        }
        payload.forEach((key, value) -> redacted.put(key, isSensitiveKey(key) ? "[REDACTED]" : value));
        return redacted;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        if ("hassecret".equals(normalized)
            || "secretlast4".equals(normalized)
            || "secretupdatedat".equals(normalized)
            || "secretref".equals(normalized)) {
            return false;
        }
        return normalized.contains("secret")
            || normalized.contains("ciphertext")
            || normalized.contains("token")
            || normalized.contains("password")
            || normalized.contains("api_key")
            || normalized.contains("apikey");
    }
}
