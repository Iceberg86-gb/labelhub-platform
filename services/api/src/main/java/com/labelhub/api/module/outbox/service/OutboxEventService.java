package com.labelhub.api.module.outbox.service;

import com.labelhub.api.module.outbox.entity.OutboxEventEntity;
import com.labelhub.api.module.outbox.mapper.OutboxEventMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventService {

    public static final String AGGREGATE_SUBMISSION = "submission";
    public static final String EVENT_AI_REVIEW = "ai_review";
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
        requireOneRow(outboxEventMapper.insert(event), "insert outbox event");
        return event;
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

    private void requireOneRow(int rows, String operation) {
        if (rows != 1) {
            throw new IllegalStateException(operation + " affected " + rows + " rows");
        }
    }
}
