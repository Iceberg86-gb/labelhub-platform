package com.labelhub.api.module.ai.service;

import java.math.BigDecimal;
import java.util.List;

public record ScoredAiReview(
    String recommendation,
    BigDecimal finalScore,
    BigDecimal passThreshold,
    BigDecimal rejectThreshold,
    String scoringRuleVersion,
    List<DimensionScoreValue> dimensionScores
) {
    public BigDecimal threshold() {
        return passThreshold;
    }

    public BigDecimal rejectFloor() {
        return rejectThreshold;
    }
}
