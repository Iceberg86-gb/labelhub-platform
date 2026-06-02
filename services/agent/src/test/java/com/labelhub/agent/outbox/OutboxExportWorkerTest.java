package com.labelhub.agent.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.ExportApiClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxExportWorkerTest {

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final ExportApiClient apiClient = mock(ExportApiClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-31T10:00:00Z"), ZoneOffset.UTC);
    private final OutboxExportWorker worker = new OutboxExportWorker(
        repository,
        apiClient,
        new ObjectMapper(),
        clock,
        "export-worker-1",
        5,
        3,
        1000,
        120
    );

    @Test
    void processDueEvents_uses_export_event_query_and_runs_export_job() {
        OutboxEvent event = event(1L, 0, "{\"exportJobId\":900}");
        when(repository.findDueExportEvents(5, 120)).thenReturn(List.of(event));
        when(repository.claim(1L, "export-worker-1", 120)).thenReturn(true);

        int processed = worker.processDueEvents();

        assertThat(processed).isEqualTo(1);
        verify(repository, never()).findDueAiReviewEvents(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        verify(apiClient).runExportJob(900L);
        verify(repository).markProcessed(1L, "export-worker-1");
    }

    @Test
    void processDueEvents_schedules_retry_on_failure() {
        OutboxEvent event = event(2L, 1, "{\"exportJobId\":901}");
        when(repository.findDueExportEvents(5, 120)).thenReturn(List.of(event));
        when(repository.claim(2L, "export-worker-1", 120)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("api down")).when(apiClient).runExportJob(901L);

        int processed = worker.processDueEvents();

        assertThat(processed).isZero();
        verify(repository).scheduleRetry(2L, "export-worker-1", 2, LocalDateTime.parse("2026-05-31T10:00:02"));
        verify(repository, never()).markProcessed(2L, "export-worker-1");
    }

    @Test
    void processDueEvents_marks_dead_letter_after_max_attempts() {
        OutboxEvent event = event(3L, 2, "{\"exportJobId\":902}");
        when(repository.findDueExportEvents(5, 120)).thenReturn(List.of(event));
        when(repository.claim(3L, "export-worker-1", 120)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("bad job")).when(apiClient).runExportJob(902L);

        worker.processDueEvents();

        verify(repository).markDeadLetter(3L, "export-worker-1", 3, "reason=illegal_state; exception=IllegalStateException; message=bad job");
        verify(repository, never()).scheduleRetry(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    private static OutboxEvent event(Long id, int retryCount, String payload) {
        return new OutboxEvent(
            id,
            "export_job",
            900L,
            "export.requested",
            payload,
            "pending",
            retryCount,
            LocalDateTime.parse("2026-05-31T09:59:00"),
            null,
            null
        );
    }
}
