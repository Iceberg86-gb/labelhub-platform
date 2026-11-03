package com.labelhub.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.math.BigInteger;
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
 * ADR-020 §3 end to end against a real MySQL: gate refusal -> shadow evidence -> enable -> agent results are
 * auto-accepted or blind-sampled -> queue / verdict / export / blind ledger -> a rejected sample trips the breaker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AutoPolicyIntegrationTest {

    private static final String JWT_SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";
    private static final String INTERNAL_TOKEN = "dev-internal-token";
    private static final String MODEL = "agent-model-x";

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
    @Autowired SubmissionMapper submissionMapper;

    private final String owner = tokenForUser(1001L, "owner_demo", "OWNER");
    private final String reviewer = tokenForUser(1003L, "reviewer_demo", "REVIEWER");
    private final String senior = tokenForUser(1003L, "reviewer_demo", "SENIOR_REVIEWER");

    @Test
    void auto_policy_lifecycle_end_to_end() throws Exception {
        Fixture fx = taskFixture("auto-policy-e2e");
        JsonNode rule = saveAndPublishRule(fx.taskId());
        long ruleId = rule.get("id").asLong();
        long promptVersionId = rule.get("promptVersionId").asLong();
        String enableBody = """
            {"promptVersionId":%d,"modelName":"%s","maxError":0.05}
            """.formatted(promptVersionId, MODEL);

        // 1. No shadow evidence yet: the gate refuses and nothing is written.
        mockMvc.perform(post("/ai-review/rules/{ruleId}/auto-policy/enable", ruleId).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(enableBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AUTO_GATE_NOT_MET"));
        assertThat(count("SELECT COUNT(*) FROM ai_review_auto_policy_events WHERE ai_review_rule_id=?", ruleId)).isZero();

        // 2. Shadow evidence from suggest mode: 300 AI-pass submissions a human approved (Wilson upper ~1.3% <= 5%).
        for (int i = 0; i < 300; i++) {
            long s = submission(fx, "shadow-" + i);
            insertAiCall(s, ruleId, promptVersionId, "pass", "shadow-" + i);
            humanVerdict(s, fx.taskId(), "approve");
        }
        mockMvc.perform(get("/ai-review/rules/{ruleId}/shadow-stats", ruleId).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groups[0].aiPassJudged").value(300));

        // 3. Enable: auto mode, evidence snapshotted in an append-only event.
        String enabled = mockMvc.perform(post("/ai-review/rules/{ruleId}/auto-policy/enable", ruleId).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(enableBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("auto"))
            .andExpect(jsonPath("$.current.action").value("enable"))
            .andExpect(jsonPath("$.current.evidence.aiPassJudged").value(300))
            .andReturn().getResponse().getContentAsString();
        long policyEventId = objectMapper.readTree(enabled).get("current").get("id").asLong();

        // 4. Agent results: find one auto-accepted and one sampled submission (deterministic selector).
        long accepted = -1;
        long sampled = -1;
        List<Long> ineligible = new ArrayList<>();
        for (int i = 0; i < 60 && (accepted < 0 || sampled < 0); i++) {
            long s = submission(fx, "live-" + i);
            reportAgentResult(s, new BigDecimal("0.95"), "valid", MODEL);
            if (isSampled(s, policyEventId)) {
                sampled = sampled < 0 ? s : sampled;
            } else {
                accepted = accepted < 0 ? s : accepted;
            }
        }
        assertThat(accepted).isPositive();
        assertThat(sampled).isPositive();
        long otherModel = submission(fx, "other-model");
        reportAgentResult(otherModel, new BigDecimal("0.95"), "valid", "some-other-model");
        ineligible.add(otherModel);
        long flagged = submission(fx, "flagged");
        reportAgentResult(flagged, new BigDecimal("0.95"), "flagged", MODEL);
        ineligible.add(flagged);

        assertThat(evidence(accepted, "policy_auto_accept")).isEqualTo(1);
        assertThat(evidence(sampled, "policy_auto_sample")).isEqualTo(1);
        for (long s : ineligible) {
            assertThat(evidence(s, "policy_auto_accept") + evidence(s, "policy_auto_sample")).isZero();
        }

        // 5. Verdict: accepted_by_policy, never "approved".
        mockMvc.perform(get("/submissions/{id}/verdict", accepted).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("accepted_by_policy"));

        // 6. Initial review queue: the auto-accepted submission is gone; the sample is there, blind.
        String queue = mockMvc.perform(get("/reviewer/submissions").param("size", "100").param("verdict", "pending")
                .header("Authorization", bearer(reviewer)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> queued = new ArrayList<>();
        JsonNode sampledRow = null;
        for (JsonNode row : objectMapper.readTree(queue).get("items")) {
            queued.add(row.get("id").asLong());
            if (row.get("id").asLong() == sampled) {
                sampledRow = row;
            }
        }
        assertThat(queued).doesNotContain(accepted).contains(sampled).containsAll(ineligible);
        assertThat(sampledRow).isNotNull();
        assertThat(sampledRow.path("aiRecommendation").isMissingNode() || sampledRow.path("aiRecommendation").isNull()).isTrue();

        // 7. Blind ledger: the initial reviewer does not see the AI overall recommendation or the sample marker.
        String blindLedger = mockMvc.perform(get("/submissions/{id}/ledger-entries", sampled).param("size", "100")
                .header("Authorization", bearer(reviewer)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(blindLedger).doesNotContain("ai_overall_recommendation").doesNotContain("policy_auto_sample");
        String seniorLedger = mockMvc.perform(get("/submissions/{id}/ledger-entries", sampled).param("size", "100")
                .header("Authorization", bearer(senior)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(seniorLedger).contains("ai_overall_recommendation").contains("policy_auto_sample");
        String blindProvenance = mockMvc.perform(get("/submissions/{id}/ai-review", sampled).header("Authorization", bearer(reviewer)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(blindProvenance).doesNotContain("\"overallSuggestion\"").doesNotContain("normalizedOutputShape")
            .doesNotContain("\"finalScore\"").contains("\"fieldFindings\"");
        String seniorProvenance = mockMvc.perform(get("/submissions/{id}/ai-review", sampled).header("Authorization", bearer(senior)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(seniorProvenance).contains("\"overallSuggestion\"");

        // 8. Export admission: default excludes the policy-admitted submission; approved_with_policy includes it.
        assertThat(submissionMapper.selectApprovedByTaskOrderedById(fx.taskId())).extracting(SubmissionEntity::getId).doesNotContain(accepted);
        assertThat(submissionMapper.selectPolicyAcceptedByTaskOrderedById(fx.taskId())).extracting(SubmissionEntity::getId).containsExactly(accepted);

        // 9. The reviewer rejects the sample: 1/1 has Wilson lower bound ~0.21 > 0.05 -> breaker trips after commit.
        mockMvc.perform(post("/submissions/{id}/ledger-entries", sampled).header("Authorization", bearer(reviewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entryType":"reviewer_overall_verdict","payload":{"verdict":"reject","reviewLevel":"reviewer","reason":"wrong"}}
                    """))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/ai-review/rules/{ruleId}/auto-policy", ruleId).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("suggest"))
            .andExpect(jsonPath("$.current.action").value("circuit_break"))
            .andExpect(jsonPath("$.current.actorType").value("system"))
            .andExpect(jsonPath("$.samples.humanRejected").value(1));
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action='ai_review.auto_policy_circuit_break'")).isEqualTo(1);

        // 10. After the breaker, new eligible results are no longer auto-accepted; the earlier one keeps its status.
        long afterBreak = submission(fx, "after-break");
        reportAgentResult(afterBreak, new BigDecimal("0.95"), "valid", MODEL);
        assertThat(evidence(afterBreak, "policy_auto_accept") + evidence(afterBreak, "policy_auto_sample")).isZero();
        mockMvc.perform(get("/submissions/{id}/verdict", accepted).header("Authorization", bearer(owner)))
            .andExpect(jsonPath("$.status").value("accepted_by_policy"));

        // 11. Events are append-only history: enable then circuit_break.
        assertThat(jdbcTemplate.queryForList("SELECT action FROM ai_review_auto_policy_events WHERE ai_review_rule_id=? ORDER BY id", String.class, ruleId))
            .containsExactly("enable", "circuit_break");
    }

    @Test
    void auto_policy_endpoints_are_owner_only() throws Exception {
        Fixture fx = taskFixture("auto-policy-authz");
        long ruleId = saveAndPublishRule(fx.taskId()).get("id").asLong();
        mockMvc.perform(get("/ai-review/rules/{ruleId}/auto-policy", ruleId).header("Authorization", bearer(reviewer)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/ai-review/rules/{ruleId}/auto-policy", ruleId).header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/ai-review/rules/{ruleId}/auto-policy/disable", ruleId).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("suggest"));
    }

    // --- helpers ---------------------------------------------------------------------------------------

    private record Fixture(long taskId, long schemaVersionId, long datasetId) {
    }

    private boolean isSampled(long submissionId, long policyEventId) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((submissionId + ":" + policyEventId).getBytes(StandardCharsets.UTF_8));
        BigInteger bucket = new BigInteger(HexFormat.of().formatHex(digest).substring(0, 8), 16);
        return bucket.compareTo(new BigDecimal("0.10").multiply(new BigDecimal(BigInteger.ONE.shiftLeft(32))).toBigInteger()) < 0;
    }

    private void reportAgentResult(long submissionId, BigDecimal score, String loopOutcome, String model) throws Exception {
        JsonNode context = objectMapper.readTree(mockMvc.perform(get("/internal/ai-review/submissions/{id}/context", submissionId)
                .header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String body = """
            {"submissionId":%d,"idempotencyKey":"%s","result":{
              "overallSuggestion":"pass","finalScore":%s,
              "dimensionScores":[{"dimension":"overall","score":%s,"reason":"r"}],
              "summary":"s","fieldFindings":[],"rawResponse":"{}","tokenInput":1,"tokenOutput":1,"latencyMs":1,
              "modelProvider":"openai-compatible","modelName":"%s",
              "responsePayload":{"overallSuggestion":"pass","agentLoop":{"outcome":"%s","steps":1}}}}
            """.formatted(submissionId, context.get("idempotencyKey").asText(), score, score, model, loopOutcome);
        mockMvc.perform(post("/internal/ai-review/results").header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNoContent());
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

    private int ordinal = 0;

    private long submission(Fixture fx, String seed) {
        ordinal++;
        long itemId = insertAndReturnId("""
            INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
            VALUES (?, ?, ?, JSON_OBJECT('q', ?), ?)
            """, fx.datasetId(), fx.taskId(), ordinal, seed, fixedHash(fx.taskId() + "-item-" + seed));
        long sessionId = insertAndReturnId("""
            INSERT INTO sessions(task_id, dataset_item_id, labeler_id, schema_version_id, claim_snapshot, status, submitted_at)
            VALUES (?, ?, 1002, ?, JSON_OBJECT('q', ?), 'submitted', NOW(3))
            """, fx.taskId(), itemId, fx.schemaVersionId(), seed);
        return insertAndReturnId("""
            INSERT INTO submissions(session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
                                    answer_payload, content_hash, status, created_at)
            VALUES (?, ?, ?, 1002, ?, JSON_OBJECT('answer', ?), ?, 'submitted', NOW(3))
            """, sessionId, fx.taskId(), itemId, fx.schemaVersionId(), seed, fixedHash(fx.taskId() + "-submission-" + seed));
    }

    private void insertAiCall(long submissionId, long ruleId, long promptVersionId, String verdict, String seed) {
        insertAndReturnId("""
            INSERT INTO ai_calls(submission_id, purpose, prompt_version, prompt_version_id, ai_review_rule_id, model_provider, model_name,
                                 input_hash, request_payload, response_payload, verdict, token_input, token_output,
                                 cost_decimal, latency_ms, status, idempotency_key, created_at, completed_at)
            VALUES (?, 'submission_review', 'shadow', ?, ?, 'openai-compatible', ?, ?, JSON_OBJECT(), JSON_OBJECT(), ?,
                    0, 0, 0, 1, 'completed', ?, NOW(3), NOW(3))
            """, submissionId, promptVersionId, ruleId, MODEL, fixedHash("in-" + seed + submissionId), verdict, "auto-shadow:" + submissionId);
    }

    private void humanVerdict(long submissionId, long taskId, String verdict) {
        insertAndReturnId("""
            INSERT INTO quality_ledger_entries(submission_id, task_id, evidence_type, actor_type, actor_id, payload, created_at)
            VALUES (?, ?, 'reviewer_overall_verdict', 'reviewer', 1003, JSON_OBJECT('verdict', ?, 'reviewLevel', 'reviewer'), NOW(3))
            """, submissionId, taskId, verdict);
    }

    private int evidence(long submissionId, String type) {
        return count("SELECT COUNT(*) FROM quality_ledger_entries WHERE submission_id=? AND evidence_type=?", submissionId, type);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
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
