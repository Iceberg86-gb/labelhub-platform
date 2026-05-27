package com.labelhub.api.module.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.TaskStatus;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.admin.audit.AuditLogServiceImpl;
import com.labelhub.api.module.admin.mapper.AuditLogMapper;
import com.labelhub.api.module.dataset.entity.DatasetEntity;
import com.labelhub.api.module.dataset.exception.InvalidDatasetForTaskException;
import com.labelhub.api.module.dataset.exception.TaskPublishedLockException;
import com.labelhub.api.module.dataset.mapper.DatasetMapper;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.mapper.ExportJobMapper;
import com.labelhub.api.module.export.mapper.ExportSnapshotMapper;
import com.labelhub.api.module.export.storage.ObjectStorageWriter;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.entity.TaskTransitionEntity;
import com.labelhub.api.module.task.mapper.TaskDeletionMapper;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.mapper.TaskTransitionMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskMapper taskMapper;
    private final TaskTransitionMapper taskTransitionMapper;
    private final TaskDeletionMapper taskDeletionMapper;
    private final AuditLogService auditLogService;
    private final DatasetMapper datasetMapper;
    private final ExportSnapshotMapper exportSnapshotMapper;
    private final ExportJobMapper exportJobMapper;
    private final ObjectStorageWriter objectStorageWriter;
    private final Clock clock;

    @Autowired
    public TaskService(
        TaskMapper taskMapper,
        TaskTransitionMapper taskTransitionMapper,
        TaskDeletionMapper taskDeletionMapper,
        AuditLogService auditLogService,
        DatasetMapper datasetMapper,
        ExportSnapshotMapper exportSnapshotMapper,
        ExportJobMapper exportJobMapper,
        ObjectStorageWriter objectStorageWriter,
        Clock clock
    ) {
        this.taskMapper = taskMapper;
        this.taskTransitionMapper = taskTransitionMapper;
        this.taskDeletionMapper = taskDeletionMapper;
        this.auditLogService = auditLogService;
        this.datasetMapper = datasetMapper;
        this.exportSnapshotMapper = exportSnapshotMapper;
        this.exportJobMapper = exportJobMapper;
        this.objectStorageWriter = objectStorageWriter;
        this.clock = clock;
    }

    public TaskService(
        TaskMapper taskMapper,
        TaskTransitionMapper taskTransitionMapper,
        TaskDeletionMapper taskDeletionMapper,
        AuditLogMapper auditLogMapper,
        DatasetMapper datasetMapper,
        ExportSnapshotMapper exportSnapshotMapper,
        ExportJobMapper exportJobMapper,
        ObjectStorageWriter objectStorageWriter,
        Clock clock,
        ObjectMapper objectMapper,
        Canonicalizer canonicalizer
    ) {
        this(taskMapper, taskTransitionMapper, taskDeletionMapper, AuditLogService.noop(), datasetMapper,
            exportSnapshotMapper, exportJobMapper, objectStorageWriter, clock);
    }

    public TaskService(
        TaskMapper taskMapper,
        TaskTransitionMapper taskTransitionMapper,
        AuditLogMapper auditLogMapper,
        DatasetMapper datasetMapper,
        Clock clock,
        ObjectMapper objectMapper,
        Canonicalizer canonicalizer
    ) {
        this(taskMapper, taskTransitionMapper, null, new AuditLogServiceImpl(auditLogMapper, canonicalizer, objectMapper),
            datasetMapper, null, null, null, clock);
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

    @Transactional
    public void deleteTask(Long taskId, Long ownerId) {
        TaskEntity task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        if (!Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskAccessDeniedException(taskId, ownerId);
        }

        Set<String> objectKeys = collectExportObjectKeys(taskId);
        auditLogService.record(
            AuditEventBuilder.forAction(AuditActions.TASK_DELETE)
                .actorUser(ownerId)
                .resource("task", taskId)
                .payload("taskId", taskId)
                .payload("ownerId", task.getOwnerId())
                .payload("status", task.getStatus().getValue())
                .payload("title", task.getTitle())
        );

        // Step 1: blocks fk_tasks_current_dataset before datasets delete.
        taskDeletionMapper.clearTaskCurrentDataset(taskId);
        // Step 2: blocks fk_tasks_schema_version before schema_versions delete.
        taskDeletionMapper.clearTaskCurrentSchemaVersion(taskId);
        // Step 3: blocks fk_label_schemas_current_version before schema_versions delete.
        taskDeletionMapper.clearLabelSchemaCurrentVersions(taskId);
        // Step 4: blocks fk_submissions_superseded_by self-reference before submissions delete.
        taskDeletionMapper.clearSubmissionSupersededBy(taskId);
        // Step 5: blocks fk_ai_calls_in_field_submission/call before submissions or ai_calls delete.
        taskDeletionMapper.deleteAiCallsInField(taskId);
        // Step 6: blocks fk_current_verdicts_* before submissions, tasks, rules, or ledger delete.
        taskDeletionMapper.deleteCurrentVerdicts(taskId);
        // Step 7: blocks fk_review_actions_submission/task before submissions or tasks delete.
        taskDeletionMapper.deleteReviewActions(taskId);
        // Step 8: blocks fk_export_snapshots_job/task/rule before export_jobs, tasks, or rules delete.
        taskDeletionMapper.deleteExportSnapshots(taskId);
        // Step 9: blocks fk_quality_ledger_submission/task/ai_call before submissions, tasks, or ai_calls delete.
        taskDeletionMapper.deleteQualityLedgerEntries(taskId);
        // Step 10: blocks fk_ai_calls_submission before submissions delete.
        taskDeletionMapper.deleteAiCalls(taskId);
        // Step 11: blocks fk_drafts_session before sessions delete.
        taskDeletionMapper.deleteDrafts(taskId);
        // Step 12: blocks fk_submissions_session/task/item/schema_version before parent deletes.
        taskDeletionMapper.deleteSubmissions(taskId);
        // Step 13: blocks fk_sessions_task/dataset_item/schema_version before parent deletes.
        taskDeletionMapper.deleteSessions(taskId);
        // Step 14: blocks fk_dataset_items_dataset/task before datasets or tasks delete.
        taskDeletionMapper.deleteDatasetItems(taskId);
        // Step 15: blocks fk_export_jobs_task before tasks delete.
        taskDeletionMapper.deleteExportJobs(taskId);
        // Step 16: blocks fk_adjudication_rules_task before tasks delete.
        taskDeletionMapper.deleteAdjudicationRules(taskId);
        // Step 17: blocks fk_datasets_task before tasks delete.
        taskDeletionMapper.deleteDatasets(taskId);
        // Step 18: blocks fk_schema_versions_schema before label_schemas delete.
        taskDeletionMapper.deleteSchemaVersions(taskId);
        // Step 19: blocks fk_label_schemas_task before tasks delete.
        taskDeletionMapper.deleteLabelSchemas(taskId);
        // Step 20: blocks fk_task_transitions_task before tasks delete.
        taskDeletionMapper.deleteTaskTransitions(taskId);
        // Step 21: all dependents cleared.
        requireOneRow(taskDeletionMapper.deleteTask(taskId), "delete task");

        registerAfterCommitCleanup(objectKeys);
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
        auditLogService.record(
            AuditEventBuilder.forAction(AuditActions.TASK_TRANSITION)
                .actorUser(actorId)
                .resource("task", taskId)
                .payload("from", fromStatus.getValue())
                .payload("to", targetStatus.getValue())
                .payload("reason", reason == null ? "" : reason)
        );

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

    Set<String> collectExportObjectKeys(Long taskId) {
        Set<String> objectKeys = new LinkedHashSet<>();
        for (ExportSnapshotEntity snapshot : exportSnapshotMapper.selectAllByTaskId(taskId)) {
            String objectKey = snapshot.getObjectKey();
            if (isBlank(objectKey)) {
                continue;
            }
            addIfNotBlank(objectKeys, objectKey + "manifest.json");
            for (Map<String, Object> file : fileEntries(snapshot.getFileManifest())) {
                Object name = file.get("name");
                if (name instanceof String s && !s.isBlank()) {
                    addIfNotBlank(objectKeys, objectKey + s);
                }
            }
        }
        for (ExportJobEntity job : exportJobMapper.selectAllByTaskId(taskId)) {
            addIfNotBlank(objectKeys, job.getFileKey());
        }
        return objectKeys;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fileEntries(Map<String, Object> manifest) {
        if (manifest == null || !(manifest.get("files") instanceof List<?> rawFiles)) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object rawFile : rawFiles) {
            if (rawFile instanceof Map<?, ?> rawMap) {
                entries.add((Map<String, Object>) rawMap);
            }
        }
        return entries;
    }

    private void addIfNotBlank(Set<String> objectKeys, String objectKey) {
        if (!isBlank(objectKey)) {
            objectKeys.add(objectKey);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void registerAfterCommitCleanup(Set<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        List<String> cleanupKeys = List.copyOf(objectKeys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupS3Objects(cleanupKeys);
            }
        });
    }

    private void cleanupS3Objects(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                objectStorageWriter.deleteObject(objectKey);
            } catch (RuntimeException exception) {
                log.warn("M6-P7 best-effort S3 cleanup failed for key={}", objectKey, exception);
            }
        }
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

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
