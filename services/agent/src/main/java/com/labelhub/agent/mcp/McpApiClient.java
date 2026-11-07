package com.labelhub.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** ADR-022: API calls the MCP server makes besides the AI review ones (AiReviewApiClient). */
public interface McpApiClient {

    record Grant(Long tokenId, Long taskId, String name) {
    }

    /** @throws McpAuthException UNAUTHORIZED (bad/revoked/expired token) or NOT_FOUND (submission outside the task). */
    Grant authorize(String token, Long submissionId);

    JsonNode openSubmissions(Long taskId, int limit);

    void appendStep(Map<String, Object> step);
}
