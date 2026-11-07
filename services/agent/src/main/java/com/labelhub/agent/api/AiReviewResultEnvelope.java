package com.labelhub.agent.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiReviewResultEnvelope(
    Long submissionId,
    String idempotencyKey,
    AiReviewResultPayload result,
    // ADR-021 §3: the precedents of the context, unchanged (null when precedent memory is off).
    List<Map<String, Object>> precedents,
    String precedentsHash
) {

    public AiReviewResultEnvelope(Long submissionId, String idempotencyKey, AiReviewResultPayload result) {
        this(submissionId, idempotencyKey, result, null, null);
    }

    public static AiReviewResultEnvelope of(AiReviewContext context, AiReviewResultPayload result) {
        return new AiReviewResultEnvelope(context.submissionId(), context.idempotencyKey(), result,
            context.precedentsHash() == null ? null : context.precedents(), context.precedentsHash());
    }
}
