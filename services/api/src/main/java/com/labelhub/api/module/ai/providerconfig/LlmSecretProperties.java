package com.labelhub.api.module.ai.providerconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "labelhub.ai.provider-secrets")
public record LlmSecretProperties(String masterKey) {

    @ConstructorBinding
    public LlmSecretProperties {
    }
}
