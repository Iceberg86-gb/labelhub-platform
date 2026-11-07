package com.labelhub.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.mock.web.MockMultipartFile;
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
 * Owner hard delete against a real MySQL: a task with the full graph (schema, dataset, published AI review
 * rule, sessions, submissions, AI calls, ledger, MCP token and trajectory) is deleted without a foreign-key
 * failure, and nothing task-scoped is left behind. Regression: ai_review_rules used to block the delete (500).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TaskHardDeleteIntegrationTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    private final String owner = tokenForUser(1001L, "owner_demo", "OWNER");

    private final String labeler = tokenForUser(1002L, "labeler_demo", "LABELER");
    private final String reviewer = tokenForUser(1003L, "reviewer_demo", "REVIEWER");

    private static final List<String> TASK_SCOPED_TABLES = List.of(
        "ai_review_rules", "ai_review_auto_policy_events", "agent_trajectory_steps", "mcp_access_tokens", "senior_review_cases",
        "quality_ledger_entries", "review_actions", "current_verdicts", "submissions", "sessions", "dataset_items", "datasets",
        "label_schemas", "export_jobs", "export_snapshots", "task_transitions");

    @Test
    void hard_delete_removes_a_task_with_ai_review_rules_ai_calls_and_mcp_trajectory() throws Exception {
        long taskId = json(mockMvc.perform(post("/tasks").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"title":"hard delete graph","description":"d","instructionRichText":"i","deadlineAt":"2030-01-01T00:00:00Z","quotaTotal":2}
                """))).get("id").asLong();
        long schemaId = json(mockMvc.perform(post("/schemas").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"taskId\":" + taskId + ",\"name\":\"s\",\"description\":\"d\"}"))).get("id").asLong();
        mockMvc.perform(post("/schemas/{id}/versions", schemaId).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"schemaJson\":{\"fields\":[{\"stableId\":\"answer\",\"label\":\"A\",\"type\":\"text\"}]}}"))
            .andExpect(status().isCreated());
        long datasetId = json(mockMvc.perform(multipart("/datasets")
            .file(new MockMultipartFile("file", "items.jsonl", "application/json", "{\"q\":\"one\"}\n{\"q\":\"two\"}\n".getBytes(StandardCharsets.UTF_8)))
            .param("taskId", String.valueOf(taskId)).param("format", "jsonl").header("Authorization", bearer(owner)))).get("id").asLong();
        mockMvc.perform(patch("/tasks/{id}/current-dataset", taskId).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"datasetId\":" + datasetId + "}")).andExpect(status().isOk());
        long ruleId = json(mockMvc.perform(post("/ai-review/rules").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"taskId\":" + taskId + ",\"promptTemplate\":\"judge\",\"dimensions\":[\"overall\"],\"passThreshold\":0.8,\"rejectThreshold\":0.2}")))
            .get("id").asLong();
        long promptVersionId = json(mockMvc.perform(post("/ai-review/rules/{id}/publish", ruleId).header("Authorization", bearer(owner))))
            .get("promptVersionId").asLong();
        mockMvc.perform(patch("/tasks/{id}/transition", taskId).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"toStatus\":\"published\",\"reason\":\"go\"}")).andExpect(status().isOk());

        long sessionId = json(mockMvc.perform(post("/tasks/{id}/claim", taskId).header("Authorization", bearer(labeler)))).get("id").asLong();
        long submissionId = json(mockMvc.perform(post("/sessions/{id}/submit", sessionId).header("Authorization", bearer(labeler))
            .contentType(MediaType.APPLICATION_JSON).content("{\"answerPayload\":{\"answer\":\"yes\"}}"))).get("id").asLong();
        mockMvc.perform(post("/submissions/{id}/ai-review", submissionId).header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"promptVersionId\":" + promptVersionId + "}")).andExpect(status().isCreated());
        mockMvc.perform(post("/submissions/{id}/ledger-entries", submissionId).header("Authorization", bearer(reviewer)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"entryType\":\"reviewer_overall_verdict\",\"payload\":{\"verdict\":\"approve\",\"reviewLevel\":\"reviewer\"}}"))
            .andExpect(status().isCreated());
        long tokenId = json(mockMvc.perform(post("/tasks/{id}/mcp-tokens", taskId).header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"agent\"}"))).get("token").get("id").asLong();
        mockMvc.perform(post("/internal/mcp/trajectory-steps").header("X-Internal-Token", INTERNAL_TOKEN).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"runId":"run-delete","stepNo":1,"tokenId":%d,"taskId":%d,"submissionId":%d,"clientName":"c","toolName":"get_submission",
                 "inputHash":"%s","status":"ok","latencyMs":1}
                """.formatted(tokenId, taskId, submissionId, "a".repeat(64)))).andExpect(status().isCreated());
        assertThat(count("SELECT COUNT(*) FROM ai_review_rules WHERE task_id=?", taskId)).isPositive();
        assertThat(count("SELECT COUNT(*) FROM tasks WHERE id=? AND current_ai_review_rule_id=?", taskId, ruleId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", submissionId)).isPositive();

        mockMvc.perform(delete("/tasks/{id}", taskId).header("Authorization", bearer(owner))).andExpect(status().isNoContent());

        assertThat(count("SELECT COUNT(*) FROM tasks WHERE id=?", taskId)).isZero();
        for (String table : TASK_SCOPED_TABLES) {
            assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE task_id=?", taskId)).as(table).isZero();
        }
        assertThat(count("SELECT COUNT(*) FROM ai_calls WHERE submission_id=?", submissionId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM prompt_versions WHERE id=?", promptVersionId)).as("prompt versions are not task-scoped").isEqualTo(1);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        String body = actions.andExpect(r -> assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isBetween(200, 299))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
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
