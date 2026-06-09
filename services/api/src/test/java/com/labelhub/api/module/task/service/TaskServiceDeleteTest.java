package com.labelhub.api.module.task.service;

import com.labelhub.api.generated.model.TaskStatus;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEvent;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.dataset.mapper.DatasetMapper;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.mapper.ExportJobMapper;
import com.labelhub.api.module.export.mapper.ExportSnapshotMapper;
import com.labelhub.api.module.export.storage.ObjectStorageWriter;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskDeletionMapper;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.mapper.TaskTransitionMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TaskServiceDeleteTest {

    private static final Long TASK_ID = 100L;
    private static final Long OWNER_ID = 200L;
    private static final Long OTHER_OWNER_ID = 300L;

    private TaskMapper taskMapper;
    private TaskDeletionMapper taskDeletionMapper;
    private ExportSnapshotMapper exportSnapshotMapper;
    private ExportJobMapper exportJobMapper;
    private ObjectStorageWriter objectStorageWriter;
    private AuditLogService auditLogService;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskMapper = mock(TaskMapper.class);
        TaskTransitionMapper taskTransitionMapper = mock(TaskTransitionMapper.class);
        taskDeletionMapper = mock(TaskDeletionMapper.class);
        DatasetMapper datasetMapper = mock(DatasetMapper.class);
        exportSnapshotMapper = mock(ExportSnapshotMapper.class);
        exportJobMapper = mock(ExportJobMapper.class);
        objectStorageWriter = mock(ObjectStorageWriter.class);
        auditLogService = mock(AuditLogService.class);

        taskService = new TaskService(
                taskMapper,
                taskTransitionMapper,
                taskDeletionMapper,
                auditLogService,
                datasetMapper,
                exportSnapshotMapper,
                exportJobMapper,
                objectStorageWriter,
                Clock.systemUTC());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteTask_fullCascade_invokesFkCorrectedOrderAndCleansS3AfterCommit() {
        givenOwnedTask();
        givenExportObjects();
        when(taskDeletionMapper.deleteTask(TASK_ID)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        taskService.deleteTask(TASK_ID, OWNER_ID);

        verifyFullCascadeOrder();
        assertAuditEvent(AuditActions.TASK_DELETE, "user", "task");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        runAfterCommit();

        verify(objectStorageWriter).deleteObject("exports/tasks/100/jobs/10/manifest.json");
        verify(objectStorageWriter).deleteObject("exports/tasks/100/jobs/10/task.json");
        verify(objectStorageWriter).deleteObject("exports/tasks/100/jobs/10/answers.jsonl");
        verify(objectStorageWriter).deleteObject("exports/tasks/100/jobs/11/manifest.json");
        verify(objectStorageWriter).deleteObject("exports/tasks/100/jobs/legacy.zip");
        verifyNoMoreInteractions(objectStorageWriter);
    }

    @Test
    void deleteTask_missingTask_throwsNotFoundBeforeCascade() {
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(null);

        assertThatThrownBy(() -> taskService.deleteTask(TASK_ID, OWNER_ID))
                .isInstanceOf(TaskNotFoundException.class);

        verifyNoInteractions(taskDeletionMapper, exportSnapshotMapper, exportJobMapper, objectStorageWriter);
    }

    @Test
    void deleteTask_crossOwner_throwsAccessDeniedBeforeCascade() {
        TaskEntity task = ownedTask();
        task.setOwnerId(OTHER_OWNER_ID);
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(task);

        assertThatThrownBy(() -> taskService.deleteTask(TASK_ID, OWNER_ID))
                .isInstanceOf(TaskAccessDeniedException.class);

        verifyNoInteractions(taskDeletionMapper, exportSnapshotMapper, exportJobMapper, objectStorageWriter);
    }

    @Test
    void deleteTask_s3CleanupFailure_doesNotPropagateAfterCommit() {
        givenOwnedTask();
        givenExportObjects();
        when(taskDeletionMapper.deleteTask(TASK_ID)).thenReturn(1);
        doThrow(new RuntimeException("cleanup failed"))
                .when(objectStorageWriter)
                .deleteObject("exports/tasks/100/jobs/10/manifest.json");
        TransactionSynchronizationManager.initSynchronization();

        taskService.deleteTask(TASK_ID, OWNER_ID);

        assertThatCode(this::runAfterCommit).doesNotThrowAnyException();
        verify(objectStorageWriter).deleteObject("exports/tasks/100/jobs/legacy.zip");
    }

    @Test
    void deleteTask_mapperFailure_doesNotRegisterS3Cleanup() {
        givenOwnedTask();
        givenExportObjects();
        when(taskDeletionMapper.deleteSubmissions(TASK_ID))
                .thenThrow(new DataAccessResourceFailureException("delete failed"));
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> taskService.deleteTask(TASK_ID, OWNER_ID))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(taskDeletionMapper, never()).deleteSessions(TASK_ID);
        verify(objectStorageWriter, never()).deleteObject("exports/tasks/100/jobs/legacy.zip");
    }

    @Test
    void deleteTask_emptyTask_deletesTaskWithoutS3Cleanup() {
        givenOwnedTask();
        when(exportSnapshotMapper.selectAllByTaskId(TASK_ID)).thenReturn(List.of());
        when(exportJobMapper.selectAllByTaskId(TASK_ID)).thenReturn(List.of());
        when(taskDeletionMapper.deleteTask(TASK_ID)).thenReturn(1);

        taskService.deleteTask(TASK_ID, OWNER_ID);

        verifyFullCascadeOrder();
        verifyNoInteractions(objectStorageWriter);
    }

    private void givenOwnedTask() {
        when(taskMapper.selectByIdForUpdate(TASK_ID)).thenReturn(ownedTask());
    }

    private TaskEntity ownedTask() {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        task.setOwnerId(OWNER_ID);
        task.setTitle("M6-P7 delete target");
        task.setStatus(TaskStatus.DRAFT);
        return task;
    }

    private void givenExportObjects() {
        when(exportSnapshotMapper.selectAllByTaskId(TASK_ID)).thenReturn(List.of(
                snapshot("exports/tasks/100/jobs/10/", Map.of("files", List.of(
                        Map.of("name", "task.json"),
                        Map.of("name", "answers.jsonl"),
                        Map.of("name", "")))),
                snapshot(" ", Map.of("files", List.of(Map.of("name", "ignored.json")))),
                snapshot("exports/tasks/100/jobs/10/", Map.of("files", List.of(Map.of("name", "task.json")))),
                snapshot("exports/tasks/100/jobs/11/", Map.of())));
        when(exportJobMapper.selectAllByTaskId(TASK_ID)).thenReturn(List.of(
                job("exports/tasks/100/jobs/10/task.json"),
                job("exports/tasks/100/jobs/legacy.zip"),
                job(null)));
    }

    private ExportSnapshotEntity snapshot(String objectKey, Map<String, Object> fileManifest) {
        ExportSnapshotEntity snapshot = new ExportSnapshotEntity();
        snapshot.setObjectKey(objectKey);
        snapshot.setFileManifest(fileManifest);
        return snapshot;
    }

    private ExportJobEntity job(String fileKey) {
        ExportJobEntity job = new ExportJobEntity();
        job.setFileKey(fileKey);
        return job;
    }

    private void runAfterCommit() {
        List<TransactionSynchronization> synchronizations =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void verifyFullCascadeOrder() {
        InOrder order = inOrder(taskDeletionMapper);
        order.verify(taskDeletionMapper).clearTaskCurrentDataset(TASK_ID);
        order.verify(taskDeletionMapper).clearTaskCurrentSchemaVersion(TASK_ID);
        order.verify(taskDeletionMapper).clearLabelSchemaCurrentVersions(TASK_ID);
        order.verify(taskDeletionMapper).clearSubmissionSupersededBy(TASK_ID);
        order.verify(taskDeletionMapper).deleteAiCallsInField(TASK_ID);
        order.verify(taskDeletionMapper).deleteCurrentVerdicts(TASK_ID);
        order.verify(taskDeletionMapper).deleteReviewActions(TASK_ID);
        order.verify(taskDeletionMapper).deleteSeniorReviewCases(TASK_ID);
        order.verify(taskDeletionMapper).deleteExportSnapshots(TASK_ID);
        order.verify(taskDeletionMapper).deleteQualityLedgerEntries(TASK_ID);
        order.verify(taskDeletionMapper).deleteAiCalls(TASK_ID);
        order.verify(taskDeletionMapper).deleteDrafts(TASK_ID);
        order.verify(taskDeletionMapper).deleteSubmissions(TASK_ID);
        order.verify(taskDeletionMapper).deleteSessions(TASK_ID);
        order.verify(taskDeletionMapper).deleteDatasetItems(TASK_ID);
        order.verify(taskDeletionMapper).deleteExportJobs(TASK_ID);
        order.verify(taskDeletionMapper).deleteAdjudicationRules(TASK_ID);
        order.verify(taskDeletionMapper).deleteDatasets(TASK_ID);
        order.verify(taskDeletionMapper).deleteSchemaVersions(TASK_ID);
        order.verify(taskDeletionMapper).deleteLabelSchemas(TASK_ID);
        order.verify(taskDeletionMapper).deleteTaskTransitions(TASK_ID);
        order.verify(taskDeletionMapper).deleteTask(TASK_ID);
        order.verifyNoMoreInteractions();
    }

    private void assertAuditEvent(String action, String actorType, String resourceType) {
        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(captor.capture());
        AuditEvent event = captor.getValue().build();
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.actorType()).isEqualTo(actorType);
        assertThat(event.resourceType()).isEqualTo(resourceType);
    }
}
