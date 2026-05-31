package com.labelhub.agent.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WebClientExportApiClient implements ExportApiClient {

    private final WebClient webClient;
    private final String internalToken;

    public WebClientExportApiClient(
        WebClient.Builder builder,
        @Value("${labelhub.api.base-url}") String baseUrl,
        @Value("${labelhub.api.internal-token}") String internalToken
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public void runExportJob(Long exportJobId) {
        webClient.post()
            .uri("/internal/exports/jobs/{exportJobId}/run", exportJobId)
            .header("X-Internal-Token", internalToken)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}
