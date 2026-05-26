package com.labelhub.api.module.ai.provider;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatiblePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    void timeout_and_retry_config_binds_under_openai_compatible_only() {
        contextRunner
            .withPropertyValues(
                "labelhub.ai.openai-compatible.base-url=http://127.0.0.1:1/v1",
                "labelhub.ai.openai-compatible.api-key=test-key",
                "labelhub.ai.openai-compatible.model-name=deepseek-v4-flash",
                "labelhub.ai.openai-compatible.provider-name=deepseek",
                "labelhub.ai.openai-compatible.estimated-cost-per-call=0.001",
                "labelhub.ai.openai-compatible.timeout=60s",
                "labelhub.ai.openai-compatible.max-attempts=5",
                "labelhub.ai.openai-compatible.base-delay=250ms",
                "labelhub.ai.providers.openai-compatible.timeout=99s"
            )
            .run(context -> {
                OpenAiCompatibleProperties properties = context.getBean(OpenAiCompatibleProperties.class);
                assertThat(properties.resolvedTimeout()).isEqualTo(Duration.ofSeconds(60));
                assertThat(properties.resolvedMaxAttempts()).isEqualTo(5);
                assertThat(properties.resolvedBaseDelay()).isEqualTo(Duration.ofMillis(250));
            });
    }

    @Configuration
    @EnableConfigurationProperties(OpenAiCompatibleProperties.class)
    static class TestConfig {
    }
}
