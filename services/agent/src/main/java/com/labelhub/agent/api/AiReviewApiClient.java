package com.labelhub.agent.api;

public interface AiReviewApiClient {

    AiReviewContext getContext(Long submissionId);

    void reportResult(AiReviewResultEnvelope result);
}
