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
                .content("{\"promptVersion\":\"prompt-v1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false))
            .andExpect(jsonPath("$.aiCall.providerName").value("mock"))
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
                .content("{\"promptVersion\":\"prompt-v1\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void cross_owner_trigger_ai_review_returns_404() throws Exception {
        long submissionId = submissionFixture("ai-review-cross-owner");

        mockMvc.perform(post("/submissions/{submissionId}/ai-review", submissionId)
                .header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"promptVersion\":\"prompt-v1\"}"))
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

        triggerReview(submissionId, "prompt-v1")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false));
        triggerReview(submissionId, "prompt-v1")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(true));

        assertThat(mockAiProvider.getCallCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(1);
    }

    @Test
    void trigger_with_different_prompt_creates_new_ai_call() throws Exception {
        long submissionId = submissionFixture("ai-review-new-prompt");

        triggerReview(submissionId, "prompt-v1").andExpect(status().isCreated());
        triggerReview(submissionId, "prompt-v2")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", Integer.class, submissionId))
            .isEqualTo(2);
    }

    private org.springframework.test.web.servlet.ResultActions triggerReview(long submissionId, String promptVersion) throws Exception {
        return mockMvc.perform(post("/submissions/{submissionId}/ai-review", submissionId)
            .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"promptVersion\":\"" + promptVersion + "\"}"));
    }

    private long reviewedSubmission(String seed) throws Exception {
        long submissionId = submissionFixture(seed);
        triggerReview(submissionId, "prompt-v1").andExpect(status().isCreated());
        return submissionId;
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
