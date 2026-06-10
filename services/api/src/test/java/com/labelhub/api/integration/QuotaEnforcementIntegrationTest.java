package com.labelhub.api.integration;

import com.labelhub.api.module.session.exception.TaskNotAvailableException;
import com.labelhub.api.module.session.service.SessionService;
import com.labelhub.api.module.session.service.view.ClaimBatchResultView;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB verification of task quota enforcement (P0-2). Self-contained: seeds its own users and
 * published task fixtures via JDBC rather than relying on demo-seed data, so it does not depend on
 * the shared integration fixtures.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class QuotaEnforcementIntegrationTest {

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
    TaskMapper taskMapper;

    @Autowired
    SessionService sessionService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void single_claim_increment_is_rejected_when_quota_is_full() {
        long ownerId = seedUser("owner-full");
        long taskId = seedPublishedTask("quota-full", ownerId, 1, 1, 2);

        int rows = taskMapper.incrementQuotaClaimedIfAvailable(taskId);

        assertThat(rows).isZero();
        assertThat(quotaClaimed(taskId)).isEqualTo(1);
    }

    @Test
    void single_claim_increment_succeeds_while_quota_remains() {
        long ownerId = seedUser("owner-remains");
        long taskId = seedPublishedTask("quota-remains", ownerId, 2, 0, 2);

        assertThat(taskMapper.incrementQuotaClaimedIfAvailable(taskId)).isEqualTo(1);
        assertThat(quotaClaimed(taskId)).isEqualTo(1);
        assertThat(taskMapper.incrementQuotaClaimedIfAvailable(taskId)).isEqualTo(1);
        assertThat(quotaClaimed(taskId)).isEqualTo(2);
        // Third attempt is now over quota and must be refused.
        assertThat(taskMapper.incrementQuotaClaimedIfAvailable(taskId)).isZero();
        assertThat(quotaClaimed(taskId)).isEqualTo(2);
    }

    @Test
    void concurrent_single_claims_never_exceed_quota_total() throws Exception {
        long ownerId = seedUser("owner-concurrent");
        int quotaTotal = 5;
        int threads = 24;
        long taskId = seedPublishedTask("quota-concurrent", ownerId, quotaTotal, 0, 0);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                start.await();
                return taskMapper.incrementQuotaClaimedIfAvailable(taskId);
            });
        }
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Integer> future : futures) {
            succeeded += future.get();
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(quotaTotal);
        assertThat(quotaClaimed(taskId)).isEqualTo(quotaTotal);
    }

    @Test
    void concurrent_single_claims_via_service_never_exceed_quota() throws Exception {
        long ownerId = seedUser("owner-claim-service");
        long labelerId = seedUser("labeler-claim-service");
        int quotaTotal = 4;
        int threads = 16;
        // More available items than the quota, so quota (not item availability) is the limiter.
        long taskId = seedPublishedTask("quota-claim-service", ownerId, quotaTotal, 0, threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                start.await();
                try {
                    sessionService.claim(taskId, labelerId);
                    return true;
                } catch (RuntimeException ignored) {
                    return false;
                }
            });
        }
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(task));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> future : futures) {
            if (Boolean.TRUE.equals(future.get())) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(quotaTotal);
        assertThat(quotaClaimed(taskId)).isEqualTo(quotaTotal);
        Integer sessionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE task_id=?", Integer.class, taskId);
        assertThat(sessionCount).isEqualTo(quotaTotal);
    }

    @Test
    void batch_increment_is_rejected_when_it_would_exceed_quota() {
        long ownerId = seedUser("owner-batch-guard");
        long taskId = seedPublishedTask("quota-batch-guard", ownerId, 3, 2, 5);

        // remaining quota = 1: an increment of 2 must be refused, an increment of 1 accepted.
        assertThat(taskMapper.incrementQuotaClaimedBy(taskId, 2)).isZero();
        assertThat(quotaClaimed(taskId)).isEqualTo(2);
        assertThat(taskMapper.incrementQuotaClaimedBy(taskId, 1)).isEqualTo(1);
        assertThat(quotaClaimed(taskId)).isEqualTo(3);
    }

    @Test
    void claim_batch_caps_to_remaining_quota_end_to_end() {
        long ownerId = seedUser("owner-batch-cap");
        long labelerId = seedUser("labeler-batch-cap");
        long taskId = seedPublishedTask("quota-batch-cap", ownerId, 3, 2, 5);

        ClaimBatchResultView result = sessionService.claimBatch(taskId, labelerId, 5);

        assertThat(result.claimedCount()).isEqualTo(1);
        assertThat(quotaClaimed(taskId)).isEqualTo(3);
        Integer sessionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE task_id=? AND labeler_id=?", Integer.class, taskId, labelerId);
        assertThat(sessionCount).isEqualTo(1);
    }

    @Test
    void claim_batch_is_rejected_when_quota_is_exhausted_end_to_end() {
        long ownerId = seedUser("owner-batch-exhausted");
        long labelerId = seedUser("labeler-batch-exhausted");
        long taskId = seedPublishedTask("quota-batch-exhausted", ownerId, 2, 2, 2);

        assertThatThrownBy(() -> sessionService.claimBatch(taskId, labelerId, 5))
            .isInstanceOf(TaskNotAvailableException.class);
        assertThat(quotaClaimed(taskId)).isEqualTo(2);
        Integer sessionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE task_id=?", Integer.class, taskId);
        assertThat(sessionCount).isZero();
    }

    private int quotaClaimed(long taskId) {
        Integer claimed = jdbcTemplate.queryForObject("SELECT quota_claimed FROM tasks WHERE id=?", Integer.class, taskId);
        return claimed == null ? -1 : claimed;
    }

    private long seedUser(String username) {
        return insertAndReturnId(
            "INSERT INTO users(username, display_name, password_hash, status) VALUES (?, ?, 'x', 'active')",
            username, username);
    }

    private long seedPublishedTask(String seed, long ownerId, int quotaTotal, int quotaClaimed, int itemCount) {
        long taskId = insertAndReturnId("""
            INSERT INTO tasks(title, description, instruction_rich_text, deadline_at, quota_total, quota_claimed, status, owner_id)
            VALUES (?, 'quota fixture', '<p>q</p>', ?, ?, ?, 'published', ?)
            """, seed, LocalDateTime.parse("2030-01-01T00:00:00"), quotaTotal, quotaClaimed, ownerId);
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
        for (int i = 0; i < itemCount; i++) {
            insertAndReturnId("""
                INSERT INTO dataset_items(dataset_id, task_id, ordinal, item_payload, item_hash)
                VALUES (?, ?, ?, JSON_OBJECT('row', ?), ?)
                """, datasetId, taskId, i, i, fixedHash(seed + "-item-" + i));
        }
        return taskId;
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
}
