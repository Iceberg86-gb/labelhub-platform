package com.labelhub.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class SubmissionLifecycleIntegrationTest {

    private static final String JWT_SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";
    private static final String INTERNAL_TOKEN = "dev-internal-token";
    private static final String EXPORT_BUCKET = "labelhub-lifecycle-test";
    private static final String MINIO_USER = "testadmin";
    private static final String MINIO_PASSWORD = "testadmin123";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("labelhub")
        .withUsername("labelhub")
        .withPassword("labelhub");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
        .withEnv("MINIO_ROOT_USER", MINIO_USER)
        .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
        .withCommand("server", "/data")
        .withExposedPorts(9000)
        .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("labelhub.security.jwt-secret", () -> JWT_SECRET);
        registry.add("labelhub.security.internal-token", () -> INTERNAL_TOKEN);
        registry.add("labelhub.object-storage.endpoint",
            () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("labelhub.object-storage.access-key", () -> MINIO_USER);
        registry.add("labelhub.object-storage.secret-key", () -> MINIO_PASSWORD);
        registry.add("labelhub.object-storage.bucket", () -> EXPORT_BUCKET);
        registry.add("labelhub.object-storage.path-style-access", () -> true);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    S3Client s3Client;

    @BeforeEach
    void ensureBucket() {
        try {
            s3Client.createBucket(b -> b.bucket(EXPORT_BUCKET));
        } catch (S3Exception e) {
            if (!"BucketAlreadyOwnedByYou".equals(e.awsErrorDetails().errorCode())
                && !"BucketAlreadyExists".equals(e.awsErrorDetails().errorCode())) {
                throw e;
            }
        }
    }

    @Test
    void labeler_submit_appears_in_default_reviewer_queue() throws Exception {
        Fixture fixture = publishedTaskFixture("lifecycle-queue", 1, 1);
        long submissionId = claimAndSubmit(fixture);

        mockMvc.perform(get("/reviewer/submissions")
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].id").value(hasItem((int) submissionId)));
    }

    @Test
    void trusted_export_includes_submission_created_by_real_submit_path() throws Exception {
        Fixture fixture = publishedTaskFixture("lifecycle-export", 1, 1);
        claimAndSubmit(fixture);

        MvcResult result = mockMvc.perform(post("/tasks/{taskId}/exports", fixture.taskId())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode snapshot = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(snapshot.get("recordCounts").get("submissions").asInt()).isEqualTo(1);
    }

    private long claimAndSubmit(Fixture fixture) throws Exception {
        String labelerToken = tokenForUser(1002L, "labeler_demo", "LABELER");
        String claimBody = mockMvc.perform(post("/tasks/{taskId}/claim", fixture.taskId())
                .header("Authorization", bearer(labelerToken)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long sessionId = objectMapper.readTree(claimBody).get("id").asLong();

        String submitBody = mockMvc.perform(post("/sessions/{sessionId}/submit", sessionId)
                .header("Authorization", bearer(labelerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerPayload\":{\"field_0\":\"real submit answer\"}}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(submitBody).get("id").asLong();
    }

    private Fixture publishedTaskFixture(String title, int quotaTotal, int itemCount) {
        Long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'Lifecycle integration', '<p>Lifecycle</p>', ?, ?, 0, 'published', 1001)
            """, title, LocalDateTime.parse("2030-01-01T00:00:00"), quotaTotal);
        Long schemaId = insertAndReturnId(
            "INSERT INTO label_schemas(task_id, name, description, owner_id) VALUES (?, ?, 'Lifecycle schema', 1001)",
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
        return new Fixture(taskId);
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

    private record Fixture(Long taskId) {}
}
