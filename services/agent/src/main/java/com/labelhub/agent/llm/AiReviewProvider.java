package com.labelhub.agent.llm;

import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;
import java.util.List;

public interface AiReviewProvider {

    AiReviewResultPayload review(AiReviewContext context);

    /**
     * ADR-019: ask the model again, replaying the conversation with every failed attempt and its validation feedback.
     * Providers that cannot hold a conversation keep the default; the loop then downgrades deterministically.
     */
    default AiReviewResultPayload correct(AiReviewContext context, List<ReviewAttempt> attempts) {
        throw new UnsupportedOperationException("provider does not support correction turns");
    }
}
