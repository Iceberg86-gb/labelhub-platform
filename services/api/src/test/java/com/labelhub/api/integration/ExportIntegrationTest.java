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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ExportIntegrationTest {

    private static final String JWT_SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";
    private static final String INTERNAL_TOKEN = "dev-internal-token";
    private static final String EXPORT_BUCKET = "labelhub-exports-test";
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
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

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
    void two_independent_http_exports_produce_identical_hash() throws Exception {
        Fixture fixture = exportFixture("export-http-identical", 1001L, 1002L);

        JsonNode snap1 = createExport(fixture.taskId());
        JsonNode snap2 = createExport(fixture.taskId());

        assertThat(snap1.get("id").asLong()).isNotEqualTo(snap2.get("id").asLong());
        assertThat(snap1.get("exportJobId").asLong()).isNotEqualTo(snap2.get("exportJobId").asLong());
        assertThat(snap1.get("objectKey").asText()).isNotEqualTo(snap2.get("objectKey").asText());
        assertThat(snap1.get("manifestHash").asText()).isEqualTo(snap2.get("manifestHash").asText());
        assertThat(snap1.get("sourceStateHash").asText()).isEqualTo(snap2.get("sourceStateHash").asText());
        assertThat(snap1.get("fileHash").asText()).isEqualTo(snap2.get("fileHash").asText());

        assertThat(objectCountUnder(snap1.get("objectKey").asText())).isEqualTo(11);
        assertThat(objectCountUnder(snap2.get("objectKey").asText())).isEqualTo(11);
    }

    @Test
    void diff_endpoint_returns_equal_for_two_identical_exports() throws Exception {
        Fixture fixture = exportFixture("export-diff-equal", 1001L, 1002L);
        JsonNode snap1 = createExport(fixture.taskId());
        JsonNode snap2 = createExport(fixture.taskId());

        mockMvc.perform(get("/exports/snapshots/{snapshotId}/diff", snap1.get("id").asLong())
                .param("compareWith", snap2.get("id").asText())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.equal").value(true))
            .andExpect(jsonPath("$.hashMatches.fileHash").value(true))
            .andExpect(jsonPath("$.hashMatches.manifestHash").value(true))
            .andExpect(jsonPath("$.hashMatches.sourceStateHash").value(true))
            .andExpect(jsonPath("$.fileLevelMatches.length()").value(10))
            .andExpect(jsonPath("$.fileLevelMatches[*].match").value(everyItem(is(true))));
    }

    @Test
    void diff_endpoint_returns_not_equal_when_source_state_differs() throws Exception {
        Fixture fixture = exportFixture("export-diff-not-equal", 1001L, 1002L);
        JsonNode snap1 = createExport(fixture.taskId());
        insertLedgerEntry(fixture.submissionId(), fixture.taskId(), 1003L, "reject");
        JsonNode snap2 = createExport(fixture.taskId());

        mockMvc.perform(get("/exports/snapshots/{snapshotId}/diff", snap1.get("id").asLong())
                .param("compareWith", snap2.get("id").asText())
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.equal").value(false))
            .andExpect(jsonPath("$.hashMatches.fileHash").value(false))
            .andExpect(jsonPath("$.hashMatches.manifestHash").value(false))
            .andExpect(jsonPath("$.hashMatches.sourceStateHash").value(false));
    }

    @Test
    void labeler_cannot_create_export_returns_403() throws Exception {
        Fixture fixture = exportFixture("export-labeler-denied", 1001L, 1002L);

        mockMvc.perform(post("/tasks/{taskId}/exports", fixture.taskId())
                .header("Authorization", bearer(tokenForUser(1002L, "labeler_demo", "LABELER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void reviewer_cannot_create_export_returns_403() throws Exception {
        Fixture fixture = exportFixture("export-reviewer-denied", 1001L, 1002L);

        mockMvc.perform(post("/tasks/{taskId}/exports", fixture.taskId())
                .header("Authorization", bearer(tokenForUser(1003L, "reviewer_demo", "REVIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void cross_owner_cannot_read_export_snapshot_returns_404() throws Exception {
        Fixture fixture = exportFixture("export-cross-owner", 1001L, 1002L);
        JsonNode snap = createExport(fixture.taskId());

        mockMvc.perform(get("/exports/snapshots/{snapshotId}", snap.get("id").asLong())
                .header("Authorization", bearer(tokenForUser(2001L, "other_owner", "OWNER"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void list_exports_returns_paged_snapshots() throws Exception {
        Fixture fixture = exportFixture("export-list", 1001L, 1002L);
        createExport(fixture.taskId());
        createExport(fixture.taskId());

        mockMvc.perform(get("/tasks/{taskId}/exports", fixture.taskId())
                .param("page", "1")
                .param("size", "20")
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void export_creates_all_11_objects_in_minio() throws Exception {
        Fixture fixture = exportFixture("export-minio-objects", 1001L, 1002L);
        JsonNode snap = createExport(fixture.taskId());

        assertThat(objectCountUnder(snap.get("objectKey").asText())).isEqualTo(11);
    }

    private JsonNode createExport(Long taskId) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks/{taskId}/exports", taskId)
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int objectCountUnder(String prefix) {
        return s3Client.listObjectsV2(r -> r.bucket(EXPORT_BUCKET).prefix(prefix)).contents().size();
    }

    private Fixture exportFixture(String seed, Long ownerId, Long labelerId) {
        Long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'Trusted export integration', '<p>Export</p>', ?, 5, 1, 'published', ?)
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
            VALUES (?, ?, ?, ?, ?, JSON_OBJECT('field-title','answer'), JSON_OBJECT('source','manual'), ?, 'submitted', NOW(3))
            """, sessionId, taskId, itemId, labelerId, schemaVersionId, fixedHash(seed + "-submission"));
        insertLedgerEntry(submissionId, taskId, 1003L, "approve");
        return new Fixture(taskId, submissionId);
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

    private record Fixture(Long taskId, Long submissionId) {}
}
