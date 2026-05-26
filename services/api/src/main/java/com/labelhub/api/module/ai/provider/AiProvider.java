package com.labelhub.api.module.ai.provider;

import java.time.Duration;

public interface AiProvider {
    String providerName();
    String modelName();
    AiCallResult invoke(AiCallRequest request);

    default Duration timeout() {
        return Duration.ofSeconds(30);
    }

    default ProviderInvocationResult invokeWithUsage(AiCallRequest request) {
        return new ProviderInvocationResult(invoke(request), null);
    }
}
