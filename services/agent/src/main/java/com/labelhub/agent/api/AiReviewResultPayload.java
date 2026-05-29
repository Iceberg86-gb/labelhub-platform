package com.labelhub.agent.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiReviewResultPayload(
    String overallSuggestion,
    BigDecimal finalScore,
    List<DimensionScorePayload> dimensionScores,
    String summary,
    List<FieldFindingPayload> fieldFindings,
    String rawResponse,
    Integer tokenInput,
    Integer tokenOutput,
    AiCallUsagePayload usage,
    Integer latencyMs,
    String modelProvider,
    String modelName,
    Map<String, Object> responsePayload
) {
}
