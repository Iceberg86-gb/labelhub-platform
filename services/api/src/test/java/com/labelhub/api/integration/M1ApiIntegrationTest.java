package com.labelhub.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Date;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class M1ApiIntegrationTest {

    private static final String JWT_SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";
    private static final String INTERNAL_TOKEN = "dev-internal-token";
    // Seeded by PlatformAdminSeeder from labelhub.platform-admin.initial-password (test application.yml).
    private static final String PLATFORM_ADMIN_PASSWORD = "test-only-pa-initial-password";

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
    void owner_can_login_create_list_publish_pause_resume_and_end_task() throws Exception {
        String token = login("owner_demo", "demo1234");

        String createdBody = mockMvc.perform(post("/tasks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Owner demo task",
                      "description": "Review task",
                      "instructionRichText": "<p>Read carefully</p>",
                      "tags": ["demo"],
                      "rewardRule": {"points": 10},
                      "deadlineAt": "2030-01-01T00:00:00Z",
                      "quotaTotal": 5
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("draft"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long taskId = objectMapper.readTree(createdBody).get("id").asLong();
        bindPublishPrerequisites(taskId);

        mockMvc.perform(get("/tasks").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));

        transition(token, taskId, "published", "ready");
        transition(token, taskId, "paused", "pause");
        transition(token, taskId, "published", "resume");
        transition(token, taskId, "ended", "done");

        Integer transitionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM task_transitions WHERE task_id = ?", Integer.class, taskId);
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE resource_type = 'task' AND resource_id = ?", Integer.class, taskId);
        assertThat(transitionCount).isEqualTo(4);
        assertThat(auditCount).isEqualTo(4);
    }

    @Test
    void business_endpoints_reject_missing_invalid_expired_and_wrong_role_tokens() throws Exception {
        mockMvc.perform(get("/tasks"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/tasks").header("Authorization", bearer("not-a-jwt")))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/tasks").header("Authorization", bearer(expiredOwnerToken())))
            .andExpect(status().isUnauthorized());

        String labelerToken = login("labeler_demo", "demo1234");
        mockMvc.perform(post("/tasks")
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Forbidden",
                      "description": "Nope",
                      "instructionRichText": "<p>Nope</p>",
                      "deadlineAt": "2030-01-01T00:00:00Z",
                      "quotaTotal": 1
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void register_forces_labeler_role_and_never_persists_plaintext_password() throws Exception {
        String body = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "new_labeler_runtime",
                      "displayName": "New Labeler Runtime",
                      "email": "new-labeler-runtime@example.test",
                      "password": "demo1234",
                      "roles": ["OWNER", "SENIOR_REVIEWER"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.roles[0]").value("LABELER"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long userId = objectMapper.readTree(body).get("user").get("id").asLong();

        Integer roleCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = ? AND r.code <> 'LABELER'
            """,
            Integer.class,
            userId
        );
        String passwordHash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE id = ?",
            String.class,
            userId
        );
        assertThat(roleCount).isZero();
        assertThat(passwordHash).doesNotContain("demo1234");
    }

    @Test
    void role_grant_endpoint_enforces_authorization_and_rejects_forbidden_roles() throws Exception {
        String labelerToken = login("labeler_demo", "demo1234");
        mockMvc.perform(post("/users/{userId}/roles", 1002L)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role":"REVIEWER","enabled":true}
                    """))
            .andExpect(status().isForbidden());

        String ownerToken = login("owner_demo", "demo1234");
        mockMvc.perform(post("/users/{userId}/roles", 1002L)
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role":"REVIEWER","enabled":true}
                    """))
            .andExpect(status().isForbidden());

        String adminToken = login("platform_admin", PLATFORM_ADMIN_PASSWORD);
        mockMvc.perform(post("/users/{userId}/roles", 1002L)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role":"OWNER","enabled":true}
                    """))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/users/{userId}/roles", 1002L)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role":"AI_AGENT","enabled":true}
                    """))
            .andExpect(status().isBadRequest());

        Integer forbiddenGrantCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = 1002 AND r.code IN ('OWNER', 'AI_AGENT')
            """,
            Integer.class
        );
        assertThat(forbiddenGrantCount).isZero();
    }

    @Test
    void platform_admin_can_list_registered_users_without_password_fields() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "owner_visible_labeler",
                      "displayName": "Owner Visible Labeler",
                      "email": "owner-visible-labeler@example.test",
                      "password": "demo1234"
                    }
                    """))
            .andExpect(status().isCreated());

        String adminToken = login("platform_admin", PLATFORM_ADMIN_PASSWORD);
        String body = mockMvc.perform(get("/users")
                .header("Authorization", bearer(adminToken))
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode root = objectMapper.readTree(body);
        assertThat(root.get("items").findValuesAsText("username")).contains("owner_visible_labeler");
        assertThat(body).doesNotContain("password");

        String labelerToken = login("labeler_demo", "demo1234");
        mockMvc.perform(get("/users").header("Authorization", bearer(labelerToken)))
            .andExpect(status().isForbidden());
        String ownerToken = login("owner_demo", "demo1234");
        mockMvc.perform(get("/users").header("Authorization", bearer(ownerToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void internal_routes_require_internal_token_before_reaching_application_routes() throws Exception {
        mockMvc.perform(post("/internal/ai-review/results"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/ai-review/results")
                .header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    void state_transition_rolls_back_transition_and_audit_when_audit_insert_fails_in_mysql() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token);
        bindPublishPrerequisites(taskId);
        // Reject only rows written after this point, so audit rows left by earlier tests (any order) still pass.
        String cutoff = jdbcTemplate.queryForObject("SELECT DATE_FORMAT(NOW(3), '%Y-%m-%d %H:%i:%s.%f')", String.class);
        jdbcTemplate.execute(
            "ALTER TABLE audit_logs ADD CONSTRAINT chk_phase4_force_audit_failure CHECK (created_at <= '" + cutoff + "')");
        try {
            mockMvc.perform(patch("/tasks/{taskId}/transition", taskId)
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"toStatus":"published","reason":"force audit failure"}
                        """))
                .andExpect(status().isInternalServerError());

            String status = jdbcTemplate.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, taskId);
            Integer transitionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task_transitions WHERE task_id = ?", Integer.class, taskId);
            assertThat(status).isEqualTo("draft");
            assertThat(transitionCount).isZero();
        } finally {
            jdbcTemplate.execute("ALTER TABLE audit_logs DROP CHECK chk_phase4_force_audit_failure");
        }
    }

    @Test
    void publishing_task_without_schema_returns_400_with_schema_field_error() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, 0, "2030-01-01T00:00:00Z");

        mockMvc.perform(patch("/tasks/{taskId}/transition", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"toStatus":"published","reason":"try publish"}
            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PUBLISH_GUARD_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("currentSchemaVersionId"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Task must have a current schema version before publishing"));
    }

    @Test
    void publishing_task_with_past_deadline_returns_400_with_deadline_at_field_error() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, 1, "2020-01-01T00:00:00Z");

        mockMvc.perform(patch("/tasks/{taskId}/transition", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"toStatus":"published","reason":"try publish"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PUBLISH_GUARD_FAILED"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("deadlineAt"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Task deadline must be in the future"));
    }

    @Test
    void create_task_requires_deadline_with_400() throws Exception {
        String token = login("owner_demo", "demo1234");

        mockMvc.perform(post("/tasks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "No deadline task",
                      "description": "No deadline",
                      "instructionRichText": "<p>No deadline</p>",
                      "quotaTotal": 1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("deadlineAt"));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }

    /** Satisfies TaskService.canPublish: a published schema version and a dataset bound to the task. */
    private void bindPublishPrerequisites(long taskId) {
        long schemaId = insertAndReturnId(
            "INSERT INTO label_schemas(task_id, name, owner_id) VALUES (?, 'M1 schema', 1001)", taskId);
        long schemaVersionId = insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, 1, JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('stableId','answer','label','Answer','type','text'))),
                    JSON_ARRAY('answer'), SHA2(CONCAT('m1-schema-', ?), 256), 'published', NOW(3), NOW(3))
            """, schemaId, taskId);
        long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', 1, 'completed')", taskId);
        jdbcTemplate.update("UPDATE label_schemas SET current_version_id=? WHERE id=?", schemaVersionId, schemaId);
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?",
            schemaVersionId, datasetId, taskId);
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

    private long createDraftTask(String token) throws Exception {
        return createDraftTask(token, 1, "2030-01-01T00:00:00Z");
    }

    private long createDraftTask(String token, int quotaTotal, String deadlineAt) throws Exception {
        String body = mockMvc.perform(post("/tasks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Rollback task",
                      "description": "Rollback",
                      "instructionRichText": "<p>Rollback</p>",
                      "deadlineAt": "%s",
                      "quotaTotal": %d
                    }
                    """.formatted(deadlineAt, quotaTotal)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void transition(String token, long taskId, String status, String reason) throws Exception {
        mockMvc.perform(patch("/tasks/{taskId}/transition", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\":\"" + status + "\",\"reason\":\"" + reason + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(status));
    }

    private String expiredOwnerToken() {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
            .subject("owner_demo")
            .claim("userId", 1001L)
            .claim("username", "owner_demo")
            .claim("roles", java.util.List.of("OWNER"))
            .issuedAt(Date.from(now.minusSeconds(7200)))
            .expiration(Date.from(now.minusSeconds(3600)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
