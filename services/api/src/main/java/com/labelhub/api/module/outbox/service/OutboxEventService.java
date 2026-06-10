package com.labelhub.api.module.outbox.service;

import com.labelhub.api.module.outbox.entity.OutboxEventEntity;
import com.labelhub.api.module.outbox.mapper.OutboxEventMapper;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventService {

    public static final String AGGREGATE_SUBMISSION = "submission";
    public static final String AGGREGATE_EXPORT_JOB = "export_job";
    public static final String EVENT_AI_REVIEW = "ai_review";
    public static final String EVENT_EXPORT_REQUESTED = "export.requested";
    public static final String STATUS_PENDING = "pending";

    private final OutboxEventMapper outboxEventMapper;
    private final Clock clock;

    public OutboxEventService(OutboxEventMapper outboxEventMapper, Clock clock) {
        this.outboxEventMapper = outboxEventMapper;
        this.clock = clock;
    }

    public OutboxEventEntity enqueueSubmissionAiReview(SubmissionEntity submission, Long aiReviewRuleId) {
        LocalDateTime now = LocalDateTime.now(clock);
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateType(AGGREGATE_SUBMISSION);
        event.setAggregateId(submission.getId());
        event.setEventType(EVENT_AI_REVIEW);
        event.setPayload(aiReviewPayload(submission, aiReviewRuleId));
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(now);
        event.setCreatedAt(now);
        return insertIdempotent(event, "insert outbox event");
    }

    public OutboxEventEntity enqueueExportRequested(ExportJobEntity job) {
        LocalDateTime now = LocalDateTime.now(clock);
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateType(AGGREGATE_EXPORT_JOB);
        event.setAggregateId(job.getId());
        event.setEventType(EVENT_EXPORT_REQUESTED);
        event.setPayload(exportRequestedPayload(job));
        event.setStatus(STATUS_PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(now);
        event.setCreatedAt(now);
        return insertIdempotent(event, "insert export outbox event");
    }

    /**
     * Insert an outbox event idempotently. The unique key (aggregate_type, aggregate_id,
     * event_type) makes a second enqueue for the same aggregate a no-op: instead of creating a
     * duplicate event we return the one already queued. This keeps the multiple enqueue paths
     * (submit, task-level prereview, …) from producing duplicate downstream work.
     */
    private OutboxEventEntity insertIdempotent(OutboxEventEntity event, String operation) {
        try {
            requireOneRow(outboxEventMapper.insert(event), operation);
            return event;
        } catch (DuplicateKeyException duplicate) {
            List<OutboxEventEntity> existing = outboxEventMapper.selectByAggregateAndEvent(
                event.getAggregateType(), event.getAggregateId(), event.getEventType());
            if (existing.isEmpty()) {
                throw duplicate;
            }
            return existing.get(0);
        }
    }

    private Map<String, Object> aiReviewPayload(SubmissionEntity submission, Long aiReviewRuleId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", submission.getId());
        payload.put("sessionId", submission.getSessionId());
        payload.put("taskId", submission.getTaskId());
        payload.put("schemaVersionId", submission.getSchemaVersionId());
        payload.put("datasetItemId", submission.getDatasetItemId());
        payload.put("labelerId", submission.getLabelerId());
        payload.put("contentHash", submission.getContentHash());
        payload.put("aiReviewRuleId", aiReviewRuleId);
        payload.put("idempotencySeed", "submission:%d:ai_review".formatted(submission.getId()));
        return payload;
    }

    private Map<String, Object> exportRequestedPayload(ExportJobEntity job) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportJobId", job.getId());
        payload.put("taskId", job.getTaskId());
        payload.put("requestedBy", job.getRequestedBy());
        payload.put("parameters", job.getParameters() == null ? Map.of() : job.getParameters());
        payload.put("idempotencySeed", "export_job:%d:export.requested".formatted(job.getId()));
        return payload;
    }

    private void requireOneRow(int rows, String operation) {
        if (rows != 1) {
            throw new IllegalStateException(operation + " affected " + rows + " rows");
        }
    }
}
