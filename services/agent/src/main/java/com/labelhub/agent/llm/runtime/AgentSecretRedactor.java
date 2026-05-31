package com.labelhub.agent.llm.runtime;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

public final class AgentSecretRedactor {

    private AgentSecretRedactor() {
    }

    public static String redactText(String value, Collection<String> secrets) {
        if (value == null || value.isBlank() || secrets == null || secrets.isEmpty()) {
            return value;
        }
        String redacted = value;
        for (String secret : secrets) {
            if (secret != null && !secret.isBlank()) {
                redacted = redacted.replace(secret, "[REDACTED]");
            }
        }
        return redacted;
    }

    public static boolean isSensitiveKey(String key) {
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
            || normalized.contains("authorization")
            || normalized.contains("bearer")
            || normalized.contains("api_key")
            || normalized.contains("apikey");
    }

    public static boolean containsSensitiveKey(Map<String, ?> payload) {
        return payload != null && payload.keySet().stream().anyMatch(AgentSecretRedactor::isSensitiveKey);
    }
}
