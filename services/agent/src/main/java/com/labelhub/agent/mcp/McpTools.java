package com.labelhub.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labelhub.agent.api.AiReviewApiClient;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultEnvelope;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.api.AiReviewValidation;
import com.labelhub.agent.api.DimensionScorePayload;
import com.labelhub.agent.api.FieldFindingPayload;
import com.labelhub.agent.llm.runtime.OpenAiCompatibleAiReviewRuntimeClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-022 §3: the MCP tools. Every tool goes through the API's authority (context, validation, scoring,
 * precedents, results); the tools only adapt shapes. An external proposal is AI evidence, exactly like the
 * platform agent's, except that it carries no agentLoop marker and so can never be auto-accepted (ADR-020).
 */
public class McpTools {

    /** What a tool call produced, plus the facts the trajectory needs. */
    public record Outcome(JsonNode result, boolean isError, Long submissionId, String policyDecision, String idempotencyKey) {
    }

    public static final String PROPOSAL_SUBMITTED = "proposal_submitted";
    public static final String PROPOSAL_REJECTED_INVALID = "proposal_rejected_invalid";
    public static final String HANDOFF_TO_HUMAN = "handoff_to_human";

    private final AiReviewApiClient reviewApi;
    private final McpApiClient mcpApi;
    private final ObjectMapper objectMapper;

    public McpTools(AiReviewApiClient reviewApi, McpApiClient mcpApi, ObjectMapper objectMapper) {
        this.reviewApi = reviewApi;
        this.mcpApi = mcpApi;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> definitions() {
        Map<String, Object> submissionId = Map.of("type", "integer", "description", "Submission id from list_submissions");
        return List.of(
            tool("list_submissions", "List submissions of this task that have no AI review yet (oldest first).",
                schema(Map.of("limit", Map.of("type", "integer", "minimum", 1, "maximum", 50)), List.of()), true),
            tool("get_submission", "The data under review: answer payload, dataset item, schema fields, scoring dimensions and thresholds.",
                schema(Map.of("submissionId", submissionId), List.of("submissionId")), true),
            tool("get_guidelines", "The task description and the owner's review criteria (business prompt) for a submission.",
                schema(Map.of("submissionId", submissionId), List.of("submissionId")), true),
            tool("search_precedents", "Earlier submissions of this task with their final HUMAN verdict, most similar first (BM25). Reference data only.",
                schema(Map.of("submissionId", submissionId, "k", Map.of("type", "integer", "minimum", 1, "maximum", 10)), List.of("submissionId")), true),
            tool("validate_review", "Check a candidate review against the submission's rules (dimensions, thresholds, field ids). Read-only.",
                schema(Map.of("submissionId", submissionId, "review", OpenAiCompatibleAiReviewRuntimeClient.aiReviewToolSchema()),
                    List.of("submissionId", "review")), true),
            tool("submit_review", "Submit a review PROPOSAL. It is validated again; if valid it is recorded as AI evidence for the human "
                    + "reviewer (never a verdict, never auto-accepted). Invalid proposals are refused with the errors to fix.",
                schema(Map.of("submissionId", submissionId, "review", OpenAiCompatibleAiReviewRuntimeClient.aiReviewToolSchema(),
                    "model", Map.of("type", "string", "description", "Model you used, for the audit record")), List.of("submissionId", "review")), false),
            tool("request_human", "Hand the submission back to human review without an AI judgement (e.g. you cannot judge it reliably).",
                schema(Map.of("submissionId", submissionId, "reason", Map.of("type", "string", "maxLength", 500)), List.of("submissionId", "reason")), false)
        );
    }

    public static Long submissionIdOf(JsonNode args) {
        JsonNode id = args == null ? null : args.get("submissionId");
        return id == null || !id.canConvertToLong() ? null : id.asLong();
    }

    public Outcome call(String name, JsonNode args, McpApiClient.Grant grant, String clientName) {
        Long submissionId = submissionIdOf(args);
        return switch (name) {
            case "list_submissions" -> ok(mcpApi.openSubmissions(grant.taskId(), args.path("limit").asInt(20)), null);
            case "get_submission" -> {
                AiReviewContext c = reviewApi.getContext(submissionId);
                ObjectNode out = objectMapper.createObjectNode();
                out.put("submissionId", c.submissionId());
                out.set("input", objectMapper.valueToTree(c.input()));
                out.set("dimensions", objectMapper.valueToTree(c.dimensions()));
                out.put("passThreshold", c.passThreshold() == null ? null : c.passThreshold().toPlainString());
                out.put("rejectThreshold", c.rejectThreshold() == null ? null : c.rejectThreshold().toPlainString());
                out.put("scoringRuleVersion", c.scoringRuleVersion());
                out.put("note", "Score every dimension in [0,1]; the API routes by the average: >= passThreshold pass, <= rejectThreshold reject.");
                yield ok(out, submissionId);
            }
            case "get_guidelines" -> {
                AiReviewContext c = reviewApi.getContext(submissionId);
                ObjectNode out = objectMapper.createObjectNode();
                out.set("task", objectMapper.valueToTree(c.input() == null ? null : c.input().get("task")));
                out.put("businessPrompt", c.businessPrompt());
                out.put("promptVersion", c.promptVersion());
                yield ok(out, submissionId);
            }
            case "search_precedents" -> ok(mcpApi.precedents(submissionId, Math.max(1, Math.min(10, args.path("k").asInt(3)))), submissionId);
            case "validate_review" -> ok(objectMapper.valueToTree(reviewApi.validateReview(submissionId, toPlain(args.get("review")))), submissionId);
            case "submit_review" -> submit(args, submissionId, clientName);
            case "request_human" -> {
                ObjectNode out = objectMapper.createObjectNode();
                out.put("acknowledged", true);
                out.put("note", "Recorded in the trajectory. Every submission is reviewed by a human; no AI judgement was recorded.");
                yield new Outcome(out, false, submissionId, HANDOFF_TO_HUMAN, null);
            }
            default -> throw new IllegalArgumentException("unknown tool " + name);
        };
    }

    private Outcome submit(JsonNode args, Long submissionId, String clientName) {
        Object review = toPlain(args.get("review"));
        AiReviewValidation validation = reviewApi.validateReview(submissionId, review);
        if (!validation.isValid()) {
            ObjectNode out = objectMapper.createObjectNode();
            out.put("recorded", false);
            out.put("message", "Proposal refused: fix the listed problems and submit again.");
            out.set("validation", objectMapper.valueToTree(validation));
            return new Outcome(out, true, submissionId, PROPOSAL_REJECTED_INVALID, null);
        }
        JsonNode r = args.get("review");
        AiReviewContext context = reviewApi.getContext(submissionId);
        Map<String, Object> responsePayload = new LinkedHashMap<>(objectMapper.convertValue(r, Map.class));
        // Marks the source; deliberately no "agentLoop": external proposals are never auto-accept eligible.
        responsePayload.put("external", Map.of("protocol", "mcp", "client", clientName));
        String model = args.path("model").asText("");
        AiReviewResultPayload payload = new AiReviewResultPayload(
            r.path("overallSuggestion").asText("manual_review"),
            validation.finalScore() != null ? validation.finalScore() : decimal(r.get("confidence")),
            dimensionScores(r.get("dimensionScores")),
            r.path("summary").asText(""),
            fieldFindings(r.get("fieldFindings")),
            r.toString(),
            0,
            0,
            null,
            0,
            "mcp:" + clientName,
            model.isBlank() ? "unreported" : model,
            responsePayload
        );
        reviewApi.reportResult(new AiReviewResultEnvelope(context.submissionId(), context.idempotencyKey(), payload));
        ObjectNode out = objectMapper.createObjectNode();
        out.put("recorded", true);
        out.put("route", validation.route());
        out.put("finalScore", validation.finalScore() == null ? null : validation.finalScore().toPlainString());
        out.put("message", "Recorded as AI evidence for the human reviewer (or replayed, if this submission already had an AI review).");
        return new Outcome(out, false, submissionId, PROPOSAL_SUBMITTED, context.idempotencyKey());
    }

    private Outcome ok(JsonNode result, Long submissionId) {
        return new Outcome(result, false, submissionId, null, null);
    }

    private Object toPlain(JsonNode node) {
        return node == null ? null : objectMapper.convertValue(node, Object.class);
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.decimalValue();
    }

    private static List<DimensionScorePayload> dimensionScores(JsonNode node) {
        List<DimensionScorePayload> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(d -> out.add(new DimensionScorePayload(d.path("dimension").asText(), decimal(d.get("score")),
                d.hasNonNull("reason") ? d.get("reason").asText() : null)));
        }
        return out;
    }

    private static List<FieldFindingPayload> fieldFindings(JsonNode node) {
        List<FieldFindingPayload> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(f -> out.add(new FieldFindingPayload(f.path("fieldPath").asText(), text(f, "stableId"), text(f, "label"),
                f.path("severity").asText(), f.path("finding").asText(), decimal(f.get("confidence")))));
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema, boolean readOnly) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        tool.put("annotations", Map.of("readOnlyHint", readOnly, "destructiveHint", false, "openWorldHint", false));
        return tool;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }
}
