package com.labelhub.api.integration;

import com.labelhub.api.module.quality.service.ReviewerBatchItemResult;
import com.labelhub.api.module.quality.service.ReviewerBatchResult;
import com.labelhub.api.module.quality.service.ReviewerBatchService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB verification that batch review isolates per-item ledger writes (P1-8). A self-review item
 * must fail without rolling back a sibling item that already committed. With the old shared-transaction
 * behaviour the whole batch would roll back (UnexpectedRollbackException) and the valid item's entry
 * would be lost; with REQUIRES_NEW the valid item commits and survives.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ReviewerBatchTransactionIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("labelhub")
        .withUsername("labelhub")
        .withPassword("labelhub");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("labelhub.security.jwt-secret", () -> "dev-only-32-bytes-minimum-secret-please-change-me");
        registry.add("labelhub.security.internal-token", () -> "dev-internal-token");
        registry.add("labelhub.object-storage.endpoint", () -> "http://localhost:9000");
        registry.add("labelhub.object-storage.region", () -> "us-east-1");
        registry.add("labelhub.object-storage.access-key", () -> "test-access-key");
        registry.add("labelhub.object-storage.secret-key", () -> "test-secret-key");
        registry.add("labelhub.object-storage.bucket", () -> "labelhub-exports");
        registry.add("labelhub.object-storage.path-style-access", () -> true);
    }

    @Autowired
    ReviewerBatchService reviewerBatchService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void self_review_item_does_not_roll_back_a_sibling_item_that_committed() {
        long ownerId = seedUser("owner-batch-tx");
        long labelerId = seedUser("labeler-batch-tx");
        long reviewerId = seedUser("reviewer-batch-tx");
        Fixture fixture = seedTask("batch-tx", ownerId, 2);

        long validSubmission = seedSubmission(fixture, labelerId, 0);   // reviewable by the reviewer
        long selfReviewSubmission = seedSubmission(fixture, reviewerId, 1); // self-review -> must fail

        ReviewerBatchResult result = reviewerBatchService.reviewSubmissions(
            List.of(validSubmission, selfReviewSubmission),
            reviewerId,
            "approve",
            null,
            "reviewer",
            Set.of("REVIEWER"));

        assertThat(result.items()).extracting(ReviewerBatchItemResult::submissionId)
            .containsExactly(validSubmission, selfReviewSubmission);
        assertThat(result.items()).extracting(ReviewerBatchItemResult::status)
            .containsExactly("created", "self_review_not_allowed");

        // The valid item's ledger entry committed and survives the sibling's rollback.
        assertThat(ledgerEntryCount(validSubmission)).isEqualTo(1);
        assertThat(ledgerEntryCount(selfReviewSubmission)).isZero();
    }

    private int ledgerEntryCount(long submissionId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM quality_ledger_entries WHERE submission_id=?", Integer.class, submissionId);
        return count == null ? -1 : count;
    }

    private long seedUser(String username) {
        return insertAndReturnId(
            "INSERT INTO users(username, display_name, password_hash, status) VALUES (?, ?, 'x', 'active')",
            username, username);
    }

    private Fixture seedTask(String seed, long ownerId, int itemCount) {
        long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'batch tx', '<p>q</p>', ?, ?, 0, 'published', ?)
            """, seed, LocalDateTime.parse("2030-01-01T00:00:00"), itemCount, ownerId);
        long schemaId = insertAndReturnId(
            "INSERT INTO label_schemas(task_id, name, owner_id) VALUES (?, ?, ?)", taskId, seed + " schema", ownerId);
        long schemaVersionId = insertAndReturnId("""
            INSERT INTO schema_versions(schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
            VALUES (?, 1, JSON_OBJECT('fields', JSON_ARRAY(JSON_OBJECT('stableId','field_0','label','Field 0','type','text'))),
                    JSON_ARRAY('field_0'), ?, 'published', NOW(3), NOW(3))
            """, schemaId, fixedHash(seed + "-schema"));
        long datasetId = insertAndReturnId(
            "INSERT INTO datasets(task_id, source_type, item_count, import_status) VALUES (?, 'json', ?, 'imported')",
            taskId, itemCount);
        jdbcTemplate.update("UPDATE tasks SET current_schema_version_id=?, current_dataset_id=? WHERE id=?",
            schemaVersionId, datasetId, taskId);
        return new Fixture(taskId, schemaVersionId, datasetId);
    }

    private long seedSubmission(Fixture fixture, long labelerId, int ordinal) {
        long itemId = insertAndReturnId("""
            INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
            VALUES (?, ?, ?, JSON_OBJECT('row', ?), ?)
            """, fixture.datasetId(), fixture.taskId(), ordinal, ordinal, fixedHash(fixture.taskId() + "-item-" + ordinal));
        long sessionId = insertAndReturnId("""
            INSERT INTO sessions(task_id, dataset_item_id, labeler_id, schema_version_id, claim_snapshot, status, claimed_at)
            VALUES (?, ?, ?, ?, JSON_OBJECT(), 'submitted', NOW(3))
            """, fixture.taskId(), itemId, labelerId, fixture.schemaVersionId());
        return insertAndReturnId("""
            INSERT INTO submissions(session_id, task_id, dataset_item_id, labeler_id, schema_version_id, answer_payload, content_hash, status, created_at)
            VALUES (?, ?, ?, ?, ?, JSON_OBJECT('field_0','answer'), ?, 'under_ai_review', NOW(3))
            """, sessionId, fixture.taskId(), itemId, labelerId, fixture.schemaVersionId(), fixedHash(sessionId + "-submission"));
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

    private record Fixture(long taskId, long schemaVersionId, long datasetId) {
    }
}
