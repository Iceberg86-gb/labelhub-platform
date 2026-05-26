package com.labelhub.api.integration;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionDiscoveryIntegrationTest {

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

    @Test
    void owner_lists_submissions_for_owned_task() throws Exception {
        Fixture fixture = submissionFixture("owner-list", 1001L, 1002L, 2);

        mockMvc.perform(get("/tasks/{taskId}/submissions?page=1&size=1", fixture.taskId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].taskId").value(fixture.taskId()))
            .andExpect(jsonPath("$.items[0].labelerId").value(1002))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void cross_owner_lists_submissions_returns_404() throws Exception {
        Fixture fixture = submissionFixture("owner-list-cross", 1001L, 1002L, 1);

        mockMvc.perform(get("/tasks/{taskId}/submissions", fixture.taskId())
                .header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void labeler_role_lists_submissions_returns_403() throws Exception {
        Fixture fixture = submissionFixture("owner-list-labeler", 1001L, 1002L, 1);

        mockMvc.perform(get("/tasks/{taskId}/submissions", fixture.taskId())
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void render_schema_returns_404_for_cross_labeler() throws Exception {
        Fixture fixture = submissionFixture("render-cross-labeler", 1001L, 1002L, 1);

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", fixture.firstSubmissionId())
                .header("Authorization", bearer(tokenForUser(2002L, "other_labeler", "LABELER"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void render_schema_returns_404_for_non_owning_owner() throws Exception {
        Fixture fixture = submissionFixture("render-cross-owner", 1001L, 1002L, 1);

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", fixture.firstSubmissionId())
                .header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void render_schema_succeeds_for_submission_labeler() throws Exception {
        Fixture fixture = submissionFixture("render-labeler", 1001L, 1002L, 1);

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", fixture.firstSubmissionId())
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion.id").value(fixture.schemaVersionId()))
            .andExpect(jsonPath("$.answerPayload.field-title").value("answer 0"));
    }

    @Test
    void render_schema_succeeds_for_task_owner() throws Exception {
        Fixture fixture = submissionFixture("render-owner", 1001L, 1002L, 1);

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", fixture.firstSubmissionId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion.id").value(fixture.schemaVersionId()));
    }

    private Fixture submissionFixture(String seed, Long ownerId, Long labelerId, int submissionCount) {
        Long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'Submission discovery', '<p>Discovery</p>', ?, 5, 0, 'published', ?)
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
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', ?, 'completed')",
            taskId, submissionCount);
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?",
            schemaVersionId, datasetId, taskId);
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, schemaId);

        Long firstSubmissionId = null;
        for (int index = 0; index < submissionCount; index++) {
            Long itemId = insertAndReturnId("""
                INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
                VALUES (?, ?, ?, JSON_OBJECT('source', ?), ?)
                """, datasetId, taskId, index + 1, "row-" + index, fixedHash(seed + "-item-" + index));
            Long sessionId = insertAndReturnId("""
                INSERT INTO sessions(task_id, dataset_item_id, labeler_id, schema_version_id, claim_snapshot, status, submitted_at)
                VALUES (?, ?, ?, ?, JSON_OBJECT('source', ?), 'submitted', NOW(3))
                """, taskId, itemId, labelerId, schemaVersionId, "row-" + index);
            Long submissionId = insertAndReturnId("""
                INSERT INTO submissions(session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
                                        answer_payload, provenance, content_hash, status, created_at)
                VALUES (?, ?, ?, ?, ?, JSON_OBJECT('field-title', ?), JSON_OBJECT('source','manual'), ?, 'under_ai_review', NOW(3))
                """, sessionId, taskId, itemId, labelerId, schemaVersionId, "answer " + index, fixedHash(seed + "-submission-" + index));
            if (firstSubmissionId == null) {
                firstSubmissionId = submissionId;
            }
        }
        return new Fixture(taskId, schemaVersionId, firstSubmissionId);
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

    private record Fixture(Long taskId, Long schemaVersionId, Long firstSubmissionId) {}
}
