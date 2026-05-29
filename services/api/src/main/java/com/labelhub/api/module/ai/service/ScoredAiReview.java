package com.labelhub.api.module.ai.service;

import java.math.BigDecimal;
import java.util.List;

public record ScoredAiReview(
    String recommendation,
    BigDecimal finalScore,
    BigDecimal threshold,
    BigDecimal rejectFloor,
    String scoringRuleVersion,
    List<DimensionScoreValue> dimensionScores
) {
}
