package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.AiPricingProperties;
import com.labelhub.api.module.ai.provider.OpenAiCompatibleProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallCostCalculatorTest {

    private final AiCallCostCalculator calculator = new AiCallCostCalculator(
        pricingProperties(Map.of(
            "deepseek-v4-flash",
            new AiPricingProperties.ModelPricing(
                new BigDecimal("0.0028"),
                new BigDecimal("0.14"),
                new BigDecimal("0.28")
            )
        )),
        new OpenAiCompatibleProperties("", "", "deepseek-v4-flash", "deepseek", new BigDecimal("0.001"))
    );

    @Test
    void computes_cost_when_prompt_and_completion_present_no_cache() {
        BigDecimal cost = calculator.computeCost(
            "deepseek-v4-flash",
            new AiCallUsage(1000, 500, 1500, null)
        );

        assertThat(cost).isEqualByComparingTo("0.000280");
    }

    @Test
    void computes_cost_with_cache_hit_split() {
        BigDecimal cost = calculator.computeCost(
            "deepseek-v4-flash",
            new AiCallUsage(1000, 500, 1500, 300)
        );

        assertThat(cost).isEqualByComparingTo("0.000239");
    }

    @Test
    void falls_back_when_prompt_tokens_null() {
        BigDecimal cost = calculator.computeCost(
            "deepseek-v4-flash",
            new AiCallUsage(null, 500, null, null)
        );

        assertThat(cost).isEqualByComparingTo("0.001");
    }

    @Test
    void falls_back_when_completion_tokens_null() {
        BigDecimal cost = calculator.computeCost(
            "deepseek-v4-flash",
            new AiCallUsage(1000, null, null, null)
        );

        assertThat(cost).isEqualByComparingTo("0.001");
    }

    @Test
    void falls_back_when_usage_null() {
        BigDecimal cost = calculator.computeCost("deepseek-v4-flash", null);

        assertThat(cost).isEqualByComparingTo("0.001");
    }

    @Test
    void falls_back_when_pricing_config_missing_for_model() {
        BigDecimal cost = calculator.computeCost(
            "unpriced-model",
            new AiCallUsage(1000, 500, 1500, 300)
        );

        assertThat(cost).isEqualByComparingTo("0.001");
    }

    @Test
    void small_cache_hit_rounds_to_zero_acceptable_loss() {
        BigDecimal cost = calculator.computeCost(
            "deepseek-v4-flash",
            new AiCallUsage(30, 10, 40, 30)
        );

        assertThat(cost).isEqualByComparingTo("0.000003");
    }

    private static AiPricingProperties pricingProperties(
        Map<String, AiPricingProperties.ModelPricing> models
    ) {
        return new AiPricingProperties("USD", models);
    }
}
