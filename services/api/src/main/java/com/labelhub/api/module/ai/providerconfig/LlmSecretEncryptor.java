package com.labelhub.api.module.ai.providerconfig;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LlmSecretEncryptor {

    private static final String ENV_NAME = "LABELHUB_LLM_PROVIDER_MASTER_KEY";
    private static final String VERSION = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public LlmSecretEncryptor(LlmSecretProperties properties) {
        String masterKey = properties == null ? null : properties.masterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(ENV_NAME + " is required for LLM provider secret encryption");
        }
        if (masterKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(ENV_NAME + " must be at least 32 bytes");
        }
        this.keySpec = new SecretKeySpec(sha256(masterKey), "AES");
    }

    public EncryptedSecret encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("LLM provider secret is required");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedSecret(
                VERSION + ":" + base64(nonce) + ":" + base64(encrypted),
                last4(plaintext)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt LLM provider secret", exception);
        }
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

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String last4(String value) {
        String trimmed = value.trim();
        return trimmed.substring(Math.max(0, trimmed.length() - 4));
    }
}
