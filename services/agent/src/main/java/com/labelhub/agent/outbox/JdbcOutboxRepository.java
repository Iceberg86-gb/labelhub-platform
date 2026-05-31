package com.labelhub.agent.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OutboxEvent> findDueAiReviewEvents(int batchSize, int leaseSeconds) {
        return findDueEvents("ai_review", batchSize, leaseSeconds);
    }

    @Override
    public List<OutboxEvent> findDueExportEvents(int batchSize, int leaseSeconds) {
        return findDueEvents("export.requested", batchSize, leaseSeconds);
    }

    private List<OutboxEvent> findDueEvents(String eventType, int batchSize, int leaseSeconds) {
        return jdbcTemplate.query("""
            SELECT id, aggregate_type, aggregate_id, event_type, payload, status, retry_count,
                   next_retry_at, locked_by, locked_at
            FROM outbox
            WHERE event_type = ?
              AND (
                  (status = 'pending' AND next_retry_at <= CURRENT_TIMESTAMP(3))
                  OR (status = 'processing' AND locked_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND))
              )
            ORDER BY status ASC, next_retry_at ASC, id ASC
            LIMIT ?
            """, this::mapRow, eventType, leaseSeconds, batchSize);
    }

    @Override
    public boolean claim(Long eventId, String workerId, int leaseSeconds) {
        int rows = jdbcTemplate.update("""
            UPDATE outbox
            SET status = 'processing', locked_by = ?, locked_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
              AND (status = 'pending'
                   OR (status = 'processing' AND locked_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND)))
            """, workerId, eventId, leaseSeconds);
        return rows == 1;
    }

    @Override
    public void markProcessed(Long eventId, String workerId) {
        jdbcTemplate.update("""
            UPDATE outbox
            SET status = 'processed', processed_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND locked_by = ?
            """, eventId, workerId);
    }

    @Override
    public void scheduleRetry(Long eventId, String workerId, int retryCount, LocalDateTime nextRetryAt) {
        jdbcTemplate.update("""
            UPDATE outbox
            SET status = 'pending', retry_count = ?, next_retry_at = ?, locked_by = NULL, locked_at = NULL
            WHERE id = ? AND locked_by = ?
            """, retryCount, nextRetryAt, eventId, workerId);
    }

    @Override
    public void markDeadLetter(Long eventId, String workerId, int retryCount) {
        jdbcTemplate.update("""
            UPDATE outbox
            SET status = 'dead_letter', retry_count = ?, locked_by = NULL, locked_at = NULL
            WHERE id = ? AND locked_by = ?
            """, retryCount, eventId, workerId);
    }

    private OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(
            rs.getLong("id"),
            rs.getString("aggregate_type"),
            rs.getLong("aggregate_id"),
            rs.getString("event_type"),
            rs.getString("payload"),
            rs.getString("status"),
            rs.getInt("retry_count"),
            rs.getTimestamp("next_retry_at").toLocalDateTime(),
            rs.getString("locked_by"),
            rs.getTimestamp("locked_at") == null ? null : rs.getTimestamp("locked_at").toLocalDateTime()
        );
    }
}
