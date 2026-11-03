package com.labelhub.agent.api;

public interface AiReviewApiClient {

    AiReviewContext getContext(Long submissionId);

    void reportResult(AiReviewResultEnvelope result);

    /** ADR-019: validate a candidate record_ai_review output against the submission's review context. Read-only. */
    AiReviewValidation validateReview(Long submissionId, Object output);
}
