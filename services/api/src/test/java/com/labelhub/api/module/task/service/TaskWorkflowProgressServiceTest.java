package com.labelhub.api.module.task.service;

import com.labelhub.api.generated.model.TaskStatus;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.mapper.TaskWorkflowProgressMapper;
import com.labelhub.api.module.task.mapper.TaskWorkflowProgressRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskWorkflowProgressServiceTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final TaskWorkflowProgressMapper progressMapper = mock(TaskWorkflowProgressMapper.class);
    private TaskWorkflowProgressService service;

    @BeforeEach
    void setUp() {
        service = new TaskWorkflowProgressService(taskMapper, progressMapper);
    }

    @Test
    void getProgress_returns_owner_visible_workflow_counts() {
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 1001L, 80, 12));
        when(progressMapper.selectByTaskId(42L)).thenReturn(row(42L));

        TaskWorkflowProgressView progress = service.getProgress(42L, 1001L);

        assertThat(progress.taskId()).isEqualTo(42L);
        assertThat(progress.quotaTotal()).isEqualTo(80);
        assertThat(progress.quotaClaimed()).isEqualTo(12);
        assertThat(progress.unclaimedCount()).isEqualTo(68L);
        assertThat(progress.labelingCount()).isEqualTo(1L);
        assertThat(progress.submittedCount()).isEqualTo(10L);
        assertThat(progress.aiPrereviewCompletedCount()).isEqualTo(8L);
        assertThat(progress.pendingReviewCount()).isEqualTo(3L);
        assertThat(progress.pendingSeniorReviewCount()).isEqualTo(4L);
        assertThat(progress.approvedCount()).isEqualTo(2L);
        assertThat(progress.rejectedCount()).isEqualTo(1L);
    }

    @Test
    void getProgress_rejects_non_owner_without_reading_progress() {
        when(taskMapper.selectById(42L)).thenReturn(task(42L, 2002L, 10, 1));

        assertThatThrownBy(() -> service.getProgress(42L, 1001L))
            .isInstanceOf(TaskNotFoundException.class);
        verify(progressMapper, never()).selectByTaskId(42L);
    }

    private static TaskEntity task(Long id, Long ownerId, Integer quotaTotal, Integer quotaClaimed) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setOwnerId(ownerId);
        task.setStatus(TaskStatus.PUBLISHED);
        task.setQuotaTotal(quotaTotal);
        task.setQuotaClaimed(quotaClaimed);
        return task;
    }

    private static TaskWorkflowProgressRow row(Long taskId) {
        TaskWorkflowProgressRow row = new TaskWorkflowProgressRow();
        row.setTaskId(taskId);
        row.setQuotaTotal(80);
        row.setQuotaClaimed(12);
        row.setUnclaimedCount(68L);
        row.setLabelingCount(1L);
        row.setSubmittedCount(10L);
        row.setAiPrereviewCompletedCount(8L);
        row.setPendingReviewCount(3L);
        row.setPendingSeniorReviewCount(4L);
        row.setApprovedCount(2L);
        row.setRejectedCount(1L);
        return row;
    }
}
