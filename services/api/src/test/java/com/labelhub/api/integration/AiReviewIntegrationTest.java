package com.labelhub.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.provider.MockAiProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AiReviewIntegrationTest {

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

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockAiProvider mockAiProvider;

    @BeforeEach
    void resetMockProvider() {
        mockAiProvider.resetCallCount();
    }

    @Test
    void owner_triggers_ai_review_creates_ai_call_and_field_rows() throws Exception {
        long submissionId = submissionFixture("ai-review-happy");

        mockMvc.perform(post("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"promptVersionId\":1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false))
            .andExpect(jsonPath("$.aiCall.providerName").value("mock"))
            .andExpect(jsonPath("$.aiCall.promptVersionId").value(1))
            .andExpect(jsonPath("$.aiCall.aiReviewRuleId").doesNotExist())
            .andExpect(jsonPath("$.aiCall.idempotencyKey").value(
                "submission:" + submissionId + ":ai_review:promptVersionId:1:adapter:agent-default-v1"
            ))
            .andExpect(jsonPath("$.aiCall.outputHash", hasLength(64)))
            .andExpect(jsonPath("$.fieldFindings", hasSize(1)));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls_in_field WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(1);
    }

    @Test
    void labeler_cannot_trigger_ai_review_returns_403() throws Exception {
        long submissionId = submissionFixture("ai-review-labeler-post");

        mockMvc.perform(post("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"promptVersionId\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void cross_owner_trigger_ai_review_returns_404() throws Exception {
        long submissionId = submissionFixture("ai-review-cross-owner");

        mockMvc.perform(post("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"promptVersionId\":1}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void get_provenance_returns_ai_calls_for_owner() throws Exception {
        long submissionId = reviewedSubmission("ai-review-owner-get");

        mockMvc.perform(get("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissionId").value(submissionId))
            .andExpect(jsonPath("$.aiCalls", hasSize(1)))
            .andExpect(jsonPath("$.aiCalls[0].outputHash", hasLength(64)))
            .andExpect(jsonPath("$.fieldFindings", hasSize(1)));
    }

    @Test
    void get_provenance_returns_ai_calls_for_labeler_own_submission() throws Exception {
        long submissionId = reviewedSubmission("ai-review-labeler-get");

        mockMvc.perform(get("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiCalls", hasSize(1)));
    }

    @Test
    void cross_labeler_get_provenance_returns_404() throws Exception {
        long submissionId = reviewedSubmission("ai-review-cross-labeler");

        mockMvc.perform(get("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(2002L, "other_labeler", "LABELER"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void second_trigger_with_same_prompt_returns_idempotency_hit_without_invoking_provider() throws Exception {
        long submissionId = submissionFixture("ai-review-idempotency");

        triggerReview(submissionId, 1L)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false));
        triggerReview(submissionId, 1L)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(true));

        assertThat(mockAiProvider.getCallCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(1);
    }

    @Test
    void trigger_with_different_prompt_creates_new_ai_call() throws Exception {
        long submissionId = submissionFixture("ai-review-new-prompt");
        long secondPromptVersionId = insertPromptVersion(2, "m3-owner-review-v2");

        triggerReview(submissionId, 1L).andExpect(status().isCreated());
        triggerReview(submissionId, secondPromptVersionId)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(2);
    }

    @Test
    void legacy_prompt_version_ai_call_is_not_reused_by_new_prompt_version_id_key() throws Exception {
        long submissionId = submissionFixture("ai-review-legacy-key");
        String legacyKey = legacyIdempotencyKey(submissionId);
        Long legacyAiCallId = insertLegacyAiCall(submissionId, legacyKey);

        triggerReview(submissionId, 1L)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false))
            .andExpect(jsonPath("$.aiCall.promptVersionId").value(1))
            .andExpect(jsonPath("$.aiCall.providerAdapterVersion").value("agent-default-v1"))
            .andExpect(jsonPath("$.aiCall.idempotencyKey").value(
                "submission:" + submissionId + ":ai_review:promptVersionId:1:adapter:agent-default-v1"
            ));

        assertThat(mockAiProvider.getCallCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT prompt_version_id FROM ai_calls WHERE id=?",
            Long.class,
            legacyAiCallId
        )).isNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT idempotency_key FROM ai_calls WHERE id=?",
            String.class,
            legacyAiCallId
        )).isEqualTo(legacyKey);

        mockMvc.perform(get("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiCalls", hasSize(2)))
            .andExpect(jsonPath("$.aiCalls[0].promptVersion").value("m3-owner-review-v1"))
            .andExpect(jsonPath("$.aiCalls[0].promptVersionId").doesNotExist())
            .andExpect(jsonPath("$.aiCalls[0].providerAdapterVersion").value("agent-default-v1"))
            .andExpect(jsonPath("$.aiCalls[1].promptVersionId").value(1))
            .andExpect(jsonPath("$.aiCalls[1].providerAdapterVersion").value("agent-default-v1"));
    }

    @Test
    void active_rule_overrides_request_prompt_and_binds_rule_evidence() throws Exception {
        long submissionId = submissionFixture("ai-review-active-rule-wins");
        JsonNode publishedRule = saveAndPublishRule(taskIdForSubmission(submissionId), "rule-specific-prompt");
        long ruleId = publishedRule.get("id").asLong();
        long rulePromptVersionId = publishedRule.get("promptVersionId").asLong();

        triggerReview(submissionId, 1L)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false))
            .andExpect(jsonPath("$.aiCall.promptVersionId").value(rulePromptVersionId))
            .andExpect(jsonPath("$.aiCall.aiReviewRuleId").value(ruleId))
            .andExpect(jsonPath("$.aiCall.idempotencyKey").value(
                "submission:" + submissionId + ":ai_review:promptVersionId:"
                    + rulePromptVersionId + ":adapter:agent-default-v1:ruleVersionId:" + ruleId
            ));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT ai_review_rule_id FROM ai_calls WHERE submission_id=?",
            Long.class,
            submissionId
        )).isEqualTo(ruleId);
    }

    @Test
    void no_rule_and_active_rule_keys_are_isolated_for_same_submission() throws Exception {
        long submissionId = submissionFixture("ai-review-rule-key-isolation");

        triggerReview(submissionId, 1L)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.aiCall.aiReviewRuleId").doesNotExist())
            .andExpect(jsonPath("$.aiCall.idempotencyKey").value(
                "submission:" + submissionId + ":ai_review:promptVersionId:1:adapter:agent-default-v1"
            ));

        JsonNode publishedRule = saveAndPublishRule(taskIdForSubmission(submissionId), "rule-key-isolation-prompt");
        long ruleId = publishedRule.get("id").asLong();
        long rulePromptVersionId = publishedRule.get("promptVersionId").asLong();

        triggerReview(submissionId, 1L)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false))
            .andExpect(jsonPath("$.aiCall.promptVersionId").value(rulePromptVersionId))
            .andExpect(jsonPath("$.aiCall.aiReviewRuleId").value(ruleId))
            .andExpect(jsonPath("$.aiCall.idempotencyKey").value(
                "submission:" + submissionId + ":ai_review:promptVersionId:"
                    + rulePromptVersionId + ":adapter:agent-default-v1:ruleVersionId:" + ruleId
            ));

        assertThat(mockAiProvider.getCallCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(2);
    }

    @Test
    void dangling_active_rule_pointer_returns_404() throws Exception {
        long submissionId = submissionFixture("ai-review-dangling-rule");
        long taskId = taskIdForSubmission(submissionId);

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            jdbcTemplate.update("UPDATE tasks SET current_ai_review_rule_id=? WHERE id=?", 999_999L, taskId);
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        }

        triggerReview(submissionId, 1L).andExpect(status().isNotFound());
    }

    @Test
    void provenance_returns_legacy_and_rule_bound_ai_calls_together() throws Exception {
        long submissionId = submissionFixture("ai-review-mixed-provenance");
        String legacyKey = legacyIdempotencyKey(submissionId);
        insertLegacyAiCall(submissionId, legacyKey);
        JsonNode publishedRule = saveAndPublishRule(taskIdForSubmission(submissionId), "mixed-provenance-rule");
        long ruleId = publishedRule.get("id").asLong();

        triggerReview(submissionId, 1L).andExpect(status().isCreated());

        mockMvc.perform(get("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiCalls", hasSize(2)))
            .andExpect(jsonPath("$.aiCalls[0].promptVersion").value("m3-owner-review-v1"))
            .andExpect(jsonPath("$.aiCalls[0].aiReviewRuleId").doesNotExist())
            .andExpect(jsonPath("$.aiCalls[1].aiReviewRuleId").value(ruleId));
    }

    private org.springframework.test.web.servlet.ResultActions triggerReview(long submissionId, Long promptVersionId) throws Exception {
        return mockMvc.perform(post("/submissions/{submissionId}/ai-review", submissionId)
            .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"promptVersionId\":" + promptVersionId + "}"));
    }

    private long reviewedSubmission(String seed) throws Exception {
        long submissionId = submissionFixture(seed);
        triggerReview(submissionId, 1L).andExpect(status().isCreated());
        return submissionId;
    }

    private JsonNode saveAndPublishRule(long taskId, String promptTemplate) throws Exception {
        JsonNode saved = objectMapper.readTree(mockMvc.perform(post("/ai-review/rules")
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"taskId":%d,"promptTemplate":"%s","dimensions":["accuracy","safety"],"threshold":0.8}
                    """.formatted(taskId, promptTemplate)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());

        return objectMapper.readTree(mockMvc.perform(post("/ai-review/rules/{ruleId}/publish", saved.get("id").asLong())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
    }

    private long taskIdForSubmission(long submissionId) {
        return jdbcTemplate.queryForObject("SELECT task_id FROM submissions WHERE id=?", Long.class, submissionId);
    }

    private long insertPromptVersion(int versionNumber, String content) {
        return insertAndReturnId("""
            INSERT INTO prompt_versions(version_no, content, content_hash, status, owner_id, published_at, created_at)
            VALUES (?, ?, ?, 'published', 1001, NOW(3), NOW(3))
            """, versionNumber, content, fixedHash("prompt-version-" + versionNumber));
    }

    private Long insertLegacyAiCall(long submissionId, String idempotencyKey) {
        return insertAndReturnId("""
            INSERT INTO ai_calls(submission_id, purpose, prompt_version, model_provider, model_name,
                                 input_hash, request_payload, response_payload, token_input, token_output,
                                 cost_decimal, latency_ms, status, idempotency_key, created_at, completed_at)
            VALUES (?, 'submission_review', 'm3-owner-review-v1', 'mock', 'mock-v1',
                    ?, JSON_OBJECT('legacy', true), JSON_OBJECT('legacy', true), 0, 0,
                    0, 1, 'completed', ?, DATE_SUB(NOW(3), INTERVAL 1 SECOND),
                    DATE_SUB(NOW(3), INTERVAL 1 SECOND))
            """, submissionId, fixedHash("legacy-input-" + submissionId), idempotencyKey);
    }

    private String legacyIdempotencyKey(long submissionId) {
        return "submission:" + submissionId + ":provider:mock:model:mock-v1:prompt:prompt-v1";
    }

    private long submissionFixture(String seed) {
        Long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'AI review integration', '<p>AI</p>', ?, 1, 1, 'published', 1001)
            """, seed, LocalDateTime.parse("2030-01-01T00:00:00"));
        Long schemaId = insertAndReturnId(
            "INSERT INTO label_schemas(task_id, name, owner_id) VALUES (?, ?, 1001)",
            taskId, seed + " schema");
        Long schemaVersionId = insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, 1, JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('stableId','field-title','label','标题','type','text'))),
                    JSON_ARRAY('field-title'), ?, 'published', NOW(3), NOW(3))
            """, schemaId, fixedHash(seed + "-schema"));
        Long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', 1, 'completed')",
            taskId);
        Long datasetItemId = insertAndReturnId("""
            INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
            VALUES (?, ?, 1, JSON_OBJECT('source','row-1'), ?)
            """, datasetId, taskId, fixedHash(seed + "-item"));
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?",
            schemaVersionId, datasetId, taskId);
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, schemaId);
        Long sessionId = insertAndReturnId("""
            INSERT INTO sessions(task_id, dataset_item_id, labeler_id, schema_version_id, claim_snapshot, status, submitted_at)
            VALUES (?, ?, 1002, ?, JSON_OBJECT('source','row-1'), 'submitted', NOW(3))
            """, taskId, datasetItemId, schemaVersionId);
        return insertAndReturnId("""
            INSERT INTO submissions(session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
                                    answer_payload, content_hash, status, created_at)
            VALUES (?, ?, ?, 1002, ?, JSON_OBJECT('field-title','final answer'), ?, 'under_ai_review', NOW(3))
            """, sessionId, taskId, datasetItemId, schemaVersionId, fixedHash(seed + "-submission"));
    }

    private Long insertAndReturnId(String sql, Object... args) {
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

    private String fixedHash(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private String tokenForUser(Long userId, String username, String role) {
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
