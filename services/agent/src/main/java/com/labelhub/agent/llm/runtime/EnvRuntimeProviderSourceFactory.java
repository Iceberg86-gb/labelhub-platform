package com.labelhub.agent.llm.runtime;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class EnvRuntimeProviderSourceFactory {

    private final String primaryProvider;
    private final String doubaoEndpoint;
    private final String doubaoApiKey;
    private final String doubaoModel;
    private final String openAiEndpoint;
    private final String openAiApiKey;
    private final String openAiModel;

    public EnvRuntimeProviderSourceFactory(
        @Value("${labelhub.llm.primary-provider:doubao}") String primaryProvider,
        @Value("${labelhub.llm.doubao.endpoint:}") String doubaoEndpoint,
        @Value("${labelhub.llm.doubao.api-key:}") String doubaoApiKey,
        @Value("${labelhub.llm.doubao.model:}") String doubaoModel,
        @Value("${labelhub.llm.openai.endpoint:https://api.openai.com/v1}") String openAiEndpoint,
        @Value("${labelhub.llm.openai.api-key:}") String openAiApiKey,
        @Value("${labelhub.llm.openai.model:gpt-4.1-mini}") String openAiModel
    ) {
        this.primaryProvider = primaryProvider;
        this.doubaoEndpoint = doubaoEndpoint;
        this.doubaoApiKey = doubaoApiKey;
        this.doubaoModel = doubaoModel;
        this.openAiEndpoint = openAiEndpoint;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
    }

    public Optional<RuntimeProviderSource> source() {
        if ("openai".equalsIgnoreCase(primaryProvider)) {
            return source("openai", openAiEndpoint, openAiModel, openAiApiKey);
        }
        if ("doubao".equalsIgnoreCase(primaryProvider)) {
            return source("doubao", doubaoEndpoint, doubaoModel, doubaoApiKey);
        }
        return Optional.empty();
    }

    private Optional<RuntimeProviderSource> source(String providerName, String baseUrl, String modelName, String apiKey) {
        if (!hasText(baseUrl) || !hasText(modelName) || !hasText(apiKey)) {
            return Optional.empty();
        }
        return Optional.of(RuntimeProviderSource.env(
            "openai-compatible",
            providerName,
            baseUrl,
            modelName,
            apiKey
        ));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
