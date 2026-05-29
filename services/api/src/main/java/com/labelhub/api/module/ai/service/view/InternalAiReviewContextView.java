package com.labelhub.api.module.ai.service.view;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record InternalAiReviewContextView(
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
    String scoringRuleVersion,
    String businessPrompt,
    String renderedPrompt
) {
}
