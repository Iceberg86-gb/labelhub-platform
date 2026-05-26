package com.labelhub.api.module.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withUserConfiguration(TestConfig.class);

    @Test
    void default_provider_is_mock_when_property_is_missing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiProvider.class);
            assertThat(context.getBean(AiProvider.class)).isInstanceOf(MockAiProvider.class);
        });
    }

    @Test
    void openai_compatible_provider_is_selected_by_property() {
        contextRunner
            .withPropertyValues(
                "labelhub.ai.active-provider=openai-compatible",
                "labelhub.ai.openai-compatible.base-url=http://127.0.0.1:1/v1",
                "labelhub.ai.openai-compatible.api-key=test-key",
                "labelhub.ai.openai-compatible.model-name=test-model",
                "labelhub.ai.openai-compatible.provider-name=test-provider"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(AiProvider.class);
                assertThat(context.getBean(AiProvider.class)).isInstanceOf(OpenAiCompatibleProvider.class);
            });
    }

    @Test
    void openai_compatible_provider_fails_fast_when_required_config_is_missing() {
        contextRunner
            .withPropertyValues(
                "labelhub.ai.active-provider=openai-compatible",
                "labelhub.ai.openai-compatible.base-url=http://127.0.0.1:1/v1",
                "labelhub.ai.openai-compatible.model-name=test-model"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "labelhub.ai.openai-compatible.api-key must be configured"
                );
            });
    }

    @Configuration
    @Import({AiProviderConfig.class, MockAiProvider.class, OpenAiCompatibleProvider.class})
    static class TestConfig {
    }
}
