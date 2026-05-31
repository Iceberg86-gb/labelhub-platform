package com.labelhub.agent.llm.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSecretRedactorTest {

    @Test
    void identifies_runtime_secret_keys_but_allows_safe_secret_metadata() {
        assertThat(AgentSecretRedactor.isSensitiveKey("Authorization")).isTrue();
        assertThat(AgentSecretRedactor.isSensitiveKey("secretCiphertext")).isTrue();
        assertThat(AgentSecretRedactor.isSensitiveKey("apiKey")).isTrue();

        assertThat(AgentSecretRedactor.isSensitiveKey("hasSecret")).isFalse();
        assertThat(AgentSecretRedactor.isSensitiveKey("secretLast4")).isFalse();
        assertThat(AgentSecretRedactor.isSensitiveKey("secretRef")).isFalse();
    }

    @Test
    void redacts_known_secret_values_from_loggable_text() {
        String message = "Authorization: Bearer sk-live-secret-1234";

        assertThat(AgentSecretRedactor.redactText(message, List.of("sk-live-secret-1234")))
            .isEqualTo("Authorization: Bearer [REDACTED]");
    }

    @Test
    void detects_sensitive_keys_in_payload_maps() {
        assertThat(AgentSecretRedactor.containsSensitiveKey(Map.of("apiKey", "secret"))).isTrue();
        assertThat(AgentSecretRedactor.containsSensitiveKey(Map.of("secretLast4", "1234"))).isFalse();
    }
}
