package com.labelhub.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

        mockMvc.perform(get("/tasks").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));

        transition(token, taskId, "PUBLISHED", "ready");
        transition(token, taskId, "PAUSED", "pause");
        transition(token, taskId, "PUBLISHED", "resume");
        transition(token, taskId, "ENDED", "done");

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
    void internal_routes_require_internal_token_before_reaching_application_routes() throws Exception {
        mockMvc.perform(post("/internal/ai-review/results"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/ai-review/results").header("X-Internal-Token", INTERNAL_TOKEN))
            .andExpect(status().isBadRequest());
    }

    @Test
    void state_transition_rolls_back_transition_and_audit_when_audit_insert_fails_in_mysql() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token);
        jdbcTemplate.execute("ALTER TABLE audit_logs ADD CONSTRAINT chk_phase4_force_audit_failure CHECK (payload_hash = 'force_fail')");
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
    void publishing_task_with_zero_quota_returns_400_with_quota_total_field_error() throws Exception {
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
            .andExpect(jsonPath("$.fieldErrors[0].field").value("quotaTotal"))
            .andExpect(jsonPath("$.fieldErrors[0].message").value("Task quota must be greater than zero"));
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
            .andExpect(jsonPath("$.status").value(status.toLowerCase()));
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
