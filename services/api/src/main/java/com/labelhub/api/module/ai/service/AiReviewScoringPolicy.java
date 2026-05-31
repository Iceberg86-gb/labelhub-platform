package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.provider.AiCallResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiReviewScoringPolicy {

    private final AiReviewScoringProperties properties;

    public AiReviewScoringPolicy(AiReviewScoringProperties properties) {
        this.properties = properties;
    }

    public ScoredAiReview score(AiCallResult result, List<String> configuredDimensions, BigDecimal configuredThreshold) {
        BigDecimal passThreshold = configuredThreshold == null ? properties.getDefaultThreshold() : configuredThreshold;
        return score(result, configuredDimensions, passThreshold, properties.getRejectFloor());
    }

    public ScoredAiReview score(
        AiCallResult result,
        List<String> configuredDimensions,
        BigDecimal configuredPassThreshold,
        BigDecimal configuredRejectThreshold
    ) {
        List<String> dimensions = configuredDimensions == null || configuredDimensions.isEmpty()
            ? List.of("overall")
            : configuredDimensions;
        BigDecimal passThreshold = configuredPassThreshold == null ? properties.getDefaultThreshold() : configuredPassThreshold;
        BigDecimal rejectThreshold = configuredRejectThreshold == null ? properties.getRejectFloor() : configuredRejectThreshold;
        Map<String, DimensionScoreValue> providerScores = providerScores(result.output().get("dimensionScores"));
        List<DimensionScoreValue> scoredDimensions = new ArrayList<>();
        for (String dimension : dimensions) {
            DimensionScoreValue providerScore = providerScores.get(dimension);
            if (providerScore != null) {
                scoredDimensions.add(providerScore);
            } else {
                BigDecimal fallback = result.confidence() == null ? BigDecimal.ZERO : result.confidence();
                scoredDimensions.add(new DimensionScoreValue(dimension, clamp(fallback), "provider omitted dimension score"));
            }
        }
        BigDecimal finalScore = average(scoredDimensions);
        return new ScoredAiReview(
            recommendation(finalScore, passThreshold, rejectThreshold),
            finalScore,
            passThreshold,
            rejectThreshold,
            properties.getScoringRuleVersion(),
            scoredDimensions
        );
    }

    private Map<String, DimensionScoreValue> providerScores(Object value) {
        Map<String, DimensionScoreValue> scores = new LinkedHashMap<>();
        if (!(value instanceof List<?> rows)) {
            return scores;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String dimension = stringValue(map.get("dimension"));
            if (dimension == null || dimension.isBlank()) {
                continue;
            }
            scores.put(dimension, new DimensionScoreValue(
                dimension,
                clamp(decimalValue(map.get("score"))),
                stringValue(map.get("reason"))
            ));
        }
        return scores;
    }

    private BigDecimal average(List<DimensionScoreValue> scores) {
        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (DimensionScoreValue score : scores) {
            sum = sum.add(score.score());
        }
        return sum.divide(new BigDecimal(scores.size()), 4, RoundingMode.HALF_UP);
    }

    private String recommendation(BigDecimal finalScore, BigDecimal passThreshold, BigDecimal rejectThreshold) {
        if (finalScore.compareTo(passThreshold) >= 0) {
            return "pass";
        }
        if (finalScore.compareTo(rejectThreshold) <= 0) {
            return "reject";
        }
        return "manual_review";
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
