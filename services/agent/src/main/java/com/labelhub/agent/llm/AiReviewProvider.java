package com.labelhub.agent.llm;

import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;

public interface AiReviewProvider {

    AiReviewResultPayload review(AiReviewContext context);
}
