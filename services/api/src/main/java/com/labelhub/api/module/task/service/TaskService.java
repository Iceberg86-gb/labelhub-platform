package com.labelhub.api.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.TaskStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskTransitionMapper taskTransitionMapper;
    private final AuditLogMapper auditLogMapper;
    private final DatasetMapper datasetMapper;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Canonicalizer canonicalizer;

    public TaskService(
        TaskMapper taskMapper,
        TaskTransitionMapper taskTransitionMapper,
        AuditLogMapper auditLogMapper,
        DatasetMapper datasetMapper,
        Clock clock,
        ObjectMapper objectMapper,
        Canonicalizer canonicalizer
    ) {
        this.taskMapper = taskMapper;
        this.taskTransitionMapper = taskTransitionMapper;
        this.auditLogMapper = auditLogMapper;
        this.datasetMapper = datasetMapper;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.canonicalizer = canonicalizer;
    }

    @Transactional
    public TaskEntity create(TaskCreateCommand command, Long ownerId) {
        TaskEntity task = new TaskEntity();
        task.setTitle(command.title());
        task.setDescription(command.description());
        task.setInstructionRichText(command.instructionRichText());
        task.setTags(command.tags());
        task.setRewardRule(command.rewardRule());
        task.setDeadlineAt(command.deadlineAt());
        task.setQuotaTotal(command.quotaTotal());
        task.setQuotaClaimed(0);
        task.setStatus(TaskStatus.DRAFT);
        task.setOwnerId(ownerId);
        requireOneRow(taskMapper.insert(task), "insert task");
        return task;
    }

    public PagedResult<TaskEntity> list(Long ownerId, TaskStatus status, long page, long size) {
        LambdaQueryWrapper<TaskEntity> query = new LambdaQueryWrapper<TaskEntity>()
            .eq(TaskEntity::getOwnerId, ownerId)
            .eq(status != null, TaskEntity::getStatusCode, status == null ? null : status.getValue())
            .orderByDesc(TaskEntity::getCreatedAt);
        Page<TaskEntity> result = taskMapper.selectPage(Page.of(page, size), query);
        return new PagedResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public TaskEntity getById(Long ownerId, Long taskId) {
        TaskEntity task = requireTask(taskId);
        if (!Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskAccessDeniedException(taskId, ownerId);
        }
        return task;
    }

    public List<TaskTransitionEntity> listTransitions(Long taskId, Long ownerId) {
        TaskEntity task = requireTask(taskId);
        if (!Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskAccessDeniedException(taskId, ownerId);
        }
        return taskTransitionMapper.selectByTaskId(taskId);
    }

    @Transactional
    public TaskEntity transition(Long taskId, TaskStatus targetStatus, String reason, Long actorId) {
        TaskEntity task = requireTask(taskId);
        if (!Objects.equals(task.getOwnerId(), actorId)) {
            throw new TaskAccessDeniedException(taskId, actorId);
        }
        TaskStatus fromStatus = task.getStatus();
        if (!TaskStateTransitions.isAllowed(fromStatus, targetStatus)) {
            throw new IllegalStateTransitionException(fromStatus, targetStatus, reason);
        }
        if (targetStatus == TaskStatus.PUBLISHED) {
            canPublish(task);
        }

        requireOneRow(taskTransitionMapper.insert(transitionRecord(task, fromStatus, targetStatus, reason, actorId)), "insert task transition");
        requireOneRow(auditLogMapper.insert(auditRecord(taskId, fromStatus, targetStatus, reason, actorId)), "insert audit log");

        task.setStatus(targetStatus);
        task.setUpdatedAt(LocalDateTime.now(clock));
        requireOneRow(taskMapper.updateById(task), "update task status");
        return task;
    }

    @Transactional
    public TaskEntity updateCurrentDataset(Long taskId, Long datasetId, Long ownerId) {
        TaskEntity task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(taskId);
        }
        if (task.getStatus() == TaskStatus.PUBLISHED) {
            throw new TaskPublishedLockException(taskId);
        }

        DatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || !Objects.equals(dataset.getTaskId(), taskId)) {
            throw new InvalidDatasetForTaskException(datasetId, taskId);
        }

        task.setCurrentDatasetId(datasetId);
        task.setUpdatedAt(LocalDateTime.now(clock));
        requireOneRow(taskMapper.updateById(task), "update task current dataset");
        return task;
    }

    private TaskEntity requireTask(Long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    private void canPublish(TaskEntity task) {
        // State legality is guarded by TaskStateTransitions; publish guards only check business prerequisites.
        if (task.getQuotaTotal() == null || task.getQuotaTotal() <= 0) {
            throw new TaskPublishGuardException("quota_total");
        }
        if (task.getDeadlineAt() == null || !task.getDeadlineAt().isAfter(LocalDateTime.now(clock))) {
            throw new TaskPublishGuardException("deadline_at");
        }
        if (task.getCurrentSchemaVersionId() == null) {
            throw new TaskPublishGuardException("current_schema_version_id");
        }
        if (task.getCurrentDatasetId() == null) {
            throw new TaskPublishGuardException("current_dataset_id");
        }
        // TODO M4: add adjudicationRuleId not null check.
    }

    private TaskTransitionEntity transitionRecord(
        TaskEntity task,
        TaskStatus fromStatus,
        TaskStatus targetStatus,
        String reason,
        Long actorId
    ) {
        TaskTransitionEntity transition = new TaskTransitionEntity();
        transition.setTaskId(task.getId());
        transition.setFromStatus(fromStatus);
        transition.setToStatus(targetStatus);
        transition.setActorId(actorId);
        transition.setReason(reason);
        return transition;
    }

    private AuditLogEntity auditRecord(Long taskId, TaskStatus fromStatus, TaskStatus targetStatus, String reason, Long actorId) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.setActorType("user");
        audit.setActorId(actorId);
        audit.setAction("task.transition");
        audit.setResourceType("task");
        audit.setResourceId(taskId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromStatus.getValue());
        payload.put("to", targetStatus.getValue());
        payload.put("reason", reason == null ? "" : reason);
        audit.setPayload(writeJson(payload));
        audit.setPayloadHash(canonicalizer.sha256Hex(canonicalizer.canonicalJson(payload)));
        return audit;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to write audit payload JSON", e);
        }
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
