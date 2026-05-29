package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.FieldFinding;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record InternalAiReviewResultCommand(
    Long submissionId,
    String idempotencyKey,
    String recommendation,
    BigDecimal finalScore,
    List<DimensionScoreValue> dimensionScores,
    String summary,
    List<FieldFinding> fieldFindings,
    String rawResponse,
    Integer tokenInput,
    Integer tokenOutput,
    AiCallUsage usage,
    Integer latencyMs,
    String modelProvider,
    String modelName,
    Map<String, Object> responsePayload
) {
}
