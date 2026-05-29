package com.labelhub.agent.outbox;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository {

    List<OutboxEvent> findDueAiReviewEvents(int batchSize, int leaseSeconds);

    boolean claim(Long eventId, String workerId, int leaseSeconds);

    void markProcessed(Long eventId, String workerId);

    void scheduleRetry(Long eventId, String workerId, int retryCount, LocalDateTime nextRetryAt);

    void markDeadLetter(Long eventId, String workerId, int retryCount);
}
