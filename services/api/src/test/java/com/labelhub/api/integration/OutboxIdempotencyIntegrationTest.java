package com.labelhub.api.integration;

import com.labelhub.api.module.outbox.entity.OutboxEventEntity;
import com.labelhub.api.module.outbox.service.OutboxEventService;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB verification of the outbox idempotency guard (P0-3). The outbox now carries a unique key
 * on (aggregate_type, aggregate_id, event_type); enqueueing the same submission's ai_review event
 * more than once must be a no-op that returns the already-queued event rather than a duplicate.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class OutboxIdempotencyIntegrationTest {

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
    OutboxEventService outboxEventService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void enqueue_submission_ai_review_is_idempotent_across_repeated_calls() {
        long submissionId = 987654L;
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(submissionId);
        submission.setTaskId(11L);

        OutboxEventEntity first = outboxEventService.enqueueSubmissionAiReview(submission, null);
        OutboxEventEntity second = outboxEventService.enqueueSubmissionAiReview(submission, null);
        OutboxEventEntity third = outboxEventService.enqueueSubmissionAiReview(submission, 19L);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(third.getId()).isEqualTo(first.getId());
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE aggregate_type='submission' AND aggregate_id=? AND event_type='ai_review'",
            Integer.class, submissionId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void unique_key_is_enforced_at_database_level() {
        long submissionId = 555L;
        insertAiReviewEvent(submissionId);
        assertThatThrownBy(() -> insertAiReviewEvent(submissionId))
            .isInstanceOf(DuplicateKeyException.class);
    }

    private void insertAiReviewEvent(long submissionId) {
        jdbcTemplate.update("""
            INSERT INTO outbox(aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_retry_at, created_at)
            VALUES ('submission', ?, 'ai_review', JSON_OBJECT(), 'pending', 0, NOW(3), NOW(3))
            """, submissionId);
    }
}
