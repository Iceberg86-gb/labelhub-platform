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
 * ADR-021 end to end against a real MySQL: only human verdicts of the same task and schema version become
 * precedents (pending, policy-accepted, same-item and other-task submissions never do), senior resolutions win,
 * the context carries the rendered block, and the echoed precedents are audited into ai_calls.request_payload.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PrecedentMemoryIntegrationTest {

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
        registry.add("labelhub.ai.precedents.enabled", () -> "true");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private final String owner = tokenForUser(1001L, "owner_demo", "OWNER");

    @Test
    void precedents_are_human_verdicts_retrieved_rendered_and_audited() throws Exception {
        Fixture fx = taskFixture("precedent-e2e");
        saveAndPublishRule(fx.taskId());
        Fixture other = taskFixture("precedent-other-task");

        long approved = submission(fx, null, "battery life is great and lasts two days");
        humanVerdict(approved, fx.taskId(), "approve");
        long rejected = submission(fx, null, "battery drains fast, the screen is dim");
        humanVerdict(rejected, fx.taskId(), "reject");
        long overturned = submission(fx, null, "battery life great says nothing about the camera");
        long overturnEntry = humanVerdict(overturned, fx.taskId(), "approve");
        seniorCase(overturned, fx.taskId(), overturnEntry, "resolved", "overturn_to_reject");
        long pending = submission(fx, null, "battery life is great great great");
        long pendingEntry = humanVerdict(pending, fx.taskId(), "approve");
        seniorCase(pending, fx.taskId(), pendingEntry, "open", null);
        long policyOnly = submission(fx, null, "battery life is great, admitted by policy");
        ledger(policyOnly, fx.taskId(), "policy_auto_accept", "system");
        long aiOnly = submission(fx, null, "battery life is great, ai said so");
        ledger(aiOnly, fx.taskId(), "ai_overall_recommendation", "ai");
        long foreign = submission(other, null, "battery life is great and lasts two days");
        humanVerdict(foreign, other.taskId(), "approve");

        long target = submission(fx, null, "battery life is great");
        long sameItem = submission(fx, itemOf(target), "battery life is great");
        humanVerdict(sameItem, fx.taskId(), "approve");

        // Context: precedents in rank order, block in the rendered prompt, precedent adapter version.
        JsonNode context = objectMapper.readTree(mockMvc.perform(get("/internal/ai-review/submissions/{id}/context", target)
                .header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<Long> ids = new ArrayList<>();
        context.get("precedents").forEach(p -> ids.add(p.get("submissionId").asLong()));
        assertThat(ids).hasSize(3).containsExactlyInAnyOrder(approved, rejected, overturned)
            .doesNotContain(pending, policyOnly, aiOnly, foreign, sameItem, target);
        assertThat(ids.get(0)).isEqualTo(approved);
        for (JsonNode p : context.get("precedents")) {
            if (p.get("submissionId").asLong() == overturned) {
                assertThat(p.get("verdict").asText()).isEqualTo("rejected");
                assertThat(p.get("source").asText()).isEqualTo("senior");
            }
            if (p.get("submissionId").asLong() == approved) {
                assertThat(p.get("verdict").asText()).isEqualTo("approved");
                assertThat(p.get("source").asText()).isEqualTo("reviewer");
                assertThat(p.get("submission").asText()).contains("answerPayload.answer: battery life is great and lasts two days");
            }
        }
        assertThat(context.get("precedentVersion").asText()).isEqualTo(PrecedentBlock.VERSION);
        assertThat(context.get("providerAdapterVersion").asText()).isEqualTo("agent-default-v1+h4-precedents-v1");
        assertThat(context.get("renderedPrompt").asText()).contains(PrecedentBlock.HEADER);
        assertThat(context.get("idempotencyKey").asText()).contains("adapter:agent-default-v1:");  // key unchanged by ADR-021

        // The read-only endpoint (backing the H5 MCP tool) returns the same retrieval.
        mockMvc.perform(get("/internal/ai-review/submissions/{id}/precedents", target).param("k", "1")
                .header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.precedents.length()").value(1))
            .andExpect(jsonPath("$.precedents[0].submissionId").value(approved))
            .andExpect(jsonPath("$.version").value(PrecedentBlock.VERSION));
        mockMvc.perform(get("/internal/ai-review/submissions/{id}/precedents", target))
            .andExpect(status().isUnauthorized());

        // A tampered echo is refused and nothing is recorded.
        ObjectNode tampered = resultBody(target, context);
        ((ObjectNode) tampered.get("precedents").get(0)).put("verdict", context.get("precedents").get(0).get("verdict").asText().equals("approved") ? "rejected" : "approved");
        mockMvc.perform(post("/internal/ai-review/results").header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(tampered)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("AI_REVIEW_INVALID_PRECEDENTS"));
        assertThat(count("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", target)).isZero();

        // The faithful echo is recorded with the precedents the model saw.
        mockMvc.perform(post("/internal/ai-review/results").header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(resultBody(target, context))))
            .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT provider_adapter_version FROM ai_calls WHERE submission_id=?", String.class, target))
            .isEqualTo("agent-default-v1+h4-precedents-v1");
        JsonNode request = objectMapper.readTree(jdbcTemplate.queryForObject(
            "SELECT CAST(request_payload AS CHAR) FROM ai_calls WHERE submission_id=?", String.class, target));
        assertThat(request.get("precedentsHash").asText()).isEqualTo(context.get("precedentsHash").asText());
        assertThat(request.get("precedents").get(0).get("submissionId").asLong()).isEqualTo(approved);
        assertThat(request.get("renderedPrompt").asText()).isEqualTo(context.get("renderedPrompt").asText());
    }

    private ObjectNode resultBody(long submissionId, JsonNode context) throws Exception {
        ObjectNode body = (ObjectNode) objectMapper.readTree("""
            {"submissionId":%d,"idempotencyKey":"%s","result":{
              "overallSuggestion":"pass","finalScore":0.9,
              "dimensionScores":[{"dimension":"overall","score":0.9,"reason":"r"}],
              "summary":"s","fieldFindings":[],"rawResponse":"{}","tokenInput":1,"tokenOutput":1,"latencyMs":1,
              "modelProvider":"openai-compatible","modelName":"m",
              "responsePayload":{"overallSuggestion":"pass","agentLoop":{"outcome":"valid","steps":1}}}}
            """.formatted(submissionId, context.get("idempotencyKey").asText()));
        body.set("precedents", context.get("precedents").deepCopy());
        body.put("precedentsHash", context.get("precedentsHash").asText());
        return body;
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
