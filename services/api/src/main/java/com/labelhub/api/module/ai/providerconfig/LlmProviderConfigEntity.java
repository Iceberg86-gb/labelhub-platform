package com.labelhub.api.module.ai.providerconfig;

import java.time.LocalDateTime;

public class LlmProviderConfigEntity {

    private Long id;
    private Long ownerId;
    private String providerType;
    private String providerName;
    private String baseUrl;
    private String modelName;
    private String secretCiphertext;
    private String secretLast4;
    private LocalDateTime secretUpdatedAt;
    private String secretRef;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getSecretCiphertext() { return secretCiphertext; }
    public void setSecretCiphertext(String secretCiphertext) { this.secretCiphertext = secretCiphertext; }
    public String getSecretLast4() { return secretLast4; }
    public void setSecretLast4(String secretLast4) { this.secretLast4 = secretLast4; }
    public LocalDateTime getSecretUpdatedAt() { return secretUpdatedAt; }
    public void setSecretUpdatedAt(LocalDateTime secretUpdatedAt) { this.secretUpdatedAt = secretUpdatedAt; }
    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean hasSecret() {
        return hasText(secretCiphertext) || hasText(secretRef);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
