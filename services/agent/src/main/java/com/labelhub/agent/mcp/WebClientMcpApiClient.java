package com.labelhub.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
public class WebClientMcpApiClient implements McpApiClient {

    private final WebClient webClient;
    private final String internalToken;

    public WebClientMcpApiClient(
        WebClient.Builder builder,
        @Value("${labelhub.api.base-url}") String baseUrl,
        @Value("${labelhub.api.internal-token}") String internalToken
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    @Override
    public Grant authorize(String token, Long submissionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        if (submissionId != null) {
            body.put("submissionId", submissionId);
        }
        try {
            JsonNode grant = webClient.post().uri("/internal/mcp/authorize").header("X-Internal-Token", internalToken)
                .bodyValue(body).retrieve().bodyToMono(JsonNode.class).block();
            return new Grant(grant.get("tokenId").asLong(), grant.get("taskId").asLong(), grant.path("name").asText());
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new McpAuthException(McpAuthException.Kind.UNAUTHORIZED, "invalid, revoked or expired token");
            }
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new McpAuthException(McpAuthException.Kind.NOT_FOUND, "submission not found");
            }
            throw ex;
        }
    }

    @Override
    public JsonNode openSubmissions(Long taskId, int limit) {
        return webClient.get().uri(b -> b.path("/internal/mcp/tasks/{taskId}/open-submissions").queryParam("limit", limit).build(taskId))
            .header("X-Internal-Token", internalToken).retrieve().bodyToMono(JsonNode.class).block();
    }

    @Override
    public JsonNode precedents(Long submissionId, int k) {
        return webClient.get().uri(b -> b.path("/internal/ai-review/submissions/{id}/precedents").queryParam("k", k).build(submissionId))
            .header("X-Internal-Token", internalToken).retrieve().bodyToMono(JsonNode.class).block();
    }

    @Override
    public void appendStep(Map<String, Object> step) {
        webClient.post().uri("/internal/mcp/trajectory-steps").header("X-Internal-Token", internalToken)
            .bodyValue(step).retrieve().toBodilessEntity().block();
    }
}
