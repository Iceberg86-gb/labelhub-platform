package com.labelhub.api.module.ai.provider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenAiCompatibleProperties.class)
public class AiProviderConfig {
}
