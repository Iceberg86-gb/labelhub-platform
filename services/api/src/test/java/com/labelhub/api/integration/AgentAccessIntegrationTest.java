package com.labelhub.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.labelhub.api.module.ai.precedent.PrecedentBlock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-022 end to end against a real MySQL: the owner issues a task-scoped MCP token (plaintext once, hash stored),
 * the internal MCP endpoints authorize per call and scope submissions to the task, trajectory steps are
 * append-only and classify submit_review proposals, the viewer is owner-only, and revocation is immediate.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AgentAccessIntegrationTest {

    private static final String JWT_SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";
    private static final String INTERNAL_TOKEN = "dev-internal-token";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("labelhub")
        .withUsername("labelhub")
        .withPassword("labelhub");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("labelhub.security.jwt-secret", () -> JWT_SECRET);
        registry.add("labelhub.security.internal-token", () -> INTERNAL_TOKEN);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private final String owner = tokenForUser(1001L, "owner_demo", "OWNER");

    @Test
    void tokens_authorization_and_trajectory_end_to_end() throws Exception {
        Fixture fx = taskFixture("mcp-e2e");
        saveAndPublishRule(fx.taskId());
        Fixture other = taskFixture("mcp-other");
        long open = submission(fx, null, "an answer to review");
        long foreign = submission(other, null, "another task");

        // Owner issues a token; the plaintext is returned once and only its hash is stored.
        JsonNode issued = objectMapper.readTree(mockMvc.perform(post("/tasks/{taskId}/mcp-tokens", fx.taskId())
                .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"claude code\",\"expiresInDays\":7}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String plaintext = issued.get("plaintext").asText();
        long tokenId = issued.get("token").get("id").asLong();
        assertThat(plaintext).startsWith("lhm_");
        assertThat(count("SELECT COUNT(*) FROM mcp_access_tokens WHERE token_hash=?", sha256(plaintext))).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM mcp_access_tokens WHERE token_prefix=? AND token_hash<>?", plaintext.substring(0, 12), plaintext)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action='mcp_token.issue'")).isEqualTo(1);
        mockMvc.perform(get("/tasks/{taskId}/mcp-tokens", fx.taskId()).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("active"))
            .andExpect(jsonPath("$[0].plaintext").doesNotExist());
        mockMvc.perform(get("/tasks/{taskId}/mcp-tokens", fx.taskId()).header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER"))))
            .andExpect(status().isNotFound());

        // Internal authorize: task scope by the API.
        mockMvc.perform(post("/internal/mcp/authorize").header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"submissionId\":%d}".formatted(plaintext, open)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.taskId").value(fx.taskId())).andExpect(jsonPath("$.tokenId").value(tokenId));
        mockMvc.perform(post("/internal/mcp/authorize").header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"submissionId\":%d}".formatted(plaintext, foreign)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/mcp/authorize").header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"lhm_forged\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_UNAUTHORIZED"));
        mockMvc.perform(post("/internal/mcp/authorize").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(plaintext)))
            .andExpect(status().isUnauthorized());  // no internal token

        mockMvc.perform(get("/internal/mcp/tasks/{taskId}/open-submissions", fx.taskId()).header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].submissionId").value(open)).andExpect(jsonPath("$.length()").value(1));

        // Trajectory: step 1 (get_submission), then the external proposal recorded via the normal results path.
        String run = "run-" + fx.taskId();
        appendStep(run, 1, tokenId, fx.taskId(), open, "get_submission", null, null).andExpect(status().isCreated());
        JsonNode context = objectMapper.readTree(mockMvc.perform(get("/internal/ai-review/submissions/{id}/context", open)
                .header("X-Internal-Token", INTERNAL_TOKEN)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String key = context.get("idempotencyKey").asText();
        mockMvc.perform(post("/internal/ai-review/results").header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"submissionId":%d,"idempotencyKey":"%s","result":{"overallSuggestion":"pass","finalScore":0.9,
                     "dimensionScores":[{"dimension":"overall","score":0.9,"reason":"r"}],"summary":"s","fieldFindings":[],
                     "rawResponse":"{}","tokenInput":0,"tokenOutput":0,"latencyMs":1,"modelProvider":"mcp:claude-code","modelName":"unreported",
                     "responsePayload":{"overallSuggestion":"pass","external":{"client":"claude-code"}}}}
                    """.formatted(open, key)))
            .andExpect(status().isNoContent());
        appendStep(run, 2, tokenId, fx.taskId(), open, "submit_review", "proposal_submitted", key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.policyDecision").value("proposal_recorded"))
            .andExpect(jsonPath("$.aiCallId").isNumber())
            .andExpect(jsonPath("$.inputSummary").value("token lhm_*** in args"));
        // External proposals are AI evidence only: never auto-accepted, never a human verdict.
        assertThat(count("SELECT COUNT(*) FROM quality_ledger_entries WHERE submission_id=? AND evidence_type IN ('policy_auto_accept','reviewer_overall_verdict')", open)).isZero();
        // A step number is used once, a run has one token, and a foreign submission is refused.
        appendStep(run, 2, tokenId, fx.taskId(), open, "ping", null, null).andExpect(status().isConflict());
        appendStep(run, 3, tokenId, fx.taskId(), foreign, "get_submission", null, null).andExpect(status().isUnprocessableEntity());
        assertThat(count("SELECT COUNT(*) FROM agent_trajectory_steps WHERE run_id=?", run)).isEqualTo(2);

        // Viewer: owner only.
        mockMvc.perform(get("/tasks/{taskId}/agent-runs", fx.taskId()).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].runId").value(run))
            .andExpect(jsonPath("$[0].steps").value(2))
            .andExpect(jsonPath("$[0].proposals").value(1))
            .andExpect(jsonPath("$[0].tokenName").value("claude code"))
            .andExpect(jsonPath("$[0].clientName").value("claude-code"));
        mockMvc.perform(get("/agent-runs/{runId}/steps", run).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].toolName").value("submit_review"));
        mockMvc.perform(get("/agent-runs/{runId}/steps", run).header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/agent-runs/{runId}/steps", run).header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isForbidden());

        // Revocation is effective on the next call.
        mockMvc.perform(post("/mcp-tokens/{tokenId}/revoke", tokenId).header("Authorization", bearer(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("revoked"));
        mockMvc.perform(post("/internal/mcp/authorize").header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(plaintext)))
            .andExpect(status().isUnauthorized());
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action='mcp_token.revoke'")).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions appendStep(String run, int stepNo, long tokenId, long taskId, long submissionId,
                                                                          String tool, String decision, String key) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("runId", run).put("stepNo", stepNo).put("tokenId", tokenId).put("taskId", taskId).put("submissionId", submissionId)
            .put("clientName", "claude-code").put("clientVersion", "2.1").put("toolName", tool)
            .put("inputHash", "a".repeat(64)).put("inputSummary", "token " + "lhm_ABCDEFGHIJKLMNOP" + " in args")
            .put("outputHash", "b".repeat(64)).put("outputSummary", "ok").put("status", "ok").put("latencyMs", 3);
        if (decision != null) {
            body.put("policyDecision", decision);
        }
        if (key != null) {
            body.put("idempotencyKey", key);
        }
        return mockMvc.perform(post("/internal/mcp/trajectory-steps").header("X-Internal-Token", INTERNAL_TOKEN)
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private static String sha256(String value) {
        return fixedHash(value);
    }

    private record Fixture(long taskId, long schemaVersionId, long datasetId) {
    }

    private int ordinal = 0;

    private long submission(Fixture fx, Long existingItemId, String answer) {
        ordinal++;
        long labeler = existingItemId == null ? 1002L : 1003L;  // one session per (item, labeler)
        long itemId = existingItemId != null ? existingItemId : insertAndReturnId("""
            INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
            VALUES (?, ?, ?, JSON_OBJECT('q', 'review this phone'), ?)
            """, fx.datasetId(), fx.taskId(), ordinal, fixedHash(fx.taskId() + "-item-" + ordinal));
        long sessionId = insertAndReturnId("""
            INSERT INTO sessions(task_id, dataset_item_id, labeler_id, schema_version_id, claim_snapshot, status, submitted_at)
            VALUES (?, ?, ?, ?, JSON_OBJECT('q', 'x'), 'submitted', NOW(3))
            """, fx.taskId(), itemId, labeler, fx.schemaVersionId());
        return insertAndReturnId("""
            INSERT INTO submissions(session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
                                    answer_payload, content_hash, status, created_at)
            VALUES (?, ?, ?, ?, ?, JSON_OBJECT('answer', ?), ?, 'submitted', NOW(3))
            """, sessionId, fx.taskId(), itemId, labeler, fx.schemaVersionId(), answer, fixedHash(fx.taskId() + "-submission-" + ordinal));
    }

    private long itemOf(long submissionId) {
        return jdbcTemplate.queryForObject("SELECT dataset_item_id FROM submissions WHERE id=?", Long.class, submissionId);
    }

    private long humanVerdict(long submissionId, long taskId, String verdict) {
        return insertAndReturnId("""
            INSERT INTO quality_ledger_entries(submission_id, task_id, evidence_type, actor_type, actor_id, payload, created_at)
            VALUES (?, ?, 'reviewer_overall_verdict', 'reviewer', 1003, JSON_OBJECT('verdict', ?, 'reviewLevel', 'reviewer'), NOW(3))
            """, submissionId, taskId, verdict);
    }

    private void ledger(long submissionId, long taskId, String type, String actorType) {
        insertAndReturnId("""
            INSERT INTO quality_ledger_entries(submission_id, task_id, evidence_type, actor_type, actor_id, payload, created_at)
            VALUES (?, ?, ?, ?, NULL, JSON_OBJECT('verdict', 'approve', 'recommendation', 'pass'), NOW(3))
            """, submissionId, taskId, type, actorType);
    }

    private void seniorCase(long submissionId, long taskId, long entryId, String caseStatus, String resolution) {
        insertAndReturnId("""
            INSERT INTO senior_review_cases(submission_id, task_id, case_key, case_type, source_signal, status,
                                            reviewer_verdict_entry_id, resolution, resolved_at)
            VALUES (?, ?, ?, 'arbitration', 'ai_reviewer_disagreement', ?, ?, ?, ?)
            """, submissionId, taskId, "precedent-case-" + submissionId, caseStatus, entryId, resolution,
            "resolved".equals(caseStatus) ? LocalDateTime.now() : null);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private JsonNode saveAndPublishRule(long taskId) throws Exception {
        JsonNode saved = objectMapper.readTree(mockMvc.perform(post("/ai-review/rules").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"taskId":%d,"promptTemplate":"auto policy %d","dimensions":["overall"],"passThreshold":0.8,"rejectThreshold":0.2}
                    """.formatted(taskId, taskId)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return objectMapper.readTree(mockMvc.perform(post("/ai-review/rules/{ruleId}/publish", saved.get("id").asLong())
                .header("Authorization", bearer(owner)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Fixture taskFixture(String seed) {
        long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'auto policy', '<p>auto</p>', ?, 1000, 0, 'published', 1001)
            """, seed, LocalDateTime.parse("2030-01-01T00:00:00"));
        long schemaId = insertAndReturnId("INSERT INTO label_schemas(task_id, name, owner_id) VALUES (?, ?, 1001)", taskId, seed + " schema");
        long schemaVersionId = insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, 1, JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('stableId','answer','label','答案','type','text'))),
                    JSON_ARRAY('answer'), ?, 'published', NOW(3), NOW(3))
            """, schemaId, fixedHash(seed + "-schema"));
        long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', 1000, 'completed')", taskId);
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?", schemaVersionId, datasetId, taskId);
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, schemaId);
        return new Fixture(taskId, schemaVersionId, datasetId);
    }






    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert did not return a generated key");
        }
        return key.longValue();
    }

    private static String fixedHash(String seed) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tokenForUser(Long userId, String username, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("username", username)
            .claim("roles", List.of(role))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
