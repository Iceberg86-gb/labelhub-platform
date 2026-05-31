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
    String renderedPrompt
) {
}
