package com.labelhub.api.module.ai.provider;

public record ProviderInvocationResult(
    AiCallResult result,
    AiCallUsage usage
) {
}
