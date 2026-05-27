package com.labelhub.api.module.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.TaskStatus;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.entity.AuditLogEntity;
import com.labelhub.api.module.admin.mapper.AuditLogMapper;
import com.labelhub.api.module.dataset.entity.DatasetEntity;
import com.labelhub.api.module.dataset.exception.InvalidDatasetForTaskException;
import com.labelhub.api.module.dataset.exception.TaskPublishedLockException;
import com.labelhub.api.module.dataset.mapper.DatasetMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.entity.TaskTransitionEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.mapper.TaskTransitionMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final TaskTransitionMapper taskTransitionMapper = mock(TaskTransitionMapper.class);
    private final AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Canonicalizer canonicalizer = new Canonicalizer(objectMapper);
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-23T12:00:00Z"), ZoneOffset.UTC);
        taskService = new TaskService(taskMapper, taskTransitionMapper, auditLogMapper, datasetMapper, clock, objectMapper, canonicalizer);
    }

    @Test
    void create_returns_task_in_draft_status() {
        doAnswer(invocation -> {
            TaskEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            return 1;
        }).when(taskMapper).insert(any(TaskEntity.class));

        TaskEntity created = taskService.create(TaskCreateCommand.builder()
            .title("Image quality check")
            .description("Review prompt")
            .quotaTotal(10)
            .deadlineAt(LocalDateTime.parse("2026-05-24T00:00:00"))
            .build(), 1001L);

        assertThat(created.getId()).isEqualTo(42L);
        assertThat(created.getStatus()).isEqualTo(TaskStatus.DRAFT);
        assertThat(created.getOwnerId()).isEqualTo(1001L);
        assertThat(created.getQuotaClaimed()).isZero();
    }

    @Test
    void list_filters_by_owner_and_status_with_page_metadata() {
        Page<TaskEntity> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(task(7L, TaskStatus.PUBLISHED)));
        when(taskMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PagedResult<TaskEntity> result = taskService.list(1001L, TaskStatus.PUBLISHED, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting(TaskEntity::getId).containsExactly(7L);
        verify(taskMapper).selectPage(any(Page.class), any());
    }

    @Test
    void get_by_id_rejects_task_owned_by_another_owner() {
        when(taskMapper.selectById(9L)).thenReturn(task(9L, TaskStatus.DRAFT, 2002L));

        assertThatThrownBy(() -> taskService.getById(1001L, 9L))
            .isInstanceOf(TaskAccessDeniedException.class);
    }

    @Test
    void list_transitions_returns_records_in_chronological_order() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, TaskStatus.PUBLISHED));
        when(taskTransitionMapper.selectByTaskId(1L)).thenReturn(List.of(
            transition(10L, TaskStatus.DRAFT, TaskStatus.PUBLISHED),
            transition(11L, TaskStatus.PUBLISHED, TaskStatus.PAUSED)
        ));

        List<TaskTransitionEntity> transitions = taskService.listTransitions(1L, 1001L);

        assertThat(transitions).extracting(TaskTransitionEntity::getId).containsExactly(10L, 11L);
    }

    @Test
    void list_transitions_rejects_when_owner_mismatch() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, TaskStatus.PUBLISHED, 2002L));

        assertThatThrownBy(() -> taskService.listTransitions(1L, 1001L))
            .isInstanceOf(TaskAccessDeniedException.class);
        verify(taskTransitionMapper, never()).selectByTaskId(any());
    }

    @Test
    void list_transitions_returns_empty_for_brand_new_task() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, TaskStatus.DRAFT));
        when(taskTransitionMapper.selectByTaskId(1L)).thenReturn(List.of());

        List<TaskTransitionEntity> transitions = taskService.listTransitions(1L, 1001L);

        assertThat(transitions).isEmpty();
    }

    @Test
    void transition_succeeds_when_legal() {
        when(taskMapper.selectById(1L)).thenReturn(publishableTask(1L, TaskStatus.DRAFT));
        when(taskTransitionMapper.insert(any())).thenReturn(1);
        when(auditLogMapper.insert(any())).thenReturn(1);
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

        TaskEntity transitioned = taskService.transition(1L, TaskStatus.PUBLISHED, "ready", 1001L);

        assertThat(transitioned.getStatus()).isEqualTo(TaskStatus.PUBLISHED);
    }

    @Test
    void transition_from_paused_to_published_succeeds_when_publish_guard_passes() {
        when(taskMapper.selectById(1L)).thenReturn(publishableTask(1L, TaskStatus.PAUSED));
        when(taskTransitionMapper.insert(any())).thenReturn(1);
        when(auditLogMapper.insert(any())).thenReturn(1);
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

        TaskEntity transitioned = taskService.transition(1L, TaskStatus.PUBLISHED, "resume", 1001L);

        assertThat(transitioned.getStatus()).isEqualTo(TaskStatus.PUBLISHED);
    }

    @Test
    void transition_from_paused_to_published_rejects_when_quota_zero() {
        TaskEntity task = publishableTask(1L, TaskStatus.PAUSED);
        task.setQuotaTotal(0);
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PUBLISHED, "resume", 1001L))
            .isInstanceOf(TaskPublishGuardException.class)
            .hasMessageContaining("quota_total")
            .isInstanceOfSatisfying(TaskPublishGuardException.class, exception ->
                assertThat(exception.getGuardName()).isEqualTo("quota_total"));
    }

    @Test
    void transition_from_paused_to_published_rejects_when_deadline_past() {
        TaskEntity task = publishableTask(1L, TaskStatus.PAUSED);
        task.setDeadlineAt(LocalDateTime.parse("2026-05-22T23:59:00"));
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PUBLISHED, "resume", 1001L))
            .isInstanceOf(TaskPublishGuardException.class)
            .hasMessageContaining("deadline_at")
            .isInstanceOfSatisfying(TaskPublishGuardException.class, exception ->
                assertThat(exception.getGuardName()).isEqualTo("deadline_at"));
    }

    @Test
    void transition_throws_when_illegal() {
        when(taskMapper.selectById(1L)).thenReturn(publishableTask(1L, TaskStatus.DRAFT));

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PAUSED, "skip publish", 1001L))
            .isInstanceOf(IllegalStateTransitionException.class)
            .hasMessageContaining("draft")
            .hasMessageContaining("paused");
    }

    @Test
    void transition_writes_transition_and_audit_before_task_update() {
        when(taskMapper.selectById(1L)).thenReturn(publishableTask(1L, TaskStatus.DRAFT));
        when(taskTransitionMapper.insert(any())).thenReturn(1);
        when(auditLogMapper.insert(any())).thenReturn(1);
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

        taskService.transition(1L, TaskStatus.PUBLISHED, "ready", 1001L);

        InOrder inOrder = inOrder(taskTransitionMapper, auditLogMapper, taskMapper);
        inOrder.verify(taskTransitionMapper).insert(any());
        inOrder.verify(auditLogMapper).insert(any());
        inOrder.verify(taskMapper).updateById(any(TaskEntity.class));
    }

    @Test
    void transition_writes_valid_json_payload_and_sha256_payload_hash() throws Exception {
        when(taskMapper.selectById(1L)).thenReturn(publishableTask(1L, TaskStatus.DRAFT));
        when(taskTransitionMapper.insert(any())).thenReturn(1);
        when(auditLogMapper.insert(any())).thenReturn(1);
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

        taskService.transition(1L, TaskStatus.PUBLISHED, "ready\nwith \"quote\"", 1001L);

        ArgumentCaptor<AuditLogEntity> auditCaptor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        AuditLogEntity audit = auditCaptor.getValue();
        Map<String, Object> payload = objectMapper.readValue(audit.getPayload(), new TypeReference<>() {});
        assertThat(audit.getAction()).isEqualTo(AuditActions.TASK_TRANSITION);
        assertThat(audit.getActorType()).isEqualTo("user");
        assertThat(audit.getResourceType()).isEqualTo("task");
        assertThat(payload).containsEntry("from", "draft");
        assertThat(payload).containsEntry("to", "published");
        assertThat(payload).containsEntry("reason", "ready\nwith \"quote\"");
        assertThat(audit.getPayloadHash()).hasSize(64);
        assertThat(audit.getPayloadHash()).isEqualTo(canonicalizer.sha256Hex(canonicalizer.canonicalJson(payload)));
    }

    @Test
    void transition_does_not_update_task_when_audit_insert_fails() {
        when(taskMapper.selectById(1L)).thenReturn(publishableTask(1L, TaskStatus.DRAFT));
        when(taskTransitionMapper.insert(any())).thenReturn(1);
        when(auditLogMapper.insert(any())).thenThrow(new RuntimeException("audit failed"));

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PUBLISHED, "ready", 1001L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("audit failed");
        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }

    @Test
    void publish_guard_rejects_when_quota_zero() {
        TaskEntity task = publishableTask(1L, TaskStatus.DRAFT);
        task.setQuotaTotal(0);
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PUBLISHED, "ready", 1001L))
            .isInstanceOf(TaskPublishGuardException.class)
            .hasMessageContaining("quota_total")
            .isInstanceOfSatisfying(TaskPublishGuardException.class, exception ->
                assertThat(exception.getGuardName()).isEqualTo("quota_total"));
    }

    @Test
    void publish_guard_rejects_when_deadline_past() {
        TaskEntity task = publishableTask(1L, TaskStatus.DRAFT);
        task.setDeadlineAt(LocalDateTime.parse("2026-05-22T23:59:00"));
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PUBLISHED, "ready", 1001L))
            .isInstanceOf(TaskPublishGuardException.class)
            .hasMessageContaining("deadline_at")
            .isInstanceOfSatisfying(TaskPublishGuardException.class, exception ->
                assertThat(exception.getGuardName()).isEqualTo("deadline_at"));
    }

    @Test
    void canPublish_rejects_task_without_current_schema_version_id_with_guard_name() {
        TaskEntity task = publishableTask(1L, TaskStatus.DRAFT);
        task.setCurrentSchemaVersionId(null);
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PUBLISHED, "ready", 1001L))
            .isInstanceOf(TaskPublishGuardException.class)
            .hasMessageContaining("current_schema_version_id")
            .isInstanceOfSatisfying(TaskPublishGuardException.class, exception ->
                assertThat(exception.getGuardName()).isEqualTo("current_schema_version_id"));
    }

    @Test
    void canPublish_rejects_task_without_current_dataset_id_with_guard_name() {
        TaskEntity task = publishableTask(1L, TaskStatus.DRAFT);
        task.setCurrentDatasetId(null);
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.transition(1L, TaskStatus.PUBLISHED, "ready", 1001L))
            .isInstanceOf(TaskPublishGuardException.class)
            .hasMessageContaining("current_dataset_id")
            .isInstanceOfSatisfying(TaskPublishGuardException.class, exception ->
                assertThat(exception.getGuardName()).isEqualTo("current_dataset_id"));
    }

    @Test
    void updateCurrentDataset_changes_pointer_when_task_unpublished() {
        TaskEntity task = task(1L, TaskStatus.DRAFT);
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task);
        when(datasetMapper.selectById(77L)).thenReturn(dataset(77L, 1L));
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

        TaskEntity updated = taskService.updateCurrentDataset(1L, 77L, 1001L);

        assertThat(updated.getCurrentDatasetId()).isEqualTo(77L);
        assertThat(updated.getUpdatedAt()).isEqualTo(LocalDateTime.parse("2026-05-23T12:00:00"));
        verify(taskMapper).updateById(any(TaskEntity.class));
    }

    @Test
    void updateCurrentDataset_rejects_published_task() {
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task(1L, TaskStatus.PUBLISHED));

        assertThatThrownBy(() -> taskService.updateCurrentDataset(1L, 77L, 1001L))
            .isInstanceOf(TaskPublishedLockException.class);

        verify(datasetMapper, never()).selectById(any());
        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }

    @Test
    void updateCurrentDataset_rejects_dataset_not_in_task() {
        when(taskMapper.selectByIdForUpdate(1L)).thenReturn(task(1L, TaskStatus.DRAFT));
        when(datasetMapper.selectById(77L)).thenReturn(dataset(77L, 2L));

        assertThatThrownBy(() -> taskService.updateCurrentDataset(1L, 77L, 1001L))
            .isInstanceOf(InvalidDatasetForTaskException.class);

        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }

    private TaskEntity publishableTask(Long id, TaskStatus status) {
        TaskEntity task = task(id, status);
        task.setQuotaTotal(10);
        task.setDeadlineAt(LocalDateTime.parse("2026-05-24T00:00:00"));
        task.setCurrentSchemaVersionId(88L);
        task.setCurrentDatasetId(99L);
        return task;
    }

    private TaskEntity task(Long id, TaskStatus status) {
        return task(id, status, 1001L);
    }

    private TaskEntity task(Long id, TaskStatus status, Long ownerId) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setTitle("Task " + id);
        task.setOwnerId(ownerId);
        task.setStatus(status);
        task.setQuotaTotal(10);
        task.setQuotaClaimed(0);
        return task;
    }

    private TaskTransitionEntity transition(Long id, TaskStatus fromStatus, TaskStatus toStatus) {
        TaskTransitionEntity transition = new TaskTransitionEntity();
        transition.setId(id);
        transition.setTaskId(1L);
        transition.setFromStatus(fromStatus);
        transition.setToStatus(toStatus);
        transition.setActorId(1001L);
        transition.setReason("state change");
        transition.setCreatedAt(LocalDateTime.parse("2026-05-23T12:00:00"));
        return transition;
    }

    private DatasetEntity dataset(Long id, Long taskId) {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(id);
        dataset.setTaskId(taskId);
        return dataset;
    }
}
