package com.labelhub.api.module.ai.providerconfig;

public record LlmProviderConfigUpdateCommand(
    String providerType,
    String providerName,
    String baseUrl,
    String modelName,
    String secret,
    String secretRef,
    Boolean enabled
) {
}
