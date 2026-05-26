package com.labelhub.api.module.ai.provider;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "labelhub.ai.openai-compatible")
public record OpenAiCompatibleProperties(
    String baseUrl,
    String apiKey,
    String modelName,
    String providerName,
    BigDecimal estimatedCostPerCall,
    Duration timeout,
    Integer maxAttempts,
    Duration baseDelay
) {

    public OpenAiCompatibleProperties(
        String baseUrl,
        String apiKey,
        String modelName,
        String providerName,
        BigDecimal estimatedCostPerCall
    ) {
        this(baseUrl, apiKey, modelName, providerName, estimatedCostPerCall, null, null, null);
    }

    public String resolvedProviderName() {
        return providerName == null || providerName.isBlank() ? "openai-compatible" : providerName;
    }

    public BigDecimal resolvedEstimatedCostPerCall() {
        return estimatedCostPerCall == null ? new BigDecimal("0.001") : estimatedCostPerCall;
    }

    public Duration resolvedTimeout() {
        return timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public int resolvedMaxAttempts() {
        return maxAttempts == null || maxAttempts < 1 ? 3 : maxAttempts;
    }

    public Duration resolvedBaseDelay() {
        return baseDelay == null ? Duration.ofMillis(1000) : baseDelay;
    }
}
