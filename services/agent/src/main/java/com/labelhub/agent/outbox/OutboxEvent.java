package com.labelhub.agent.outbox;

import java.time.LocalDateTime;

public record OutboxEvent(
    Long id,
    String aggregateType,
    Long aggregateId,
    String eventType,
    String payload,
    String status,
    int retryCount,
    LocalDateTime nextRetryAt,
    String lockedBy,
    LocalDateTime lockedAt
) {
}
