package com.labelhub.agent.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.AiReviewApiClient;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultEnvelope;
import com.labelhub.agent.api.AiReviewValidation;
import com.labelhub.agent.api.ReviewValidationError;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * ADR-022 §7 — the H5 gate. The official MCP Java SDK client connects to /mcp (real HTTP, real WebFlux routes)
 * and completes a demo review: initialize -> list_submissions -> get_submission -> search_precedents ->
 * validate_review (invalid) -> submit_review (fixed). The API behind it is an in-memory fake that enforces
 * the same contract (token scope, validation); the test asserts the audit facts the API would persist.
 */
class McpGateTest {

    static final String TOKEN = "lhm_demo-token-abcdefghijklmnopqrstuvwxyz";
    static final long TASK = 44L;
    static final long SUBMISSION = 300L;
    static final long FOREIGN = 999L;

    final ObjectMapper objectMapper = new ObjectMapper();
    final FakeApi api = new FakeApi();
    DisposableServer server;

    @BeforeEach
    void start() {
        McpHttpRoutes routes = new McpHttpRoutes();
        McpServer mcp = routes.mcpServer(api, api, objectMapper, Clock.systemUTC());
        server = HttpServer.create().host("127.0.0.1").port(0)
            .handle(new ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(routes.mcpRoutes(mcp, objectMapper))))
            .bindNow();
    }

    @AfterEach
    void stop() {
        server.disposeNow();
    }

    McpSyncClient client(String token) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + server.port())
            .endpoint("/mcp")
            .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
            .httpRequestCustomizer((builder, method, uri, body, context) -> builder.header("Authorization", "Bearer " + token))
            .build();
        return McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation("Demo Agent", "1.0"))
            .requestTimeout(Duration.ofSeconds(10))
            .build();
    }

    JsonNode call(McpSyncClient client, String tool, Map<String, Object> args, boolean expectError) throws Exception {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(tool, args));
        assertThat(result.isError()).as(tool + " isError").isEqualTo(expectError);
        return objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
    }

    static Map<String, Object> review(boolean valid) {
        return Map.of(
            "overallSuggestion", "pass", "confidence", 0.9, "summary", "clear and correct",
            "dimensionScores", List.of(Map.of("dimension", valid ? "overall" : "style", "score", 0.9)),
            "fieldFindings", List.of());
    }

    @Test
    void external_agent_completes_a_demo_review_under_the_same_audit_rules() throws Exception {
        try (McpSyncClient client = client(TOKEN)) {
            McpSchema.InitializeResult init = client.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("labelhub");
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly(
                "list_submissions", "get_submission", "get_guidelines", "search_precedents", "validate_review", "submit_review", "request_human");

            assertThat(call(client, "list_submissions", Map.of(), false).get(0).get("submissionId").asLong()).isEqualTo(SUBMISSION);
            assertThat(call(client, "get_submission", Map.of("submissionId", SUBMISSION), false).get("dimensions").get(0).asText()).isEqualTo("overall");
            assertThat(call(client, "search_precedents", Map.of("submissionId", SUBMISSION, "k", 2), false).get("precedents").get(0).get("verdict").asText())
                .isEqualTo("approved");
            assertThat(call(client, "validate_review", Map.of("submissionId", SUBMISSION, "review", review(false)), false).get("valid").asBoolean()).isFalse();
            // An invalid proposal is refused and nothing reaches the API's results endpoint.
            JsonNode refused = call(client, "submit_review", Map.of("submissionId", SUBMISSION, "review", review(false)), true);
            assertThat(refused.get("recorded").asBoolean()).isFalse();
            assertThat(api.results).isEmpty();
            JsonNode recorded = call(client, "submit_review", Map.of("submissionId", SUBMISSION, "review", review(true), "model", "demo-model"), false);
            assertThat(recorded.get("recorded").asBoolean()).isTrue();
            // Out-of-task submission: denied by the API's scope check, still audited.
            assertThat(call(client, "get_submission", Map.of("submissionId", FOREIGN), true).get("error").asText()).contains("not found");
        }

        // Audit facts: ai_calls provider mcp:<client>, no agentLoop (never auto-accepted), external marker.
        assertThat(api.results).hasSize(1);
        AiReviewResultEnvelope envelope = api.results.get(0);
        assertThat(envelope.result().modelProvider()).isEqualTo("mcp:demo-agent");
        assertThat(envelope.result().modelName()).isEqualTo("demo-model");
        assertThat(envelope.result().finalScore()).isEqualByComparingTo("0.9");
        assertThat(envelope.result().responsePayload()).doesNotContainKey("agentLoop").containsKey("external");

        // Trajectory: one step per tool call, in order, one run, hashed, summarized, token never stored.
        assertThat(api.steps).extracting(s -> s.get("toolName")).containsExactly(
            "list_submissions", "get_submission", "search_precedents", "validate_review", "submit_review", "submit_review", "get_submission");
        assertThat(api.steps).extracting(s -> s.get("stepNo")).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(api.steps).extracting(s -> s.get("runId")).containsOnly(api.steps.get(0).get("runId"));
        assertThat(api.steps).allSatisfy(s -> {
            assertThat((String) s.get("inputHash")).matches("[a-f0-9]{64}");
            assertThat((String) s.get("outputHash")).matches("[a-f0-9]{64}");
            assertThat(s.get("clientName")).isEqualTo("demo-agent");
            assertThat(s.get("tokenId")).isEqualTo(7L);
            assertThat(String.valueOf(s.get("inputSummary")) + s.get("outputSummary")).doesNotContain(TOKEN);
        });
        assertThat(api.steps.get(4)).containsEntry("policyDecision", "proposal_rejected_invalid").containsEntry("status", "error");
        assertThat(api.steps.get(5)).containsEntry("policyDecision", "proposal_submitted").containsEntry("idempotencyKey", "key-300");
        assertThat(api.steps.get(6)).containsEntry("status", "denied").doesNotContainKey("submissionId");
    }

    @Test
    void revoked_or_wrong_tokens_are_refused() {
        try (McpSyncClient client = client(TOKEN)) {
            client.initialize();
            api.revoked = true;  // revocation takes effect on the next call
            assertThatThrownBy(() -> client.callTool(new McpSchema.CallToolRequest("list_submissions", Map.of()))).isInstanceOf(RuntimeException.class);
        }
        api.revoked = false;
        try (McpSyncClient stranger = client("lhm_not-a-real-token")) {
            assertThatThrownBy(stranger::initialize).isInstanceOf(RuntimeException.class);
        }
        assertThat(api.steps).isEmpty();
    }

    @Test
    void request_human_is_a_recorded_handoff_without_an_ai_judgement() throws Exception {
        try (McpSyncClient client = client(TOKEN)) {
            client.initialize();
            call(client, "request_human", Map.of("submissionId", SUBMISSION, "reason", "domain I cannot judge"), false);
        }
        assertThat(api.results).isEmpty();
        assertThat(api.steps).singleElement().satisfies(s -> assertThat(s).containsEntry("policyDecision", "handoff_to_human"));
    }

    /** In-memory API: token scope, a context, validation (dimension "overall" required) and capture. */
    static final class FakeApi implements AiReviewApiClient, McpApiClient {
        final List<AiReviewResultEnvelope> results = new CopyOnWriteArrayList<>();
        final List<Map<String, Object>> steps = new CopyOnWriteArrayList<>();
        volatile boolean revoked;
        final ObjectMapper om = new ObjectMapper();

        @Override
        public Grant authorize(String token, Long submissionId) {
            if (revoked || !TOKEN.equals(token)) {
                throw new McpAuthException(McpAuthException.Kind.UNAUTHORIZED, "bad token");
            }
            if (submissionId != null && submissionId != SUBMISSION) {
                throw new McpAuthException(McpAuthException.Kind.NOT_FOUND, "not in task");
            }
            return new Grant(7L, TASK, "demo");
        }

        @Override
        public JsonNode openSubmissions(Long taskId, int limit) {
            return om.valueToTree(List.of(Map.of("submissionId", SUBMISSION, "datasetItemId", 1, "createdAt", "2026-11-07T00:00:00Z")));
        }

        @Override
        public JsonNode precedents(Long submissionId, int k) {
            return om.valueToTree(Map.of("version", "h4-precedents-v1", "precedents",
                List.of(Map.of("rank", 1, "submissionId", 41, "verdict", "approved", "source", "reviewer", "submission", "answerPayload.answer: yes"))));
        }

        @Override
        public void appendStep(Map<String, Object> step) {
            steps.add(step);
        }

        @Override
        public AiReviewContext getContext(Long submissionId) {
            return new AiReviewContext(submissionId, "key-" + submissionId, "promptVersion#1", 1L, 9L, "agent-default-v1",
                Map.of("answerPayload", Map.of("answer", "yes"), "task", Map.of("id", TASK, "title", "demo")), "h", List.of("overall"),
                new BigDecimal("0.8"), new BigDecimal("0.2"), new BigDecimal("0.8"), new BigDecimal("0.2"), "equal-weight-three-zone-v2",
                "Judge helpfulness.", "rendered", null, List.of(), null);
        }

        @Override
        public void reportResult(AiReviewResultEnvelope result) {
            results.add(result);
        }

        @Override
        @SuppressWarnings("unchecked")
        public AiReviewValidation validateReview(Long submissionId, Object output) {
            List<Map<String, Object>> dims = (List<Map<String, Object>>) ((Map<String, Object>) output).get("dimensionScores");
            boolean ok = dims.stream().anyMatch(d -> "overall".equals(d.get("dimension")));
            List<ReviewValidationError> errors = new ArrayList<>();
            if (!ok) {
                errors.add(new ReviewValidationError("unknown_dimension", "dimensionScores[0].dimension", "style is not configured"));
            }
            return new AiReviewValidation(ok, "review-output-v1", errors, ok ? "pass" : null, ok ? new BigDecimal("0.9") : null);
        }
    }
}
