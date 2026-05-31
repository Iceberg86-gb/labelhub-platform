package com.labelhub.agent.llm.runtime;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLlmSecretDecryptorTest {

    private static final String MASTER_KEY = "0123456789abcdef0123456789abcdef";
    private static final byte[] NONCE = "123456789012".getBytes(StandardCharsets.UTF_8);

    @Test
    void decrypts_batch_a_v1_ciphertext_format() {
        String ciphertext = encryptFixture("sk-live-secret-1234");

        AgentLlmSecretDecryptor decryptor = new AgentLlmSecretDecryptor(MASTER_KEY);

        assertThat(decryptor.decrypt(ciphertext)).isEqualTo("sk-live-secret-1234");
    }

    @Test
    void rejects_missing_or_short_master_key() {
        assertThatThrownBy(() -> new AgentLlmSecretDecryptor("short"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LABELHUB_LLM_PROVIDER_MASTER_KEY");
    }

    @Test
    void decrypt_failure_message_does_not_include_ciphertext_or_plaintext_key() {
        AgentLlmSecretDecryptor decryptor = new AgentLlmSecretDecryptor(MASTER_KEY);

        assertThatThrownBy(() -> decryptor.decrypt("v1:not-base64:not-base64"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageNotContaining("not-base64")
            .hasMessageNotContaining("sk-live");
    }

    private static String encryptFixture(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sha256(MASTER_KEY), "AES"), new GCMParameterSpec(128, NONCE));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(NONCE) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] sha256(String value) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }
}
