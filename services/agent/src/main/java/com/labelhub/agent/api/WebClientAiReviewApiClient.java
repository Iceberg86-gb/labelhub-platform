package com.labelhub.agent.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebClientAiReviewApiClient implements AiReviewApiClient {

    private final WebClient webClient;
    private final String internalToken;

    public WebClientAiReviewApiClient(
        WebClient.Builder builder,
        @Value("${labelhub.api.base-url}") String baseUrl,
        @Value("${labelhub.api.internal-token}") String internalToken
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public AiReviewContext getContext(Long submissionId) {
        return webClient.get()
            .uri("/internal/ai-review/submissions/{submissionId}/context", submissionId)
            .header("X-Internal-Token", internalToken)
            .retrieve()
            .bodyToMono(AiReviewContext.class)
            .block();
    }

    @Override
    public void reportResult(AiReviewResultEnvelope result) {
        webClient.post()
            .uri("/internal/ai-review/results")
            .header("X-Internal-Token", internalToken)
            .bodyValue(result)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}
