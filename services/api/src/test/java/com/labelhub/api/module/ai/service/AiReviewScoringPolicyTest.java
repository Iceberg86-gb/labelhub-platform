package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.provider.AiCallResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewScoringPolicyTest {

    private final AiReviewScoringProperties properties = new AiReviewScoringProperties();
    private final AiReviewScoringPolicy policy = new AiReviewScoringPolicy(properties);

    @Test
    void score_uses_equal_weight_dimension_average_and_threshold_for_pass() {
        AiCallResult result = result(List.of(
            Map.of("dimension", "accuracy", "score", new BigDecimal("0.90")),
            Map.of("dimension", "format", "score", new BigDecimal("0.80"))
        ));

        ScoredAiReview score = policy.score(result, List.of("accuracy", "format"), new BigDecimal("0.80"));

        assertThat(score.finalScore()).isEqualByComparingTo("0.8500");
        assertThat(score.recommendation()).isEqualTo("pass");
        assertThat(score.dimensionScores()).extracting(DimensionScoreValue::dimension)
            .containsExactly("accuracy", "format");
    }

    @Test
    void score_uses_configured_reject_floor_for_reject() {
        properties.setRejectFloor(new BigDecimal("0.30"));
        AiCallResult result = result(List.of(Map.of("dimension", "quality", "score", new BigDecimal("0.20"))));

        ScoredAiReview score = policy.score(result, List.of("quality"), new BigDecimal("0.80"));

        assertThat(score.recommendation()).isEqualTo("reject");
        assertThat(score.rejectFloor()).isEqualByComparingTo("0.30");
    }

    private static AiCallResult result(List<Map<String, Object>> dimensionScores) {
        return new AiCallResult(
            Map.of("dimensionScores", dimensionScores),
            "manual_review",
            new BigDecimal("0.50"),
            "summary",
            List.of(),
            0,
            0,
            BigDecimal.ZERO,
            0,
            null
        );
    }
}
