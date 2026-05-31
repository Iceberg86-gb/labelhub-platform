package com.labelhub.api.module.outbox.service;

import com.labelhub.api.module.outbox.entity.OutboxEventEntity;
import com.labelhub.api.module.outbox.mapper.OutboxEventMapper;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventServiceTest {

    private final OutboxEventMapper mapper = mock(OutboxEventMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-29T10:15:30Z"), ZoneOffset.UTC);
    private final OutboxEventService service = new OutboxEventService(mapper, clock);

    @Test
    void enqueueSubmissionAiReview_writes_pending_submission_ai_review_event_with_idempotency_seed() {
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.enqueueSubmissionAiReview(submission(), 19L);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mapper).insert(captor.capture());
        OutboxEventEntity event = captor.getValue();
        assertThat(event.getAggregateType()).isEqualTo("submission");
        assertThat(event.getAggregateId()).isEqualTo(300L);
        assertThat(event.getEventType()).isEqualTo("ai_review");
        assertThat(event.getStatus()).isEqualTo("pending");
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getNextRetryAt()).isEqualTo(LocalDateTime.parse("2026-05-29T10:15:30"));
        assertThat(event.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-05-29T10:15:30"));
        assertThat(event.getPayload()).containsAllEntriesOf(Map.of(
            "submissionId", 300L,
            "sessionId", 44L,
            "taskId", 10L,
            "aiReviewRuleId", 19L,
            "idempotencySeed", "submission:300:ai_review"
        ));
    }

    @Test
    void enqueueExportRequested_writes_pending_export_event_without_ai_review_type() {
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.enqueueExportRequested(exportJob());

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(mapper).insert(captor.capture());
        OutboxEventEntity event = captor.getValue();
        assertThat(event.getAggregateType()).isEqualTo("export_job");
        assertThat(event.getAggregateId()).isEqualTo(900L);
        assertThat(event.getEventType()).isEqualTo("export.requested");
        assertThat(event.getStatus()).isEqualTo("pending");
        assertThat(event.getPayload()).containsAllEntriesOf(Map.of(
            "exportJobId", 900L,
            "taskId", 10L,
            "requestedBy", 44L,
            "idempotencySeed", "export_job:900:export.requested"
        ));
    }

    private static SubmissionEntity submission() {
        SubmissionEntity entity = new SubmissionEntity();
        entity.setId(300L);
        entity.setSessionId(44L);
        entity.setTaskId(10L);
        entity.setSchemaVersionId(700L);
        entity.setDatasetItemId(500L);
        entity.setLabelerId(1002L);
        entity.setContentHash("f".repeat(64));
        return entity;
    }

    private static ExportJobEntity exportJob() {
        ExportJobEntity entity = new ExportJobEntity();
        entity.setId(900L);
        entity.setTaskId(10L);
        entity.setRequestedBy(44L);
        entity.setParameters(Map.of("mode", "approved_only"));
        return entity;
    }
}
