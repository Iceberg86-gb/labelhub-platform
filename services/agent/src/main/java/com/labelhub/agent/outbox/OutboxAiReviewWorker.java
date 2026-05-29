package com.labelhub.agent.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.AiReviewApiClient;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultEnvelope;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.llm.AiReviewProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxAiReviewWorker {

    private final OutboxRepository outboxRepository;
    private final AiReviewApiClient aiReviewApiClient;
    private final AiReviewProvider aiReviewProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String workerId;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseDelayMs;
    private final int leaseSeconds;

    public OutboxAiReviewWorker(
        OutboxRepository outboxRepository,
        AiReviewApiClient aiReviewApiClient,
        AiReviewProvider aiReviewProvider,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${labelhub.agent.outbox.worker-id:agent-local}") String workerId,
        @Value("${labelhub.agent.outbox.batch-size:10}") int batchSize,
        @Value("${labelhub.agent.outbox.max-attempts:3}") int maxAttempts,
        @Value("${labelhub.agent.outbox.base-delay-ms:1000}") long baseDelayMs,
        @Value("${labelhub.agent.outbox.lease-seconds:60}") int leaseSeconds
    ) {
        this.outboxRepository = outboxRepository;
        this.aiReviewApiClient = aiReviewApiClient;
        this.aiReviewProvider = aiReviewProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.workerId = workerId;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${labelhub.agent.outbox.poll-delay-ms:5000}")
    public void scheduledPoll() {
        processDueEvents();
    }

    public int processDueEvents() {
        int processed = 0;
        for (OutboxEvent event : outboxRepository.findDueAiReviewEvents(batchSize, leaseSeconds)) {
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
            Long submissionId = submissionId(event);
            AiReviewContext context = aiReviewApiClient.getContext(submissionId);
            AiReviewResultPayload result = aiReviewProvider.review(context);
            aiReviewApiClient.reportResult(new AiReviewResultEnvelope(
                context.submissionId(),
                context.idempotencyKey(),
                result
            ));
            outboxRepository.markProcessed(event.id(), workerId);
            return true;
        } catch (Exception exception) {
            handleFailure(event);
            return false;
        }
    }

    private Long submissionId(OutboxEvent event) throws Exception {
        JsonNode payload = objectMapper.readTree(event.payload());
        if (payload.has("submissionId")) {
            return payload.get("submissionId").asLong();
        }
        return event.aggregateId();
    }

    private void handleFailure(OutboxEvent event) {
        int nextRetryCount = event.retryCount() + 1;
        if (nextRetryCount >= maxAttempts) {
            outboxRepository.markDeadLetter(event.id(), workerId, nextRetryCount);
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
