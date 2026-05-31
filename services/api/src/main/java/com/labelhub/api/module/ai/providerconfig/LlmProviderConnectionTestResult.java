package com.labelhub.api.module.ai.providerconfig;

public record LlmProviderConnectionTestResult(
    boolean ok,
    String providerName,
    String modelName,
    Long latencyMs,
    Integer providerStatus,
    String providerCode,
    String message
) {

    public static LlmProviderConnectionTestResult ok(String providerName, String modelName, long latencyMs) {
        return new LlmProviderConnectionTestResult(true, providerName, modelName, latencyMs, null, null, "Connection test succeeded");
    }

    public static LlmProviderConnectionTestResult failed(
        String providerName,
        String modelName,
        Integer providerStatus,
        String providerCode,
        String message
    ) {
        return new LlmProviderConnectionTestResult(false, providerName, modelName, null, providerStatus, providerCode, message);
    }
}
