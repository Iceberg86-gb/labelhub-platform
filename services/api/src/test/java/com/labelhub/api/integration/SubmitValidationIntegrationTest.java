package com.labelhub.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class SubmitValidationIntegrationTest {

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
    void submit_invalid_payload_returns_422_fieldErrors_with_schema_stableId() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("submit-validation-422", 3, 1);
        long sessionId = claimSession(labelerToken, fixture);

        mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"ab\"}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("field_0"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("最少 3 字"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM submissions WHERE session_id=?", Integer.class, sessionId))
            .isZero();
    }

    @Test
    void submit_validation_uses_session_bound_schema_version_after_task_publishes_v3() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        Fixture fixture = publishedTaskFixture("submit-validation-bound-v2", 5, 2);
        long sessionId = claimSession(labelerToken, fixture);
        long v3 = publishNextSchemaVersion(fixture, 1, 3);

        assertThat(jdbcTemplate.queryForObject("SELECT current_schema_version_id FROM tasks WHERE id=?", Long.class, fixture.taskId()))
            .isEqualTo(v3);

        mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"abc\"}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("field_0"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("最少 5 字"));

        assertThat(jdbcTemplate.queryForObject("SELECT schema_version_id FROM sessions WHERE id=?", Long.class, sessionId))
            .isEqualTo(fixture.schemaVersionId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM submissions WHERE session_id=?", Integer.class, sessionId))
            .isZero();
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

    private long claimSession(String labelerToken, Fixture fixture) throws Exception {
        String body = mockMvc.perform(post("/tasks/{taskId}/claim", fixture.taskId())
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private Fixture publishedTaskFixture(String title, int minLength, int versionNo) {
        Long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'Submit validation integration', '<p>Submit validation</p>', ?, 5, 0, 'published', 1001)
            """, title, LocalDateTime.parse("2030-01-01T00:00:00"));
        Long schemaId = insertAndReturnId(
            "INSERT INTO label_schemas(task_id, name, description, owner_id) VALUES (?, ?, 'Submit validation schema', 1001)",
            taskId, title + " schema");
        Long schemaVersionId = insertSchemaVersion(schemaId, versionNo, minLength, title + "-schema-v" + versionNo);
        Long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', 1, 'imported')",
            taskId);
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?",
            schemaVersionId, datasetId, taskId);
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, schemaId);
        insertAndReturnId("""
            INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
            VALUES (?, ?, 0, JSON_OBJECT('row', 0), ?)
            """, datasetId, taskId, fixedHash(title + "-item-0"));
        return new Fixture(taskId, schemaId, schemaVersionId);
    }

    private long publishNextSchemaVersion(Fixture fixture, int minLength, int versionNo) {
        long schemaVersionId = insertSchemaVersion(fixture.schemaId(), versionNo, minLength, "schema-v" + versionNo);
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=? WHERE id=?", schemaVersionId, fixture.taskId());
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, fixture.schemaId());
        return schemaVersionId;
    }

    private Long insertSchemaVersion(long schemaId, int versionNo, int minLength, String seed) {
        return insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, ?, JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT(
                    'stableId','field_0',
                    'label','Field 0',
                    'type','text',
                    'validation', JSON_OBJECT('minLength', ?)
                ))),
                JSON_ARRAY('field_0'), ?, 'published', NOW(3), NOW(3))
            """, schemaId, versionNo, minLength, fixedHash(seed));
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

    private record Fixture(Long taskId, Long schemaId, Long schemaVersionId) {}
}
