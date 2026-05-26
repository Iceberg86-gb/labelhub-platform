package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.AiPricingProperties;
import com.labelhub.api.module.ai.provider.OpenAiCompatibleProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class AiCallCostCalculator {

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final int INTERNAL_SCALE = 10;
    private static final int DB_SCALE = 6;

    private final AiPricingProperties pricingProperties;
    private final OpenAiCompatibleProperties providerProperties;

    public AiCallCostCalculator(
        AiPricingProperties pricingProperties,
        OpenAiCompatibleProperties providerProperties
    ) {
        this.pricingProperties = pricingProperties;
        this.providerProperties = providerProperties;
    }

    public BigDecimal computeCost(String modelName, AiCallUsage usage) {
        if (usage == null || usage.promptTokens() == null || usage.completionTokens() == null) {
            return fallbackCost();
        }

        AiPricingProperties.ModelPricing pricing = pricingProperties.forModel(modelName);
        if (pricing == null) {
            return fallbackCost();
        }

        int promptTokens = Math.max(0, usage.promptTokens());
        int completionTokens = Math.max(0, usage.completionTokens());
        int cacheHitTokens = clampCacheHitTokens(usage.cacheHitTokens(), promptTokens);
        int cacheMissTokens = Math.max(0, promptTokens - cacheHitTokens);

        BigDecimal totalCost = tokenCost(cacheHitTokens, pricing.inputCacheHitPer1mUsd())
            .add(tokenCost(cacheMissTokens, pricing.inputCacheMissPer1mUsd()))
            .add(tokenCost(completionTokens, pricing.outputPer1mUsd()));

        return totalCost.setScale(DB_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal tokenCost(int tokens, BigDecimal ratePerMillion) {
        return BigDecimal.valueOf(tokens)
            .multiply(ratePerMillion)
            .divide(MILLION, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private int clampCacheHitTokens(Integer cacheHitTokens, int promptTokens) {
        if (cacheHitTokens == null) {
            return 0;
        }
        return Math.min(promptTokens, Math.max(0, cacheHitTokens));
    }

    private BigDecimal fallbackCost() {
        return providerProperties.resolvedEstimatedCostPerCall();
    }
}
