package com.labelhub.api.module.ai.providerconfig;

public record LlmProviderConfigCreateCommand(
    String providerType,
    String providerName,
    String baseUrl,
    String modelName,
    String secret,
    String secretRef,
    Boolean enabled
) {
}
