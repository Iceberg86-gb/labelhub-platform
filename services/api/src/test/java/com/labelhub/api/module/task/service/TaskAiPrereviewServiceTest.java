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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAiPrereviewServiceTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private final AiPrereviewStatusService prereviewStatusService = mock(AiPrereviewStatusService.class);
    private final OutboxEventService outboxEventService = mock(OutboxEventService.class);
    private TaskAiPrereviewService service;

    @BeforeEach
    void setUp() {
        service = new TaskAiPrereviewService(taskMapper, submissionMapper, prereviewStatusService, outboxEventService);
        when(prereviewStatusService.defaultView(anyLong()))
            .thenAnswer(invocation -> view(invocation.getArgument(0), "pending", null, null, false));
    }

    @Test
    void getSummary_counts_all_task_level_prereview_statuses_and_enqueueable_submissions() {
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 1001L, 19L));
        when(submissionMapper.selectAiPrereviewCandidatesByTaskOrderedById(42L)).thenReturn(submissions(42L));
        when(prereviewStatusService.viewsFor(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(beforeViews());

        TaskAiPrereviewSummaryView summary = service.getSummary(42L, 1001L);

        assertThat(summary.totalCount()).isEqualTo(5L);
        assertThat(summary.pendingCount()).isEqualTo(2L);
        assertThat(summary.processingCount()).isEqualTo(1L);
        assertThat(summary.completedCount()).isEqualTo(1L);
        assertThat(summary.failedCount()).isEqualTo(1L);
        assertThat(summary.enqueueableCount()).isEqualTo(2L);
    }

    @Test
    void enqueueEligible_appends_outbox_events_only_for_not_queued_or_failed_submissions() {
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 1001L, 19L));
        when(submissionMapper.selectAiPrereviewCandidatesByTaskOrderedById(42L)).thenReturn(submissions(42L));
        when(prereviewStatusService.viewsFor(List.of(1L, 2L, 3L, 4L, 5L)))
            .thenReturn(beforeViews())
            .thenReturn(afterViews());

        TaskAiPrereviewEnqueueResultView result = service.enqueueEligible(42L, 1001L);

        assertThat(result.enqueuedCount()).isEqualTo(2L);
        assertThat(result.skippedCount()).isEqualTo(3L);
        assertThat(result.summary().enqueueableCount()).isZero();
        verify(outboxEventService).enqueueSubmissionAiReview(submissionWithId(1L), eq(19L));
        verify(outboxEventService).enqueueSubmissionAiReview(submissionWithId(5L), eq(19L));
        verify(outboxEventService, never()).enqueueSubmissionAiReview(submissionWithId(2L), eq(19L));
        verify(outboxEventService, never()).enqueueSubmissionAiReview(submissionWithId(3L), eq(19L));
        verify(outboxEventService, never()).enqueueSubmissionAiReview(submissionWithId(4L), eq(19L));
    }

    @Test
    void enqueueSubmission_appends_one_outbox_event_for_owner_submission() {
        SubmissionEntity submission = submission(10L, 42L);
        when(submissionMapper.selectById(10L)).thenReturn(submission);
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 1001L, 19L));
        when(prereviewStatusService.viewFor(10L)).thenReturn(view(10L, "pending", null, null, false));
        when(submissionMapper.selectAiPrereviewCandidatesByTaskOrderedById(42L)).thenReturn(List.of(submission));
        when(prereviewStatusService.viewsFor(List.of(10L)))
            .thenReturn(Map.of(10L, view(10L, "pending", "pending", null, false)));

        TaskAiPrereviewEnqueueResultView result = service.enqueueSubmission(10L, 1001L);

        assertThat(result.taskId()).isEqualTo(42L);
        assertThat(result.enqueuedCount()).isEqualTo(1L);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.summary().processingCount()).isZero();
        verify(outboxEventService).enqueueSubmissionAiReview(submissionWithId(10L), eq(19L));
    }

    @Test
    void enqueueSubmission_skips_when_submission_already_has_ai_recommendation() {
        SubmissionEntity submission = submission(10L, 42L);
        when(submissionMapper.selectById(10L)).thenReturn(submission);
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 1001L, 19L));
        when(prereviewStatusService.viewFor(10L)).thenReturn(view(10L, "completed", "processed", "completed", true));
        when(submissionMapper.selectAiPrereviewCandidatesByTaskOrderedById(42L)).thenReturn(List.of(submission));
        when(prereviewStatusService.viewsFor(List.of(10L)))
            .thenReturn(Map.of(10L, view(10L, "completed", "processed", "completed", true)));

        TaskAiPrereviewEnqueueResultView result = service.enqueueSubmission(10L, 1001L);

        assertThat(result.enqueuedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1L);
        verify(outboxEventService, never()).enqueueSubmissionAiReview(submissionWithId(10L), eq(19L));
    }

    @Test
    void enqueueSubmission_rejects_non_owner_without_enqueuing() {
        SubmissionEntity submission = submission(10L, 42L);
        when(submissionMapper.selectById(10L)).thenReturn(submission);
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 2002L, 19L));

        assertThatThrownBy(() -> service.enqueueSubmission(10L, 1001L))
            .isInstanceOf(TaskNotFoundException.class);
        verify(outboxEventService, never()).enqueueSubmissionAiReview(submissionWithId(10L), eq(19L));
    }

    @Test
    void enqueueEligible_rejects_non_owner_without_reading_submissions() {
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 2002L, 19L));

        assertThatThrownBy(() -> service.enqueueEligible(42L, 1001L))
            .isInstanceOf(TaskNotFoundException.class);
        verify(submissionMapper, never()).selectAiPrereviewCandidatesByTaskOrderedById(42L);
    }

    private static TaskEntity task(Long id, Long ownerId, Long aiReviewRuleId) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setOwnerId(ownerId);
        task.setCurrentAiReviewRuleId(aiReviewRuleId);
        return task;
    }

    private static List<SubmissionEntity> submissions(Long taskId) {
        return List.of(
            submission(1L, taskId),
            submission(2L, taskId),
            submission(3L, taskId),
            submission(4L, taskId),
            submission(5L, taskId)
        );
    }

    private static SubmissionEntity submission(Long id, Long taskId) {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(id);
        submission.setTaskId(taskId);
        submission.setSessionId(id + 100L);
        submission.setDatasetItemId(id + 200L);
        submission.setLabelerId(1002L);
        submission.setSchemaVersionId(1L);
        submission.setStatusCode("submitted");
        return submission;
    }

    private static Map<Long, AiPrereviewSignalsView> beforeViews() {
        return Map.of(
            1L, view(1L, "pending", null, null, false),
            2L, view(2L, "pending", "pending", null, false),
            3L, view(3L, "processing", "processing", null, false),
            4L, view(4L, "completed", "processed", "completed", true),
            5L, view(5L, "failed", "dead_letter", null, false)
        );
    }

    private static Map<Long, AiPrereviewSignalsView> afterViews() {
        return Map.of(
            1L, view(1L, "pending", "pending", null, false),
            2L, view(2L, "pending", "pending", null, false),
            3L, view(3L, "processing", "processing", null, false),
            4L, view(4L, "completed", "processed", "completed", true),
            5L, view(5L, "pending", "pending", null, false)
        );
    }

    private static AiPrereviewSignalsView view(
        Long submissionId,
        String status,
        String outboxStatus,
        String aiCallStatus,
        boolean hasRecommendation
    ) {
        return new AiPrereviewSignalsView(
            submissionId,
            status,
            new AiPrereviewSignals(outboxStatus, aiCallStatus, hasRecommendation, null)
        );
    }

    private static SubmissionEntity submissionWithId(Long id) {
        return argThat(submission -> submission != null && Objects.equals(submission.getId(), id));
    }
}
