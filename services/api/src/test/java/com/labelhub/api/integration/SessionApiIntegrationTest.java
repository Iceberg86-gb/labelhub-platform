package com.labelhub.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class SessionApiIntegrationTest {

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

    @Test
    void labeler_can_list_marketplace_and_claim_session_bound_to_current_schema_version() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("marketplace-happy", 3, 2);

        mockMvc.perform(get("/tasks/marketplace?page=1&size=20").header("Authorization", bearer(labelerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(1)));

        String body = mockMvc.perform(post("/tasks/{taskId}/claim", fixture.taskId())
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.taskId").value(fixture.taskId()))
            .andExpect(jsonPath("$.schemaVersionId").value(fixture.schemaVersionId()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        long sessionId = objectMapper.readTree(body).get("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT quota_claimed FROM tasks WHERE id=?", Integer.class, fixture.taskId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM dataset_items WHERE id=(SELECT dataset_item_id FROM sessions WHERE id=?)", String.class, sessionId))
            .isEqualTo("claimed");
        assertThat(jdbcTemplate.queryForObject("SELECT schema_version_id FROM sessions WHERE id=?", Long.class, sessionId))
            .isEqualTo(fixture.schemaVersionId());
    }

    @Test
    void my_sessions_accepts_lowercase_status_query_value() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        claimSession(labelerToken, publishedTaskFixture("sessions-status-lowercase", 1, 1));

        String body = mockMvc.perform(get("/my/sessions?status=claimed")
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode items = objectMapper.readTree(body).get("items");
        assertThat(items).isNotEmpty();
        for (JsonNode item : items) {
            assertThat(item.get("status").asText()).isEqualTo("claimed");
        }
    }

    @Test
    void my_sessions_rejects_invalid_status_query_value_as_bad_request() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");

        mockMvc.perform(get("/my/sessions?status=CLAIMED")
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATUS"));
    }

    @Test
    void concurrent_claim_with_quota_1_allows_only_one_winner() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("marketplace-concurrent", 1, 5);
        Callable<Integer> claimCall = () -> mockMvc.perform(post("/tasks/{taskId}/claim", fixture.taskId())
                .header("Authorization", bearer(labelerToken)))
            .andReturn()
            .getResponse()
            .getStatus();

        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            List<Integer> statuses = new ArrayList<>();
            for (var future : executor.invokeAll(List.of(claimCall, claimCall, claimCall, claimCall, claimCall))) {
                statuses.add(future.get());
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("SELECT quota_claimed FROM tasks WHERE id=?", Integer.class, fixture.taskId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sessions WHERE task_id=?", Integer.class, fixture.taskId())).isEqualTo(1);
    }

    @Test
    void claim_failure_when_no_dataset_item_rolls_back_quota_increment() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("marketplace-rollback", 1, 1);
        jdbcTemplate.update("UPDATE dataset_items SET status='claimed' WHERE task_id=?", fixture.taskId());

        mockMvc.perform(post("/tasks/{taskId}/claim", fixture.taskId()).header("Authorization", bearer(labelerToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("NO_AVAILABLE_DATASET_ITEM"));

        assertThat(jdbcTemplate.queryForObject("SELECT quota_claimed FROM tasks WHERE id=?", Integer.class, fixture.taskId())).isZero();
    }

    @Test
    void concurrent_draft_save_creates_sequential_revisions() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        long sessionId = claimSession(labelerToken, publishedTaskFixture("draft-concurrent", 1, 1));
        Callable<Integer> draftCall = () -> mockMvc.perform(put("/sessions/{sessionId}/draft", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"field_0\":\"answer\"}}"))
            .andReturn()
            .getResponse()
            .getStatus();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Integer> statuses = new ArrayList<>();
            for (var future : executor.invokeAll(List.of(draftCall, draftCall))) {
                statuses.add(future.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 201);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForList(
            "SELECT revision_no FROM drafts WHERE session_id=? ORDER BY revision_no", Integer.class, sessionId))
            .containsExactly(1, 2);
    }

    @Test
    void draft_revision_unique_constraint_prevents_duplicate() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        long sessionId = claimSession(labelerToken, publishedTaskFixture("draft-unique", 1, 1));
        jdbcTemplate.update(
            "INSERT INTO drafts(session_id, revision_no, draft_payload, saved_at) VALUES (?, 1, JSON_OBJECT('field_0','a'), NOW(3))",
            sessionId);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO drafts(session_id, revision_no, draft_payload, saved_at) VALUES (?, 1, JSON_OBJECT('field_0','b'), NOW(3))",
            sessionId))
            .isInstanceOf(DataAccessException.class);
    }

    @Test
    void draft_save_on_submitted_session_returns_409() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        long sessionId = claimSession(labelerToken, publishedTaskFixture("draft-submitted", 1, 1));
        jdbcTemplate.update("UPDATE sessions SET status='submitted', submitted_at=NOW(3) WHERE id=?", sessionId);

        mockMvc.perform(put("/sessions/{sessionId}/draft", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"field_0\":\"late\"}}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_NOT_EDITABLE"));
    }

    @Test
    void submit_creates_submission_and_marks_session_submitted() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("submit-happy", 1, 1);
        long sessionId = claimSession(labelerToken, fixture);

        String body = mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"final answer\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.schemaVersionId").value(fixture.schemaVersionId()))
            .andExpect(jsonPath("$.status").value("submitted"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        long submissionId = objectMapper.readTree(body).get("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sessions WHERE id=?", String.class, sessionId))
            .isEqualTo("submitted");
        assertThat(jdbcTemplate.queryForObject("SELECT submitted_at IS NOT NULL FROM sessions WHERE id=?", Integer.class, sessionId))
            .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT schema_version_id FROM submissions WHERE id=?", Long.class, submissionId))
            .isEqualTo(fixture.schemaVersionId());
    }

    @Test
    void my_sessions_reflects_reviewer_level_verdict_without_senior_review() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("my-sessions-reviewer-verdict", 1, 1);
        long sessionId = claimSession(labelerToken, fixture);

        String submitBody = mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"answer\"}}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long submissionId = objectMapper.readTree(submitBody).get("id").asLong();

        // A reviewer-level (NOT senior) overall verdict is the authoritative outcome post senior-orthogonalization;
        // the labeler's session must surface it as approved instead of staying "submitted" (审核中).
        jdbcTemplate.update("""
            INSERT INTO quality_ledger_entries(submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at)
            VALUES (?, ?, 'reviewer_overall_verdict', 'reviewer', 1003, NULL, JSON_OBJECT('verdict', 'approve', 'reviewLevel', 'reviewer'), NOW(3))
            """, submissionId, fixture.taskId());

        assertThat(workStatusForSession(labelerToken, sessionId)).isEqualTo("approved");
    }

    @Test
    void my_sessions_lets_senior_arbitration_override_reviewer_verdict() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("my-sessions-senior-override", 1, 1);
        long sessionId = claimSession(labelerToken, fixture);

        String submitBody = mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"answer\"}}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long submissionId = objectMapper.readTree(submitBody).get("id").asLong();

        jdbcTemplate.update("""
            INSERT INTO quality_ledger_entries(submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at)
            VALUES (?, ?, 'reviewer_overall_verdict', 'reviewer', 1003, NULL, JSON_OBJECT('verdict', 'approve', 'reviewLevel', 'reviewer'), NOW(3))
            """, submissionId, fixture.taskId());
        jdbcTemplate.update("""
            INSERT INTO quality_ledger_entries(submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at)
            VALUES (?, ?, 'reviewer_overall_verdict', 'reviewer', 1004, NULL, JSON_OBJECT('verdict', 'reject', 'reviewLevel', 'senior_reviewer'), NOW(3))
            """, submissionId, fixture.taskId());

        assertThat(workStatusForSession(labelerToken, sessionId)).isEqualTo("rejected");
    }

    @Test
    void historical_render_schema_returns_submission_schema_version_after_task_publishes_v2() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("submit-history", 1, 1);
        long sessionId = claimSession(labelerToken, fixture);

        String submitBody = mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"historical answer\"}}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long submissionId = objectMapper.readTree(submitBody).get("id").asLong();

        long v2 = publishSchemaV2(fixture, "submit-history-v2");
        assertThat(jdbcTemplate.queryForObject("SELECT current_schema_version_id FROM tasks WHERE id=?", Long.class, fixture.taskId()))
            .isEqualTo(v2);

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", submissionId)
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion.id").value(fixture.schemaVersionId()))
            .andExpect(jsonPath("$.schemaVersion.versionNumber").value(1))
            .andExpect(jsonPath("$.schemaVersion.schemaJson.fields.length()").value(1));
    }

    @Test
    void cannot_submit_twice() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        long sessionId = claimSession(labelerToken, publishedTaskFixture("submit-twice", 1, 1));
        String payload = "{\"answerPayload\":{\"field_0\":\"answer\"}}";

        mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_ALREADY_SUBMITTED"));
    }

    @Test
    void cannot_save_draft_after_submit() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        long sessionId = claimSession(labelerToken, publishedTaskFixture("submit-draft-closed", 1, 1));

        mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"answer\"}}"))
            .andExpect(status().isCreated());

        mockMvc.perform(put("/sessions/{sessionId}/draft", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":{\"field_0\":\"late\"}}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_NOT_EDITABLE"));
    }

    @Test
    void cross_labeler_get_submission_returns_404() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        long sessionId = claimSession(labelerToken, publishedTaskFixture("submit-owner-only", 1, 1));
        String submitBody = mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"answer\"}}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long submissionId = objectMapper.readTree(submitBody).get("id").asLong();

        mockMvc.perform(get("/submissions/{submissionId}", submissionId)
                .header("Authorization", bearer(tokenForUser(2002L, "other_labeler", "LABELER"))))
            .andExpect(status().isNotFound());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private String workStatusForSession(String labelerToken, long sessionId) throws Exception {
        String body = mockMvc.perform(get("/my/sessions?page=1&size=50")
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        for (JsonNode item : objectMapper.readTree(body).get("items")) {
            if (item.get("id").asLong() == sessionId) {
                return item.get("workStatus").asText();
            }
        }
        throw new AssertionError("session " + sessionId + " not found in /my/sessions");
    }

    private long claimSession(String labelerToken, Fixture fixture) throws Exception {
        String body = mockMvc.perform(post("/tasks/{taskId}/claim", fixture.taskId())
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private Fixture publishedTaskFixture(String title, int quotaTotal, int itemCount) {
        Long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'Claim integration', '<p>Claim</p>', ?, ?, 0, 'published', 1001)
            """, title, LocalDateTime.parse("2030-01-01T00:00:00"), quotaTotal);
        Long schemaId = insertAndReturnId(
            "INSERT INTO label_schemas(task_id, name, description, owner_id) VALUES (?, ?, 'Claim schema', 1001)",
            taskId, title + " schema");
        Long schemaVersionId = insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, 1, JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('stableId','field_0','label','Field 0','type','text'))),
                    JSON_ARRAY('field_0'), ?, 'published', NOW(3), NOW(3))
            """, schemaId, fixedHash(title + "-schema"));
        Long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', ?, 'imported')",
            taskId, itemCount);
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?",
            schemaVersionId, datasetId, taskId);
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, schemaId);
        for (int i = 0; i < itemCount; i++) {
            insertAndReturnId("""
                INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
                VALUES (?, ?, ?, JSON_OBJECT('row', ?), ?)
                """, datasetId, taskId, i, i, fixedHash(title + "-item-" + i));
        }
        return new Fixture(taskId, schemaId, schemaVersionId, datasetId);
    }

    private long publishSchemaV2(Fixture fixture, String seed) {
        Long schemaVersionId = insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, 2,
                    JSON_OBJECT('fields', JSON_ARRAY(
                        JSON_OBJECT('stableId','field_0','label','Field 0','type','text'),
                        JSON_OBJECT('stableId','field_1','label','Field 1','type','number')
                    )),
                    JSON_ARRAY('field_0','field_1'), ?, 'published', NOW(3), NOW(3))
            """, fixture.schemaId(), fixedHash(seed + "-schema-v2"));
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, fixture.schemaId());
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=? WHERE id=?", schemaVersionId, fixture.taskId());
        return schemaVersionId;
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

    private String bearer(String token) {
        return "Bearer " + token;
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

    private record Fixture(Long taskId, Long schemaId, Long schemaVersionId, Long datasetId) {
    }
}
