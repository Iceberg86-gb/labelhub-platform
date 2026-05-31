package com.labelhub.api.module.ai.providerconfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmSecretEncryptorTest {

    private static final String MASTER_KEY = "dev-only-llm-provider-master-key-32b";

    @Test
    void encrypt_returnsCiphertextAndLast4WithoutPlaintextLeak() {
        LlmSecretEncryptor encryptor = new LlmSecretEncryptor(new LlmSecretProperties(MASTER_KEY));
        String secret = "sk-test-secret-1234567890";

        EncryptedSecret encrypted = encryptor.encrypt(secret);

        assertThat(encrypted.ciphertext()).doesNotContain(secret);
        assertThat(encrypted.last4()).isEqualTo("7890");
        assertThat(encryptor.decrypt(encrypted.ciphertext())).isEqualTo(secret);
    }

    @Test
    void encrypt_usesRandomNonceForEveryWrite() {
        LlmSecretEncryptor encryptor = new LlmSecretEncryptor(new LlmSecretProperties(MASTER_KEY));

        EncryptedSecret first = encryptor.encrypt("sk-same-secret-1234");
        EncryptedSecret second = encryptor.encrypt("sk-same-secret-1234");

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.last4()).isEqualTo("1234");
        assertThat(second.last4()).isEqualTo("1234");
    }

    @Test
    void constructor_rejectsMissingOrWeakMasterKey() {
        assertThatThrownBy(() -> new LlmSecretEncryptor(new LlmSecretProperties("")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LABELHUB_LLM_PROVIDER_MASTER_KEY");

        assertThatThrownBy(() -> new LlmSecretEncryptor(new LlmSecretProperties("too-short")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 bytes");
    }
}
