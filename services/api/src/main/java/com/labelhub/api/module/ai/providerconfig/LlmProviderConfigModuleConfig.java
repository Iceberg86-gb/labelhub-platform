package com.labelhub.api.module.ai.providerconfig;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmSecretProperties.class)
public class LlmProviderConfigModuleConfig {
}
