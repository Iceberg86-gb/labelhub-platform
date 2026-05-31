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

    @Test
    void score_uses_task_level_three_zone_thresholds_for_v2_conclusions() {
        AiCallResult middle = result(List.of(
            Map.of("dimension", "accuracy", "score", new BigDecimal("0.70")),
            Map.of("dimension", "format", "score", new BigDecimal("0.60"))
        ));
        AiCallResult pass = result(List.of(Map.of("dimension", "accuracy", "score", new BigDecimal("0.80"))));
        AiCallResult reject = result(List.of(Map.of("dimension", "accuracy", "score", new BigDecimal("0.30"))));

        ScoredAiReview manualReview = policy.score(
            middle,
            List.of("accuracy", "format"),
            new BigDecimal("0.80"),
            new BigDecimal("0.40")
        );
        ScoredAiReview passReview = policy.score(
            pass,
            List.of("accuracy"),
            new BigDecimal("0.80"),
            new BigDecimal("0.40")
        );
        ScoredAiReview rejectReview = policy.score(
            reject,
            List.of("accuracy"),
            new BigDecimal("0.80"),
            new BigDecimal("0.40")
        );

        assertThat(manualReview.finalScore()).isEqualByComparingTo("0.6500");
        assertThat(manualReview.recommendation()).isEqualTo("manual_review");
        assertThat(manualReview.passThreshold()).isEqualByComparingTo("0.80");
        assertThat(manualReview.rejectThreshold()).isEqualByComparingTo("0.40");
        assertThat(manualReview.scoringRuleVersion()).isEqualTo("equal-weight-three-zone-v2");
        assertThat(passReview.recommendation()).isEqualTo("pass");
        assertThat(rejectReview.recommendation()).isEqualTo("reject");
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
