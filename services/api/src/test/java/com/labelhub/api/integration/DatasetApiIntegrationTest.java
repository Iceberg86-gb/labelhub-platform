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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class DatasetApiIntegrationTest {

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
    void upload_creates_dataset_and_items_via_multipart() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Dataset upload happy");

        String body = uploadJsonl(token, taskId, """
            {"text":"one"}
            {"text":"two"}
            {"text":"three"}
            {"text":"four"}
            {"text":"five"}
            """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.taskId").value(taskId))
            .andExpect(jsonPath("$.itemCount").value(5))
            .andExpect(jsonPath("$.importStatus").value("completed"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long datasetId = objectMapper.readTree(body).get("id").asLong();

        assertThat(jdbcTemplate.queryForList(
            "SELECT ordinal FROM dataset_items WHERE dataset_id=? ORDER BY ordinal", Integer.class, datasetId))
            .containsExactly(1, 2, 3, 4, 5);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dataset_items WHERE dataset_id=? AND CHAR_LENGTH(item_hash)=64", Integer.class, datasetId))
            .isEqualTo(5);

        mockMvc.perform(get("/datasets?taskId={taskId}", taskId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)));
        mockMvc.perform(get("/datasets/{datasetId}", datasetId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(datasetId));
    }

    @Test
    void upload_returns_400_for_empty_dataset() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Dataset empty");

        mockMvc.perform(multipart("/datasets")
                .file(file("empty.json", "[]"))
                .param("taskId", String.valueOf(taskId))
                .param("format", "json")
                .header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EMPTY_DATASET"));
    }

    @Test
    void upload_returns_400_for_malformed_jsonl_with_line_number() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Dataset malformed");

        uploadJsonl(token, taskId, """
            {"text":"ok"}
            not-json
            {"text":"after"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_DATASET_FILE"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("line 2")));
    }

    @Test
    void upload_returns_404_for_cross_owner_task() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Dataset cross owner");

        uploadJsonl(ownerToken(2001L, "owner_two"), taskId, "{\"text\":\"private\"}\n")
            .andExpect(status().isNotFound());
    }

    @Test
    void patch_current_dataset_succeeds_for_unpublished_task() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Dataset pointer");
        long datasetId = uploadedDatasetId(token, taskId);

        mockMvc.perform(patch("/tasks/{taskId}/current-dataset", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetId\":" + datasetId + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentDatasetId").value(datasetId));

        assertThat(jdbcTemplate.queryForObject("SELECT current_dataset_id FROM tasks WHERE id=?", Long.class, taskId))
            .isEqualTo(datasetId);
    }

    @Test
    void patch_current_dataset_returns_409_for_published_task() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Dataset published lock");
        long datasetId = uploadedDatasetId(token, taskId);
        jdbcTemplate.update("UPDATE tasks SET status='published' WHERE id=?", taskId);

        mockMvc.perform(patch("/tasks/{taskId}/current-dataset", taskId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetId\":" + datasetId + "}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TASK_PUBLISHED_LOCK"));
    }

    private org.springframework.test.web.servlet.ResultActions uploadJsonl(String token, long taskId, String content) throws Exception {
        return mockMvc.perform(multipart("/datasets")
            .file(file("sample.jsonl", content))
            .param("taskId", String.valueOf(taskId))
            .param("format", "jsonl")
            .header("Authorization", bearer(token)));
    }

    private long uploadedDatasetId(String token, long taskId) throws Exception {
        String body = uploadJsonl(token, taskId, "{\"text\":\"one\"}\n")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/json", content.getBytes(StandardCharsets.UTF_8));
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

    private long createDraftTask(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/tasks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "%s",
                      "description": "Dataset integration task",
                      "instructionRichText": "<p>Dataset integration</p>",
                      "deadlineAt": "2030-01-01T00:00:00Z",
                      "quotaTotal": 5
                    }
                    """.formatted(title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String ownerToken(Long userId, String username) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("username", username)
            .claim("roles", java.util.List.of("OWNER"))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
