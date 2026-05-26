package com.labelhub.api.module.ai.provider;

public interface AiProvider {
    String providerName();
    String modelName();
    AiCallResult invoke(AiCallRequest request);

    default ProviderInvocationResult invokeWithUsage(AiCallRequest request) {
        return new ProviderInvocationResult(invoke(request), null);
    }
}
