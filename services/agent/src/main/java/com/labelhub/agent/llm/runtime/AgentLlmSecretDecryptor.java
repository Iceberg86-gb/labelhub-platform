package com.labelhub.agent.llm.runtime;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class AgentLlmSecretDecryptor {

    private static final String ENV_NAME = "LABELHUB_LLM_PROVIDER_MASTER_KEY";
    private static final String VERSION = "v1";
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;

    public AgentLlmSecretDecryptor(@Value("${labelhub.llm.provider.master-key:}") String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(ENV_NAME + " is required for LLM provider runtime decryption");
        }
        if (masterKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(ENV_NAME + " must be at least 32 bytes");
        }
        this.keySpec = new SecretKeySpec(sha256(masterKey), "AES");
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        String[] parts = ciphertext.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("Unsupported LLM provider secret ciphertext format");
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to decrypt LLM provider secret", exception);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
