package com.labelhub.agent.api;

public record AiCallUsagePayload(
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens,
    Integer cacheHitTokens
) {
}
