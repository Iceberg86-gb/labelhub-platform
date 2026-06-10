package com.labelhub.agent.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.ExportApiClient;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxExportWorker {

    private final OutboxRepository outboxRepository;
    private final ExportApiClient exportApiClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String workerId;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseDelayMs;
    private final int leaseSeconds;
    private final OutboxLastErrorBuilder lastErrorBuilder = new OutboxLastErrorBuilder();

    public OutboxExportWorker(
        OutboxRepository outboxRepository,
        ExportApiClient exportApiClient,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${labelhub.agent.export-outbox.worker-id:agent-export-local}") String workerId,
        @Value("${labelhub.agent.export-outbox.batch-size:5}") int batchSize,
        @Value("${labelhub.agent.export-outbox.max-attempts:3}") int maxAttempts,
        @Value("${labelhub.agent.export-outbox.base-delay-ms:1000}") long baseDelayMs,
        @Value("${labelhub.agent.export-outbox.lease-seconds:120}") int leaseSeconds
    ) {
        this.outboxRepository = outboxRepository;
        this.exportApiClient = exportApiClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.workerId = workerId;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${labelhub.agent.export-outbox.poll-delay-ms:5000}")
    public void scheduledPoll() {
        processDueEvents();
    }

    public int processDueEvents() {
        int processed = 0;
        for (OutboxEvent event : outboxRepository.findDueExportEvents(batchSize, leaseSeconds)) {
            if (process(event)) {
                processed++;
            }
        }
        return processed;
    }

    private boolean process(OutboxEvent event) {
        if (!outboxRepository.claim(event.id(), workerId, leaseSeconds)) {
            return false;
        }
        try {
            exportApiClient.runExportJob(exportJobId(event));
            outboxRepository.markProcessed(event.id(), workerId);
            return true;
        } catch (Exception exception) {
            handleFailure(event, exception);
            return false;
        }
    }

    private Long exportJobId(OutboxEvent event) throws Exception {
        JsonNode payload = objectMapper.readTree(event.payload());
        if (payload.has("exportJobId")) {
            return payload.get("exportJobId").asLong();
        }
        return event.aggregateId();
    }

    private void handleFailure(OutboxEvent event, Exception exception) {
        if (OutboxNonRetryable.isNonRetryable(exception)) {
            // Deterministic failure — retrying cannot succeed, so dead-letter immediately.
            outboxRepository.markDeadLetter(event.id(), workerId, maxAttempts, lastErrorBuilder.buildLastError(exception));
            return;
        }
        int nextRetryCount = event.retryCount() + 1;
        if (nextRetryCount >= maxAttempts) {
            outboxRepository.markDeadLetter(event.id(), workerId, nextRetryCount, lastErrorBuilder.buildLastError(exception));
            return;
        }
        long delayMs = baseDelayMs * (1L << Math.max(0, nextRetryCount - 1));
        outboxRepository.scheduleRetry(
            event.id(),
            workerId,
            nextRetryCount,
            LocalDateTime.now(clock).plusNanos(delayMs * 1_000_000)
        );
    }
}
