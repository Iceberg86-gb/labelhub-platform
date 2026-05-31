package com.labelhub.agent.llm.runtime;

public record RuntimeProviderSource(
    Source source,
    Long providerConfigId,
    String providerType,
    String providerName,
    String baseUrl,
    String modelName,
    String apiKey
) {

    public enum Source {
        DB,
        ENV
    }

    public static RuntimeProviderSource db(
        Long providerConfigId,
        String providerType,
        String providerName,
        String baseUrl,
        String modelName,
        String apiKey
    ) {
        return new RuntimeProviderSource(
            Source.DB,
            providerConfigId,
            providerType,
            providerName,
            baseUrl,
            modelName,
            apiKey
        );
    }

    public static RuntimeProviderSource env(
        String providerType,
        String providerName,
        String baseUrl,
        String modelName,
        String apiKey
    ) {
        return new RuntimeProviderSource(
            Source.ENV,
            null,
            providerType,
            providerName,
            baseUrl,
            modelName,
            apiKey
        );
    }

    @Override
    public String toString() {
        return "RuntimeProviderSource[source=%s, providerConfigId=%s, providerType=%s, providerName=%s, baseUrl=%s, modelName=%s, hasRuntimeSecret=%s]"
            .formatted(source, providerConfigId, providerType, providerName, baseUrl, modelName, apiKey != null && !apiKey.isBlank());
    }
}
