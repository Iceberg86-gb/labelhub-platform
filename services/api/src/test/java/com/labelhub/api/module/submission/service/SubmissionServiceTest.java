package com.labelhub.api.module.submission.service;

import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionServiceTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionService(taskMapper, submissionMapper);
    }

    @Test
    void listByTaskForOwner_returns_paged_submissions_for_owned_task() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(submissionMapper.selectPageByTaskId(10L, 20L, 20L)).thenReturn(List.of(
            submission(3L, 10L),
            submission(2L, 10L)
        ));
        when(submissionMapper.selectCountByTaskId(10L)).thenReturn(5L);

        PagedResult<SubmissionEntity> result = submissionService.listByTaskForOwner(10L, 1001L, 2, 20);

        assertThat(result.items()).extracting(SubmissionEntity::getId).containsExactly(3L, 2L);
        assertThat(result.total()).isEqualTo(5L);
        assertThat(result.page()).isEqualTo(2L);
        assertThat(result.size()).isEqualTo(20L);
    }

    @Test
    void listByTaskForOwner_throws_not_found_when_task_not_owned() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 2002L));

        assertThatThrownBy(() -> submissionService.listByTaskForOwner(10L, 1001L, 1, 20))
            .isInstanceOf(TaskNotFoundException.class);

        verify(submissionMapper, never()).selectPageByTaskId(10L, 0L, 20L);
        verify(submissionMapper, never()).selectCountByTaskId(10L);
    }

    private static TaskEntity task(Long id, Long ownerId) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setOwnerId(ownerId);
        return task;
    }

    private static SubmissionEntity submission(Long id, Long taskId) {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(id);
        submission.setTaskId(taskId);
        submission.setSessionId(id + 100);
        submission.setDatasetItemId(id + 200);
        submission.setLabelerId(1002L);
        submission.setSchemaVersionId(id + 300);
        submission.setStatusCode("under_ai_review");
        submission.setCreatedAt(LocalDateTime.parse("2026-05-25T09:00:00"));
        return submission;
    }
}
