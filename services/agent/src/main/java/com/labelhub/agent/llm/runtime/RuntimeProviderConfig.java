package com.labelhub.agent.llm.runtime;

public record RuntimeProviderConfig(
    Long id,
    Long ownerId,
    String providerType,
    String providerName,
    String baseUrl,
    String modelName,
    String secretCiphertext,
    String secretRef,
    Boolean enabled
) {

    @Override
    public String toString() {
        return "RuntimeProviderConfig[id=%s, ownerId=%s, providerType=%s, providerName=%s, baseUrl=%s, modelName=%s, hasCiphertext=%s, hasSecretRef=%s, enabled=%s]"
            .formatted(id, ownerId, providerType, providerName, baseUrl, modelName, hasText(secretCiphertext), hasText(secretRef), enabled);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
