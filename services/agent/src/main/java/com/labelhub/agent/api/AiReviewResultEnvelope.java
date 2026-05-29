package com.labelhub.agent.api;

public record AiReviewResultEnvelope(
    Long submissionId,
    String idempotencyKey,
    AiReviewResultPayload result
) {
}
