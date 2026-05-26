package com.labelhub.api.module.ai.provider;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "labelhub.ai.pricing")
public record AiPricingProperties(
    String currency,
    Map<String, ModelPricing> models
) {

    public ModelPricing forModel(String modelName) {
        if (modelName == null || models == null) {
            return null;
        }
        return models.get(modelName);
    }

    public record ModelPricing(
        BigDecimal inputCacheHitPer1mUsd,
        BigDecimal inputCacheMissPer1mUsd,
        BigDecimal outputPer1mUsd
    ) {
    }
}
