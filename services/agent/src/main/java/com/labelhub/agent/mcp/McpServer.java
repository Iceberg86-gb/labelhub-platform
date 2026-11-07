package com.labelhub.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labelhub.agent.llm.runtime.AgentSecretRedactor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ADR-022 §1: a minimal MCP server (JSON-RPC over Streamable HTTP, JSON responses only, tools only).
 * Transport-agnostic: {@link McpHttpRoutes} adapts it to WebFlux. A session (= run) is bound to the token that
 * initialized it; every request re-authorizes that token, and every tools/call appends one trajectory step.
 */
public class McpServer {

    public static final String LATEST_PROTOCOL = "2025-06-18";
    public static final Set<String> SUPPORTED_PROTOCOLS = Set.of("2025-11-25", "2025-06-18", "2025-03-26");
    public static final int SUMMARY_MAX_CODE_POINTS = 500;
    private static final Duration SESSION_IDLE = Duration.ofHours(2);
    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    /** HTTP-level outcome: status, optional JSON body, optional new session id. */
    public record Reply(int status, JsonNode body, String sessionId) {
    }

    static final class Session {
        final String id;
        final Long tokenId;
        final Long taskId;
        final String clientName;
        final String clientVersion;
        final String protocolVersion;
        final AtomicInteger steps = new AtomicInteger();
        volatile Instant lastSeen;

        Session(String id, Long tokenId, Long taskId, String clientName, String clientVersion, String protocolVersion, Instant now) {
            this.id = id;
            this.tokenId = tokenId;
            this.taskId = taskId;
            this.clientName = clientName;
            this.clientVersion = clientVersion;
            this.protocolVersion = protocolVersion;
            this.lastSeen = now;
        }
    }

    private final McpApiClient api;
    private final McpTools tools;
    private final ObjectMapper canonical;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public McpServer(McpApiClient api, McpTools tools, ObjectMapper objectMapper, Clock clock) {
        this.api = api;
        this.tools = tools;
        this.objectMapper = objectMapper;
        this.canonical = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    public Reply handle(String bearer, String sessionId, JsonNode message) {
        if (bearer == null || bearer.isBlank()) {
            return new Reply(401, null, null);
        }
        if (message == null || !message.isObject()) {
            return new Reply(400, error(null, -32600, "expected one JSON-RPC message (batches are not supported)"), null);
        }
        String method = message.path("method").asText(null);
        JsonNode id = message.get("id");
        if (method == null) {
            return new Reply(202, null, null);  // a response from the client: nothing to do
        }
        McpApiClient.Grant grant;
        try {
            grant = api.authorize(bearer, null);
        } catch (McpAuthException ex) {
            recordRefusedCall(sessionId, bearer, method, message.path("params"));
            return new Reply(401, null, null);
        }
        if ("initialize".equals(method)) {
            return initialize(id, message.path("params"), grant);
        }
        expire();
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (sessionId == null) {
            return new Reply(400, error(id, -32600, "missing Mcp-Session-Id"), null);
        }
        if (session == null || !session.tokenId.equals(grant.tokenId())) {
            return new Reply(404, error(id, -32001, "unknown session"), null);
        }
        session.lastSeen = clock.instant();
        if (id == null) {
            return new Reply(202, null, null);  // notifications (e.g. notifications/initialized)
        }
        return switch (method) {
            case "ping" -> new Reply(200, result(id, JsonNodeFactory.instance.objectNode()), null);
            case "tools/list" -> {
                ObjectNode r = JsonNodeFactory.instance.objectNode();
                r.set("tools", objectMapper.valueToTree(tools.definitions()));
                yield new Reply(200, result(id, r), null);
            }
            case "tools/call" -> new Reply(200, callTool(id, message.path("params"), session, bearer), null);
            default -> new Reply(200, error(id, -32601, "method not found: " + method), null);
        };
    }

    public boolean close(String bearer, String sessionId) {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        try {
            if (!api.authorize(bearer, null).tokenId().equals(session.tokenId)) {
                return false;
            }
        } catch (McpAuthException ex) {
            return false;
        }
        sessions.remove(sessionId);
        return true;
    }

    private Reply initialize(JsonNode id, JsonNode params, McpApiClient.Grant grant) {
        String requested = params.path("protocolVersion").asText(LATEST_PROTOCOL);
        String version = SUPPORTED_PROTOCOLS.contains(requested) ? requested : LATEST_PROTOCOL;
        JsonNode info = params.path("clientInfo");
        Session session = new Session(UUID.randomUUID().toString(), grant.tokenId(), grant.taskId(),
            clientName(info.path("name").asText("unknown")), truncate(info.path("version").asText(null), 64), version, clock.instant());
        sessions.put(session.id, session);
        ObjectNode r = JsonNodeFactory.instance.objectNode();
        r.put("protocolVersion", version);
        r.putObject("capabilities").putObject("tools").put("listChanged", false);
        ObjectNode server = r.putObject("serverInfo");
        server.put("name", "labelhub");
        server.put("version", "h5-mcp-v1");
        r.put("instructions", "LabelHub task " + grant.taskId() + " (token '" + grant.name() + "'). You propose AI reviews; humans decide. "
            + "Typical flow: list_submissions -> get_submission / get_guidelines -> validate_review -> submit_review. "
            + "Every call is recorded in an audit trajectory.");
        return new Reply(200, result(id, r), session.id);
    }

    private JsonNode callTool(JsonNode id, JsonNode params, Session session, String bearer) {
        String name = params.path("name").asText("");
        JsonNode args = params.has("arguments") && params.get("arguments").isObject() ? params.get("arguments") : JsonNodeFactory.instance.objectNode();
        if (tools.definitions().stream().noneMatch(t -> name.equals(t.get("name")))) {
            return error(id, -32602, "unknown tool: " + name);
        }
        long started = System.nanoTime();
        Long submissionId = McpTools.submissionIdOf(args);
        McpTools.Outcome outcome;
        String status;
        try {
            if (submissionId != null || !"list_submissions".equals(name)) {
                if (submissionId == null) {
                    throw new IllegalArgumentException("submissionId is required");
                }
                api.authorize(bearer, submissionId);  // task scope, decided by the API
            }
            outcome = tools.call(name, args, new McpApiClient.Grant(session.tokenId, session.taskId, null), session.clientName);
            status = outcome.isError() ? "error" : "ok";
        } catch (McpAuthException ex) {
            if (ex.kind() == McpAuthException.Kind.UNAUTHORIZED) {
                return error(id, -32001, "token revoked or expired");
            }
            outcome = new McpTools.Outcome(message("submission not found in this task"), true, null, "denied_scope", null);
            status = "denied";
        } catch (RuntimeException ex) {
            outcome = new McpTools.Outcome(message(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()), true,
                submissionId, null, null);
            status = "error";
        }
        int latency = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000);
        try {
            record(session, bearer, name, args, outcome, status, latency);
        } catch (RuntimeException ex) {
            log.warn("MCP trajectory append failed for run {}: {}", session.id, ex.getMessage());
            return error(id, -32603, "audit trajectory could not be recorded; the call result is withheld");
        }
        ObjectNode r = JsonNodeFactory.instance.objectNode();
        ArrayNode content = r.putArray("content");
        content.addObject().put("type", "text").put("text", outcome.result().toString());
        if (outcome.result().isObject()) {
            r.set("structuredContent", outcome.result());
        }
        r.put("isError", outcome.isError());
        return result(id, r);
    }

    private void record(Session session, String bearer, String tool, JsonNode args, McpTools.Outcome outcome, String status, int latency) {
        List<String> secrets = List.of(bearer);
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("runId", session.id);
        step.put("stepNo", session.steps.incrementAndGet());
        step.put("tokenId", session.tokenId);
        step.put("taskId", session.taskId);
        if (outcome.submissionId() != null && !"denied".equals(status)) {
            step.put("submissionId", outcome.submissionId());
        }
        step.put("clientName", session.clientName);
        if (session.clientVersion != null) {
            step.put("clientVersion", session.clientVersion);
        }
        step.put("toolName", tool);
        step.put("inputHash", sha256(canonicalJson(args)));
        step.put("inputSummary", summary(canonicalJson(args), secrets));
        step.put("outputHash", sha256(canonicalJson(outcome.result())));
        step.put("outputSummary", summary(canonicalJson(outcome.result()), secrets));
        step.put("status", status);
        if (outcome.policyDecision() != null) {
            step.put("policyDecision", outcome.policyDecision());
        }
        if (outcome.idempotencyKey() != null) {
            step.put("idempotencyKey", outcome.idempotencyKey());
        }
        step.put("latencyMs", latency);
        api.appendStep(step);
    }

    /**
     * A tools/call on a known session whose token was revoked or expired meanwhile: the session still knows the
     * token, so the refusal is audited as a denied step (an unknown token has nothing to attribute it to).
     */
    private void recordRefusedCall(String sessionId, String bearer, String method, JsonNode params) {
        Session session = sessionId == null ? null : sessions.remove(sessionId);
        if (session == null || !"tools/call".equals(method)) {
            return;
        }
        JsonNode args = params.has("arguments") && params.get("arguments").isObject() ? params.get("arguments") : JsonNodeFactory.instance.objectNode();
        try {
            record(session, bearer, params.path("name").asText("unknown"), args,
                new McpTools.Outcome(message("token revoked or expired"), true, null, "token_rejected", null), "denied", 0);
        } catch (RuntimeException ex) {
            log.warn("MCP trajectory append failed for refused call on run {}: {}", session.id, ex.getMessage());
        }
    }

    String canonicalJson(JsonNode node) {
        try {
            return canonical.writeValueAsString(canonical.convertValue(node, Object.class));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String summary(String text, List<String> secrets) {
        return truncate(AgentSecretRedactor.redactText(text, secrets), SUMMARY_MAX_CODE_POINTS);
    }

    static String truncate(String text, int codePoints) {
        if (text == null || text.codePointCount(0, text.length()) <= codePoints) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, codePoints)) + "…";
    }

    /** Client names end up in ai_calls.model_provider as mcp:<client>: keep them short and plain. */
    static String clientName(String raw) {
        String cleaned = raw == null ? "" : raw.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        cleaned = cleaned.isEmpty() ? "unknown" : cleaned;
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void expire() {
        Instant cutoff = clock.instant().minus(SESSION_IDLE);
        sessions.values().removeIf(s -> s.lastSeen.isBefore(cutoff));
    }

    private ObjectNode message(String text) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("error", text);
        return node;
    }

    private static ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode r = JsonNodeFactory.instance.objectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.set("result", result);
        return r;
    }

    private static ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode r = JsonNodeFactory.instance.objectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.putObject("error").put("code", code).put("message", message);
        return r;
    }
}
