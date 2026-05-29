package com.labelhub.agent.llm;

import com.labelhub.agent.api.AiCallUsagePayload;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.api.DimensionScorePayload;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class FakeAiReviewProvider implements AiReviewProvider {

    @Override
    public AiReviewResultPayload review(AiReviewContext context) {
        List<DimensionScorePayload> scores = context.dimensions().stream()
            .map(dimension -> new DimensionScorePayload(dimension, new BigDecimal("0.90"), "local fake provider"))
            .toList();
        return new AiReviewResultPayload(
            "pass",
            new BigDecimal("0.90"),
            scores,
            "local fake provider",
            List.of(),
            "{\"provider\":\"fake\"}",
            1,
            1,
            new AiCallUsagePayload(1, 1, 2, null),
            1,
            "fake",
            "fake-v1",
            Map.of("overallSuggestion", "pass", "dimensionScores", scores)
        );
    }
}
