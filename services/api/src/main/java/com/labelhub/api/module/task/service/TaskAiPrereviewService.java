package com.labelhub.api.module.task.service;

import com.labelhub.api.module.ai.prereview.AiPrereviewSignals;
import com.labelhub.api.module.ai.prereview.AiPrereviewSignalsView;
import com.labelhub.api.module.ai.prereview.AiPrereviewStatusService;
import com.labelhub.api.module.outbox.service.OutboxEventService;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskAiPrereviewService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";

    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final AiPrereviewStatusService prereviewStatusService;
    private final OutboxEventService outboxEventService;

    public TaskAiPrereviewService(
        TaskMapper taskMapper,
        SubmissionMapper submissionMapper,
        AiPrereviewStatusService prereviewStatusService,
        OutboxEventService outboxEventService
    ) {
        this.taskMapper = taskMapper;
        this.submissionMapper = submissionMapper;
        this.prereviewStatusService = prereviewStatusService;
        this.outboxEventService = outboxEventService;
    }

    public TaskAiPrereviewSummaryView getSummary(Long taskId, Long ownerId) {
        TaskEntity task = ownerTask(taskId, ownerId);
        return snapshot(task).summary();
    }

    @Transactional
    public TaskAiPrereviewEnqueueResultView enqueueEligible(Long taskId, Long ownerId) {
        TaskEntity task = ownerTask(taskId, ownerId);
        Snapshot before = snapshot(task);
        long enqueued = 0;
        for (SubmissionEntity submission : before.submissions()) {
            AiPrereviewSignalsView view = before.prereviewBySubmissionId().getOrDefault(
                submission.getId(),
                prereviewStatusService.defaultView(submission.getId())
            );
            if (!isEnqueueable(view)) {
                continue;
            }
            outboxEventService.enqueueSubmissionAiReview(submission, task.getCurrentAiReviewRuleId());
            enqueued++;
        }
        TaskAiPrereviewSummaryView after = snapshot(task).summary();
        return new TaskAiPrereviewEnqueueResultView(
            task.getId(),
            enqueued,
            before.submissions().size() - enqueued,
            after
        );
    }

    @Transactional
    public TaskAiPrereviewEnqueueResultView enqueueSubmission(Long submissionId, Long ownerId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new TaskNotFoundException(submissionId);
        }
        TaskEntity task = ownerTask(submission.getTaskId(), ownerId);
        AiPrereviewSignalsView view = prereviewStatusService.viewFor(submissionId);
        long enqueued = 0;
        long skipped = 1;
        if (isEnqueueable(view == null ? prereviewStatusService.defaultView(submissionId) : view)) {
            outboxEventService.enqueueSubmissionAiReview(submission, task.getCurrentAiReviewRuleId());
            enqueued = 1;
            skipped = 0;
        }
        return new TaskAiPrereviewEnqueueResultView(
            task.getId(),
            enqueued,
            skipped,
            snapshot(task).summary()
        );
    }

    private TaskEntity ownerTask(Long taskId, Long ownerId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    private Snapshot snapshot(TaskEntity task) {
        List<SubmissionEntity> submissions = submissionMapper.selectAiPrereviewCandidatesByTaskOrderedById(task.getId());
        Map<Long, AiPrereviewSignalsView> prereviewBySubmissionId =
            prereviewStatusService.viewsFor(submissions.stream().map(SubmissionEntity::getId).toList());
        long pending = 0;
        long processing = 0;
        long completed = 0;
        long failed = 0;
        long enqueueable = 0;
        for (SubmissionEntity submission : submissions) {
            AiPrereviewSignalsView view = prereviewBySubmissionId.getOrDefault(
                submission.getId(),
                prereviewStatusService.defaultView(submission.getId())
            );
            String status = view.status() == null ? STATUS_PENDING : view.status();
            if (STATUS_COMPLETED.equals(status)) {
                completed++;
            } else if (STATUS_PROCESSING.equals(status)) {
                processing++;
            } else if (STATUS_FAILED.equals(status)) {
                failed++;
            } else {
                pending++;
            }
            if (isEnqueueable(view)) {
                enqueueable++;
            }
        }
        return new Snapshot(
            submissions,
            prereviewBySubmissionId,
            new TaskAiPrereviewSummaryView(
                task.getId(),
                (long) submissions.size(),
                pending,
                processing,
                completed,
                failed,
                enqueueable
            )
        );
    }

    private boolean isEnqueueable(AiPrereviewSignalsView view) {
        AiPrereviewSignals signals = view.signals();
        if (signals.hasAiOverallRecommendation() || STATUS_COMPLETED.equals(signals.aiCallStatus())) {
            return false;
        }
        return !STATUS_PENDING.equals(signals.outboxStatus())
            && !STATUS_PROCESSING.equals(signals.outboxStatus());
    }

    private record Snapshot(
        List<SubmissionEntity> submissions,
        Map<Long, AiPrereviewSignalsView> prereviewBySubmissionId,
        TaskAiPrereviewSummaryView summary
    ) {
    }
}
