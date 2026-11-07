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
    Map<String, Object> responsePayload,
    List<com.labelhub.api.module.ai.precedent.Precedent> precedents,
    String precedentsHash
) {

    /** Without precedent memory (ADR-021 off, or an agent that predates it). */
    public InternalAiReviewResultCommand(Long submissionId, String idempotencyKey, String recommendation, BigDecimal finalScore,
                                         List<DimensionScoreValue> dimensionScores, String summary, List<FieldFinding> fieldFindings,
                                         String rawResponse, Integer tokenInput, Integer tokenOutput, AiCallUsage usage, Integer latencyMs,
                                         String modelProvider, String modelName, Map<String, Object> responsePayload) {
        this(submissionId, idempotencyKey, recommendation, finalScore, dimensionScores, summary, fieldFindings, rawResponse, tokenInput,
            tokenOutput, usage, latencyMs, modelProvider, modelName, responsePayload, null, null);
    }
}
