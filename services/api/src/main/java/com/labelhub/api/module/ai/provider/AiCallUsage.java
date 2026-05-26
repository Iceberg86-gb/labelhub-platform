package com.labelhub.api.module.ai.provider;

public record AiCallUsage(
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Integer cacheHitTokens
) {
}
