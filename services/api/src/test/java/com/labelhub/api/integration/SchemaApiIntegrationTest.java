package com.labelhub.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class SchemaApiIntegrationTest {

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
    void owner_can_create_then_publish_v1_then_v2_then_list_versions() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Schema owner flow");
        long schemaId = createSchema(token, taskId, "Product schema");

        publishSchema(token, schemaId, schemaDocumentJson(3))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.versionNumber").value(1))
            .andExpect(jsonPath("$['schemaJson']['x-labelhub-fields']", hasSize(3)));
        publishSchema(token, schemaId, schemaDocumentJson(5))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.versionNumber").value(2))
            .andExpect(jsonPath("$['schemaJson']['x-labelhub-fields']", hasSize(5)));

        mockMvc.perform(get("/schemas/{schemaId}/versions", schemaId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].versionNumber").value(1))
            .andExpect(jsonPath("$[1].versionNumber").value(2));
    }

    @Test
    void historical_render_schema_returns_v1_after_v2_is_current() throws Exception {
        String ownerToken = login("owner_demo", "demo1234");
        String labelerToken = login("labeler_demo", "demo1234");
        long taskId = createDraftTask(ownerToken, "Historical render");
        long schemaId = createSchema(ownerToken, taskId, "Historical schema");
        long v1 = publishSchemaId(ownerToken, schemaId, schemaDocumentJson(3));
        publishSchemaId(ownerToken, schemaId, schemaDocumentJson(5));
        long submissionId = insertSubmission(taskId, v1);

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", submissionId)
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submissionId").value(submissionId))
            .andExpect(jsonPath("$.schemaVersion.id").value(v1))
            .andExpect(jsonPath("$.schemaVersion.versionNumber").value(1))
            .andExpect(jsonPath("$['schemaVersion']['schemaJson']['x-labelhub-fields']", hasSize(3)))
            .andExpect(jsonPath("$.answerPayload.field_0").value("answer"));
    }

    @Test
    void cross_owner_get_schema_returns_404() throws Exception {
        String ownerToken = login("owner_demo", "demo1234");
        long taskId = createDraftTask(ownerToken, "Cross owner");
        long schemaId = createSchema(ownerToken, taskId, "Private schema");

        mockMvc.perform(get("/schemas/{schemaId}", schemaId)
                .header("Authorization", bearer(ownerToken(2001L, "owner_two"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalid_schema_document_returns_400_with_field_errors() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Invalid schema");
        long schemaId = createSchema(token, taskId, "Invalid schema");

        publishSchema(token, schemaId, """
            {"fields":[
              {"stableId":"dup","label":"A","type":"text"},
              {"stableId":"dup","label":"B","type":"number"}
            ]}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_SCHEMA_DOCUMENT"))
            .andExpect(jsonPath("$.fieldErrors[0].field").value("fields[1].stableId"));
    }

    @Test
    void publish_schema_request_omitting_fieldStableIds_succeeds() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "No client stable IDs");
        long schemaId = createSchema(token, taskId, "No client stable IDs");

        publishSchema(token, schemaId, schemaDocumentJson(1))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fieldStableIds[0]").value("field_0"));
    }

    @Test
    void unauthorized_create_schema_returns_401() throws Exception {
        mockMvc.perform(post("/schemas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No auth\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void labeler_role_create_schema_returns_403() throws Exception {
        String ownerToken = login("owner_demo", "demo1234");
        long taskId = createDraftTask(ownerToken, "Forbidden schema");
        String labelerToken = login("labeler_demo", "demo1234");

        mockMvc.perform(post("/schemas")
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":" + taskId + ",\"name\":\"Forbidden\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void render_schema_works_for_authenticated_labeler() throws Exception {
        String ownerToken = login("owner_demo", "demo1234");
        String labelerToken = login("labeler_demo", "demo1234");
        long taskId = createDraftTask(ownerToken, "Labeler render");
        long schemaId = createSchema(ownerToken, taskId, "Labeler render schema");
        long versionId = publishSchemaId(ownerToken, schemaId, schemaDocumentJson(2));
        long submissionId = insertSubmission(taskId, versionId);

        mockMvc.perform(get("/submissions/{submissionId}/render-schema", submissionId)
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion.id").value(versionId));
    }

    @Test
    void getVersion_returns_404_when_version_belongs_to_different_schema() throws Exception {
        String token = login("owner_demo", "demo1234");
        long taskId = createDraftTask(token, "Wrong schema version");
        long schemaA = createSchema(token, taskId, "A");
        long schemaB = createSchema(token, taskId, "B");
        long versionB = publishSchemaId(token, schemaB, schemaDocumentJson(1));

        mockMvc.perform(get("/schemas/{schemaId}/versions/{versionId}", schemaA, versionB)
                .header("Authorization", bearer(token)))
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

    private long createDraftTask(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/tasks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "%s",
                      "description": "Schema integration task",
                      "instructionRichText": "<p>Schema integration</p>",
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

    private long createSchema(String token, long taskId, String name) throws Exception {
        String body = mockMvc.perform(post("/schemas")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":" + taskId + ",\"name\":\"" + name + "\",\"description\":\"Demo\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions publishSchema(String token, long schemaId, String schemaJson) throws Exception {
        return mockMvc.perform(post("/schemas/{schemaId}/versions", schemaId)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"schemaJson\":" + schemaJson + "}"));
    }

    private long publishSchemaId(String token, long schemaId, String schemaJson) throws Exception {
        String body = publishSchema(token, schemaId, schemaJson)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long insertSubmission(long taskId, long schemaVersionId) {
        Long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', 1, 'imported')",
            taskId);
        Long itemId = insertAndReturnId(
            "INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash) VALUES (?, ?, 0, JSON_OBJECT('source','demo'), ?)",
            datasetId, taskId, fixedHash("item"));
        Long sessionId = insertAndReturnId(
            "INSERT INTO sessions(task_id, dataset_item_id, labeler_id, schema_version_id, claim_snapshot, status) VALUES (?, ?, 1002, ?, JSON_OBJECT('claim','demo'), 'submitted')",
            taskId, itemId, schemaVersionId);
        return insertAndReturnId(
            "INSERT INTO submissions(session_id, task_id, dataset_item_id, labeler_id, schema_version_id, answer_payload, provenance, content_hash, status) VALUES (?, ?, ?, 1002, ?, JSON_OBJECT('field_0','answer'), JSON_OBJECT('source','manual'), ?, 'under_ai_review')",
            sessionId, taskId, itemId, schemaVersionId, fixedHash("submission"));
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

    private String schemaDocumentJson(int fieldCount) {
        String fields = java.util.stream.IntStream.range(0, fieldCount)
            .mapToObj(index -> """
                {"stableId":"field_%d","label":"Field %d","type":"text"}
                """.formatted(index, index).trim())
            .collect(java.util.stream.Collectors.joining(","));
        return "{\"fields\":[" + fields + "]}";
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
}
