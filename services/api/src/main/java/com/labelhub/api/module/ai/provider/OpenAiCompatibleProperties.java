package com.labelhub.api.module.ai.provider;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "labelhub.ai.openai-compatible")
public record OpenAiCompatibleProperties(
    String baseUrl,
    String apiKey,
    String modelName,
    String providerName,
    BigDecimal estimatedCostPerCall
) {

    public String resolvedProviderName() {
        return providerName == null || providerName.isBlank() ? "openai-compatible" : providerName;
    }

    public BigDecimal resolvedEstimatedCostPerCall() {
        return estimatedCostPerCall == null ? new BigDecimal("0.001") : estimatedCostPerCall;
    }
}
