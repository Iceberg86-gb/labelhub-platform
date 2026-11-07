package com.labelhub.agent.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiReviewContext(
    Long submissionId,
    String idempotencyKey,
    String promptVersion,
    Long promptVersionId,
    Long aiReviewRuleId,
    String providerAdapterVersion,
    Map<String, Object> input,
    String inputHash,
    List<String> dimensions,
    BigDecimal threshold,
    BigDecimal rejectFloor,
    BigDecimal passThreshold,
    BigDecimal rejectThreshold,
    String scoringRuleVersion,
    String businessPrompt,
    // Includes the ADR-021 precedent block when precedents is non-empty; the agent sends it as is.
    String renderedPrompt,
    // ADR-021: echoed back unchanged with the result so the API can audit what the model saw.
    String precedentVersion,
    List<Map<String, Object>> precedents,
    String precedentsHash
) {
}
