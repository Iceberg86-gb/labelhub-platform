package com.labelhub.api.integration;

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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class QualityLedgerIntegrationTest {

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockAiProvider mockAiProvider;

    @BeforeEach
    void resetMockProvider() {
        mockAiProvider.resetCallCount();
    }

    @Test
    void reviewer_lists_queue_default_returns_submitted_submissions() throws Exception {
        Fixture fixture = submissionFixture("reviewer-queue-default", 1001L, 1002L, "submitted");

        mockMvc.perform(get("/reviewer/submissions")
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(fixture.submissionId()))
            .andExpect(jsonPath("$.items[0].verdict.status").value("pending"))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void reviewer_lists_queue_filters_by_verdict_pending() throws Exception {
        submissionFixture("reviewer-queue-pending", 1001L, 1002L, "submitted");

        mockMvc.perform(get("/reviewer/submissions?verdict=pending")
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].verdict.status").value("pending"));
    }

    @Test
    void reviewer_lists_queue_filters_by_verdict_approved() throws Exception {
        Fixture fixture = submissionFixture("reviewer-queue-approved", 1001L, 1002L, "submitted");
        insertLedgerEntry(fixture.submissionId(), fixture.taskId(), 1003L, "approve");

        mockMvc.perform(get("/reviewer/submissions?verdict=approved")
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(fixture.submissionId()))
            .andExpect(jsonPath("$.items[0].verdict.status").value("approved"));
    }

    @Test
    void labeler_role_cannot_list_reviewer_queue_returns_403() throws Exception {
        mockMvc.perform(get("/reviewer/submissions")
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void owner_role_cannot_list_reviewer_queue_returns_403() throws Exception {
        mockMvc.perform(get("/reviewer/submissions")
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void reviewer_creates_ledger_entry_returns_201_with_payload() throws Exception {
        Fixture fixture = submissionFixture("ledger-create", 1001L, 1002L, "submitted");

        mockMvc.perform(post("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entryType":"reviewer_overall_verdict","payload":{"verdict":"approve","reason":"Looks good"}}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.entryType").value("reviewer_overall_verdict"))
            .andExpect(jsonPath("$.actorUserId").value(1003))
            .andExpect(jsonPath("$.payload.verdict").value("approve"))
            .andExpect(jsonPath("$.payload.reason").value("Looks good"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quality_ledger_entries WHERE submission_id=?",
            Integer.class,
            fixture.submissionId()
        )).isEqualTo(1);
    }

    @Test
    void reviewer_self_review_returns_409() throws Exception {
        Fixture fixture = submissionFixture("ledger-self-review", 1001L, 1003L, "submitted");

        mockMvc.perform(post("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entryType":"reviewer_overall_verdict","payload":{"verdict":"approve"}}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SELF_REVIEW_NOT_ALLOWED"));
    }

    @Test
    void new_ledger_entry_changes_verdict_via_http() throws Exception {
        Fixture fixture = submissionFixture("ledger-verdict-flow", 1001L, 1002L, "submitted");

        mockMvc.perform(get("/submissions/{submissionId}/verdict", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.derivedFromEntryId").value(nullValue()));

        mockMvc.perform(post("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entryType":"reviewer_overall_verdict","payload":{"verdict":"approve"}}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/submissions/{submissionId}/verdict", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("approved"))
            .andExpect(jsonPath("$.derivedFromEntryId").isNumber());

        mockMvc.perform(post("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entryType":"reviewer_overall_verdict","payload":{"verdict":"reject"}}
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/submissions/{submissionId}/verdict", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("rejected"));
    }

    @Test
    void labeler_cannot_create_ledger_entry_returns_403() throws Exception {
        Fixture fixture = submissionFixture("ledger-labeler-post", 1001L, 1002L, "submitted");

        mockMvc.perform(post("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entryType":"reviewer_overall_verdict","payload":{"verdict":"approve"}}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void owner_cannot_create_ledger_entry_returns_403() throws Exception {
        Fixture fixture = submissionFixture("ledger-owner-post", 1001L, 1002L, "submitted");

        mockMvc.perform(post("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entryType":"reviewer_overall_verdict","payload":{"verdict":"approve"}}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void reviewer_can_read_any_submission_render_schema() throws Exception {
        Fixture fixture = submissionFixture("ledger-render-reviewer", 1001L, 1002L, "submitted");

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion.id").value(fixture.schemaVersionId()))
            .andExpect(jsonPath("$.answerPayload.field-title").value("answer"));
    }

    @Test
    void labeler_can_read_own_submission_ledger() throws Exception {
        Fixture fixture = submissionFixture("ledger-labeler-read", 1001L, 1002L, "submitted");
        insertLedgerEntry(fixture.submissionId(), fixture.taskId(), 1003L, "approve");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].payload.verdict").value("approve"));
    }

    @Test
    void owner_can_read_owned_submission_ledger() throws Exception {
        Fixture fixture = submissionFixture("ledger-owner-read", 1001L, 1002L, "submitted");
        insertLedgerEntry(fixture.submissionId(), fixture.taskId(), 1003L, "reject");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].payload.verdict").value("reject"));
    }

    @Test
    void cross_labeler_cannot_read_ledger_returns_404() throws Exception {
        Fixture fixture = submissionFixture("ledger-cross-labeler", 1001L, 1002L, "submitted");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(2002L, "other_labeler", "LABELER"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void ai_review_appends_field_findings_to_ledger_on_new_call() throws Exception {
        Fixture fixture = submissionFixture("ledger-ai-review-new-call", 1001L, 1002L, "under_ai_review");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(post("/submissions/{submissionId}/ai-review", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"promptVersion\":\"prompt-v1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false))
            .andExpect(jsonPath("$.fieldFindings", hasSize(1)));

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].entryType").value("ai_field_finding"))
            .andExpect(jsonPath("$.items[0].actorType").value("ai"))
            .andExpect(jsonPath("$.items[0].actorUserId").value(nullValue()))
            .andExpect(jsonPath("$.items[0].aiCallId").isNumber())
            .andExpect(jsonPath("$.items[0].payload.fieldPath").value("field-title"))
            .andExpect(jsonPath("$.items[0].payload.severity").value("warning"))
            .andExpect(jsonPath("$.items[0].payload.aiCallId").doesNotExist());
    }

    @Test
    void repeat_ai_review_does_not_duplicate_ledger_entries() throws Exception {
        Fixture fixture = submissionFixture("ledger-ai-review-idempotency", 1001L, 1002L, "under_ai_review");

        triggerAiReview(fixture.submissionId(), "prompt-v1")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(false));
        assertThat(ledgerEntryCount(fixture.submissionId())).isEqualTo(1);

        triggerAiReview(fixture.submissionId(), "prompt-v1")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotencyHit").value(true));

        assertThat(mockAiProvider.getCallCount()).isEqualTo(1);
        assertThat(ledgerEntryCount(fixture.submissionId())).isEqualTo(1);
    }

    @Test
    void reviewer_can_read_ai_field_finding_entries() throws Exception {
        Fixture fixture = submissionWithAiReview("ledger-ai-reviewer-read");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].entryType").value("ai_field_finding"))
            .andExpect(jsonPath("$.items[0].payload.fieldPath").value("field-title"));
    }

    @Test
    void owner_can_read_ai_field_finding_entries() throws Exception {
        Fixture fixture = submissionWithAiReview("ledger-ai-owner-read");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].entryType").value("ai_field_finding"));
    }

    @Test
    void labeler_can_read_ai_field_finding_entries_for_own_submission() throws Exception {
        Fixture fixture = submissionWithAiReview("ledger-ai-labeler-read");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].entryType").value("ai_field_finding"));
    }

    @Test
    void cross_labeler_cannot_read_ai_field_findings_returns_404() throws Exception {
        Fixture fixture = submissionWithAiReview("ledger-ai-cross-labeler");

        mockMvc.perform(get("/submissions/{submissionId}/ledger-entries", fixture.submissionId())
                .header("Authorization", bearer(tokenForUser(2002L, "other_labeler", "LABELER"))))
            .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions triggerAiReview(Long submissionId, String promptVersion) throws Exception {
        return mockMvc.perform(post("/submissions/{submissionId}/ai-review", submissionId)
            .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"promptVersion\":\"" + promptVersion + "\"}"));
    }

    private Fixture submissionWithAiReview(String seed) throws Exception {
        Fixture fixture = submissionFixture(seed, 1001L, 1002L, "under_ai_review");
        triggerAiReview(fixture.submissionId(), "prompt-v1").andExpect(status().isCreated());
        assertThat(ledgerEntryCount(fixture.submissionId())).isEqualTo(1);
        return fixture;
    }

    private Integer ledgerEntryCount(Long submissionId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quality_ledger_entries WHERE submission_id=?",
            Integer.class,
            submissionId
        );
    }

    private Fixture submissionFixture(String seed, Long ownerId, Long labelerId, String submissionStatus) {
        Long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'Quality ledger integration', '<p>Quality</p>', ?, 5, 1, 'published', ?)
            """, seed, LocalDateTime.parse("2030-01-01T00:00:00"), ownerId);
        Long schemaId = insertAndReturnId(
            "INSERT INTO label_schemas(task_id, name, owner_id) VALUES (?, ?, ?)",
            taskId, seed + " schema", ownerId);
        Long schemaVersionId = insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, 1, JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('stableId','field-title','label','标题','type','text'))),
                    JSON_ARRAY('field-title'), ?, 'published', NOW(3), NOW(3))
            """, schemaId, fixedHash(seed + "-schema"));
        Long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', 1, 'completed')",
            taskId);
        Long itemId = insertAndReturnId("""
            INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
            VALUES (?, ?, 1, JSON_OBJECT('source','row-1'), ?)
            """, datasetId, taskId, fixedHash(seed + "-item"));
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?",
            schemaVersionId, datasetId, taskId);
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, schemaId);
        Long sessionId = insertAndReturnId("""
            INSERT INTO sessions(task_id, dataset_item_id, labeler_id, schema_version_id, claim_snapshot, status, submitted_at)
            VALUES (?, ?, ?, ?, JSON_OBJECT('source','row-1'), 'submitted', NOW(3))
            """, taskId, itemId, labelerId, schemaVersionId);
        Long submissionId = insertAndReturnId("""
            INSERT INTO submissions(session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
                                    answer_payload, provenance, content_hash, status, created_at)
            VALUES (?, ?, ?, ?, ?, JSON_OBJECT('field-title','answer'), JSON_OBJECT('source','manual'), ?, ?, NOW(3))
            """, sessionId, taskId, itemId, labelerId, schemaVersionId, fixedHash(seed + "-submission"), submissionStatus);
        return new Fixture(taskId, schemaVersionId, submissionId);
    }

    private void insertLedgerEntry(Long submissionId, Long taskId, Long reviewerId, String verdict) {
        jdbcTemplate.update("""
            INSERT INTO quality_ledger_entries(submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at)
            VALUES (?, ?, 'reviewer_overall_verdict', 'reviewer', ?, NULL, JSON_OBJECT('verdict', ?), NOW(3))
            """, submissionId, taskId, reviewerId, verdict);
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

    private String tokenForUser(Long userId, String username, String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("username", username)
            .claim("roles", List.of(roles))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(Long taskId, Long schemaVersionId, Long submissionId) {}
}
