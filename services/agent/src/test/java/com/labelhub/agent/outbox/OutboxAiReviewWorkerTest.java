package com.labelhub.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.AiReviewApiClient;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultEnvelope;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.api.DimensionScorePayload;
import com.labelhub.agent.llm.AiReviewProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxAiReviewWorkerTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final AiReviewApiClient apiClient = mock(AiReviewApiClient.class);
    private final AiReviewProvider provider = mock(AiReviewProvider.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-29T12:00:00Z"), ZoneOffset.UTC);
    private final OutboxAiReviewWorker worker = new OutboxAiReviewWorker(
        repository,
        apiClient,
        provider,
        new ObjectMapper(),
        clock,
        "worker-1",
        10,
        3,
        1000,
        60
    );

    @Test
    void processDueEvents_claims_event_gets_context_reports_result_and_marks_processed() {
        OutboxEvent event = event(1L, 0, "{\"submissionId\":300}");
        AiReviewContext context = context();
        AiReviewResultPayload result = result();
        when(repository.findDueAiReviewEvents(10, 60)).thenReturn(List.of(event));
        when(repository.claim(1L, "worker-1", 60)).thenReturn(true);
        when(apiClient.getContext(300L)).thenReturn(context);
        when(provider.review(context)).thenReturn(result);

        int processed = worker.processDueEvents();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<AiReviewResultEnvelope> captor = ArgumentCaptor.forClass(AiReviewResultEnvelope.class);
        verify(apiClient).reportResult(captor.capture());
        assertThat(captor.getValue().submissionId()).isEqualTo(300L);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("submission:300:ai_review:promptVersionId:7:adapter:agent-default-v1");
        verify(repository).markProcessed(1L, "worker-1");
    }

    @Test
    void processDueEvents_schedules_retry_with_exponential_backoff_on_failure() {
        OutboxEvent event = event(2L, 1, "{\"submissionId\":301}");
        when(repository.findDueAiReviewEvents(10, 60)).thenReturn(List.of(event));
        when(repository.claim(2L, "worker-1", 60)).thenReturn(true);
        when(apiClient.getContext(301L)).thenThrow(new IllegalStateException("api unavailable"));

        int processed = worker.processDueEvents();

        assertThat(processed).isZero();
        verify(repository).scheduleRetry(2L, "worker-1", 2, LocalDateTime.parse("2026-05-29T12:00:02"));
        verify(repository, never()).markProcessed(2L, "worker-1");
    }

    @Test
    void processDueEvents_marks_dead_letter_after_max_attempts() {
        OutboxEvent event = event(3L, 2, "{\"submissionId\":302}");
        when(repository.findDueAiReviewEvents(10, 60)).thenReturn(List.of(event));
        when(repository.claim(3L, "worker-1", 60)).thenReturn(true);
        when(apiClient.getContext(302L)).thenThrow(new IllegalStateException("bad payload"));

        worker.processDueEvents();

        verify(repository).markDeadLetter(3L, "worker-1", 3);
        verify(repository, never()).scheduleRetry(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    private static OutboxEvent event(Long id, int retryCount, String payload) {
        return new OutboxEvent(
            id,
            "submission",
            300L,
            "ai_review",
            payload,
            "pending",
            retryCount,
            LocalDateTime.parse("2026-05-29T11:59:00"),
            null,
            null
        );
    }

    private static AiReviewContext context() {
        return new AiReviewContext(
            300L,
            "submission:300:ai_review:promptVersionId:7:adapter:agent-default-v1",
            "promptVersion#7",
            7L,
            19L,
            "agent-default-v1",
            Map.of("answerPayload", Map.of("field-title", "answer")),
            "a".repeat(64),
            List.of("quality"),
            new BigDecimal("0.80"),
            new BigDecimal("0.20"),
            "equal-weight-threshold-v1",
            "business prompt",
            "rendered prompt"
        );
    }

    private static AiReviewResultPayload result() {
        return new AiReviewResultPayload(
            "pass",
            new BigDecimal("0.90"),
            List.of(new DimensionScorePayload("quality", new BigDecimal("0.90"), "ok")),
            "ok",
            List.of(),
            "{}",
            1,
            1,
            null,
            5,
            "fake",
            "fake-v1",
            Map.of("overallSuggestion", "pass")
        );
    }
}
