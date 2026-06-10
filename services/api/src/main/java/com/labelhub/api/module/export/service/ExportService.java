package com.labelhub.api.module.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.exception.ExportFailureException;
import com.labelhub.api.module.export.exception.ExportSnapshotNotFoundException;
import com.labelhub.api.module.export.mapper.ExportJobMapper;
import com.labelhub.api.module.export.mapper.ExportSnapshotMapper;
import com.labelhub.api.module.export.storage.ObjectStorageWriter;
import com.labelhub.api.module.outbox.service.OutboxEventService;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final TaskMapper taskMapper;
    private final ExportJobMapper exportJobMapper;
    private final ExportSnapshotMapper exportSnapshotMapper;
    private final ExportFactCollector factCollector;
    private final ExportArtifactBuilder artifactBuilder;
    private final ExportPackageBuilder packageBuilder;
    private final ObjectStorageWriter storageWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AuditLogService auditLogService;
    private final OutboxEventService outboxEventService;

    @Autowired
    public ExportService(TaskMapper taskMapper, ExportJobMapper exportJobMapper, ExportSnapshotMapper exportSnapshotMapper,
                         ExportFactCollector factCollector, ExportArtifactBuilder artifactBuilder,
                         ExportPackageBuilder packageBuilder, ObjectStorageWriter storageWriter, ObjectMapper objectMapper,
                         Clock clock, AuditLogService auditLogService, OutboxEventService outboxEventService) {
        this.taskMapper = taskMapper; this.exportJobMapper = exportJobMapper; this.exportSnapshotMapper = exportSnapshotMapper;
        this.factCollector = factCollector; this.artifactBuilder = artifactBuilder; this.packageBuilder = packageBuilder;
        this.storageWriter = storageWriter; this.objectMapper = objectMapper; this.clock = clock;
        this.auditLogService = auditLogService; this.outboxEventService = outboxEventService;
    }

    public ExportService(TaskMapper taskMapper, ExportJobMapper exportJobMapper, ExportSnapshotMapper exportSnapshotMapper,
                         ExportFactCollector factCollector, ExportArtifactBuilder artifactBuilder,
                         ObjectStorageWriter storageWriter, ObjectMapper objectMapper, Clock clock,
                         AuditLogService auditLogService, OutboxEventService outboxEventService) {
        this(taskMapper, exportJobMapper, exportSnapshotMapper, factCollector, artifactBuilder, new ExportPackageBuilder(objectMapper),
            storageWriter, objectMapper, clock, auditLogService, outboxEventService);
    }

    public ExportService(TaskMapper taskMapper, ExportJobMapper exportJobMapper, ExportSnapshotMapper exportSnapshotMapper,
                         ExportFactCollector factCollector, ExportArtifactBuilder artifactBuilder,
                         ObjectStorageWriter storageWriter, ObjectMapper objectMapper, Clock clock,
                         AuditLogService auditLogService) {
        this(taskMapper, exportJobMapper, exportSnapshotMapper, factCollector, artifactBuilder, storageWriter, objectMapper,
            clock, auditLogService, null);
    }

    public ExportService(TaskMapper taskMapper, ExportJobMapper exportJobMapper, ExportSnapshotMapper exportSnapshotMapper,
                         ExportFactCollector factCollector, ExportArtifactBuilder artifactBuilder,
                         ObjectStorageWriter storageWriter, ObjectMapper objectMapper, Clock clock) {
        this(taskMapper, exportJobMapper, exportSnapshotMapper, factCollector, artifactBuilder, storageWriter, objectMapper,
            clock, AuditLogService.noop(), null);
    }

    @Transactional
    public ExportJobEntity requestExport(Long taskId, Long ownerUserId, ExportDataScope dataScope, ExportFieldMapping fieldMapping) {
        return requestExport(taskId, ownerUserId, dataScope, fieldMapping, TrainingExportProfile.flatTable());
    }

    @Transactional
    public ExportJobEntity requestExport(
        Long taskId,
        Long ownerUserId,
        ExportDataScope dataScope,
        ExportFieldMapping fieldMapping,
        TrainingExportProfile trainingProfile
    ) {
        ExportDataScope effectiveScope = dataScope == null ? ExportDataScope.APPROVED_ONLY : dataScope;
        ExportFieldMapping effectiveFieldMapping = fieldMapping == null ? ExportFieldMapping.empty() : fieldMapping;
        TrainingExportProfile effectiveTrainingProfile = trainingProfile == null ? TrainingExportProfile.flatTable() : trainingProfile;
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerUserId)) {
            throw new TaskNotFoundException(taskId);
        }
        if (outboxEventService == null) {
            throw new IllegalStateException("OutboxEventService is required for async export requests");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ExportJobEntity job = new ExportJobEntity();
        job.setTaskId(taskId);
        job.setRequestedBy(ownerUserId);
        job.setFormat("jsonl-bundle");
        job.setStatus("created");
        job.setParameters(Map.of(
            "mode", effectiveScope.mode(),
            "fieldMapping", effectiveFieldMapping.toParameter(),
            "trainingProfile", effectiveTrainingProfile.snapshot()
        ));
        job.setCreatedAt(now);
        job.setDownloadCount(0);
        requireOneRow(exportJobMapper.insert(job), "insert export job");
        outboxEventService.enqueueExportRequested(job);
        requireOneRow(exportJobMapper.markQueued(job.getId()), "mark export job queued");
        job.setStatus("queued");
        return job;
    }

    @Transactional
    public ExportSnapshotEntity createSnapshot(Long taskId, Long ownerUserId) {
        return createSnapshot(taskId, ownerUserId, ExportDataScope.APPROVED_ONLY);
    }

    @Transactional
    public ExportSnapshotEntity createSnapshot(Long taskId, Long ownerUserId, ExportDataScope dataScope) {
        return createSnapshot(taskId, ownerUserId, dataScope, ExportFieldMapping.empty());
    }

    @Transactional
    public ExportSnapshotEntity createSnapshot(Long taskId, Long ownerUserId, ExportDataScope dataScope, ExportFieldMapping fieldMapping) {
        return createSnapshot(taskId, ownerUserId, dataScope, fieldMapping, TrainingExportProfile.flatTable());
    }

    @Transactional
    public ExportSnapshotEntity createSnapshot(
        Long taskId,
        Long ownerUserId,
        ExportDataScope dataScope,
        ExportFieldMapping fieldMapping,
        TrainingExportProfile trainingProfile
    ) {
        ExportDataScope effectiveScope = dataScope == null ? ExportDataScope.APPROVED_ONLY : dataScope;
        ExportFieldMapping effectiveFieldMapping = fieldMapping == null ? ExportFieldMapping.empty() : fieldMapping;
        TrainingExportProfile effectiveTrainingProfile = trainingProfile == null ? TrainingExportProfile.flatTable() : trainingProfile;
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerUserId)) {
            throw new TaskNotFoundException(taskId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ExportJobEntity job = new ExportJobEntity();
        job.setTaskId(taskId);
        job.setRequestedBy(ownerUserId);
        job.setFormat("jsonl-bundle");
        job.setStatus("created");
        job.setParameters(Map.of(
            "mode", effectiveScope.mode(),
            "trainingProfile", effectiveTrainingProfile.snapshot()
        ));
        job.setCreatedAt(now);
        job.setStartedAt(now);
        job.setDownloadCount(0);
        requireOneRow(exportJobMapper.insert(job), "insert export job");
        return writeSnapshotForJob(job, effectiveScope, effectiveFieldMapping, effectiveTrainingProfile);
    }

    @Transactional
    public ExportSnapshotEntity processExportJob(Long exportJobId) {
        ExportJobEntity job = exportJobMapper.selectById(exportJobId);
        if (job == null) {
            throw new ExportFailureException(
                "Export job " + exportJobId + " was not found",
                new IllegalArgumentException("export job not found")
            );
        }
        if ("succeeded".equals(job.getStatus())) {
            // Idempotent no-op on at-least-once redelivery (lease expiry / crash before markProcessed):
            // the job already produced its (immutable) snapshot in a committed run, so return that
            // instead of re-running. markRunning excludes 'succeeded', so re-running here would 0-row
            // and dead-letter a job that actually finished.
            List<ExportSnapshotEntity> existing = exportSnapshotMapper.selectByExportJobId(job.getId());
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
            throw new ExportFailureException(
                "Export job " + exportJobId + " is marked succeeded but has no snapshot",
                new IllegalStateException("missing snapshot for succeeded export job")
            );
        }
        LocalDateTime startedAt = LocalDateTime.now(clock);
        requireOneRow(exportJobMapper.markRunning(job.getId(), startedAt), "mark export job running");
        job.setStartedAt(startedAt);
        try {
            ExportSnapshotEntity snapshot = writeSnapshotForJob(
                job,
                dataScopeFromJob(job),
                ExportFieldMapping.fromParameter(job.getParameters() == null ? null : job.getParameters().get("fieldMapping")),
                trainingProfileFromJob(job)
            );
            requireOneRow(exportJobMapper.markSucceeded(
                job.getId(),
                LocalDateTime.now(clock),
                snapshot.getObjectKey(),
                totalFileSize(snapshot.getFileManifest())
            ), "mark export job succeeded");
            return snapshot;
        } catch (RuntimeException exception) {
            exportJobMapper.markFailed(job.getId(), LocalDateTime.now(clock));
            throw exception;
        }
    }

    private ExportSnapshotEntity writeSnapshotForJob(
        ExportJobEntity job,
        ExportDataScope effectiveScope,
        ExportFieldMapping effectiveFieldMapping,
        TrainingExportProfile effectiveTrainingProfile
    ) {
        List<String> writtenKeys = new ArrayList<>();
        try {
            ExportFactBundle bundle = factCollector.collectForTask(job.getTaskId(), effectiveScope);
            ExportFactBundle businessBundle = effectiveScope == ExportDataScope.APPROVED_ONLY
                ? bundle
                : factCollector.collectForTask(job.getTaskId(), ExportDataScope.APPROVED_ONLY);
            ExportArtifact artifact = artifactBuilder.build(bundle, businessBundle, effectiveFieldMapping, effectiveTrainingProfile);
            Map<String, Object> fieldMappingSnapshot = fieldMappingSnapshot(artifact);
            Map<String, Object> trainingProfileSnapshot = trainingProfileSnapshot(artifact);
            String objectKeyPrefix = "exports/tasks/" + job.getTaskId() + "/jobs/" + job.getId() + "/";
            LocalDateTime now = LocalDateTime.now(clock);

            for (ArtifactFile file : artifact.files()) {
                writeObject(objectKeyPrefix + file.name(), file.content(), writtenKeys);
            }

            Map<String, Object> fullManifest = buildFullManifest(artifact, job, now, objectKeyPrefix);
            writeObject(objectKeyPrefix + "manifest.json", toJsonBytes(fullManifest), writtenKeys);

            ExportSnapshotEntity snapshot = new ExportSnapshotEntity();
            snapshot.setExportJobId(job.getId());
            snapshot.setTaskId(job.getTaskId());
            snapshot.setFileHash(artifact.fileHash());
            snapshot.setManifestHash(artifact.manifestHash());
            snapshot.setSourceStateHash(artifact.sourceStateHash());
            snapshot.setObjectKey(objectKeyPrefix);
            snapshot.setFileManifest(Map.of("files", artifact.files().stream().map(ArtifactFile::toManifestEntry).toList()));
            snapshot.setRecordCounts(artifact.recordCounts());
            snapshot.setSchemaVersionIds(bundle.schemaVersions().stream().map(SchemaVersionEntity::getId).toList());
            snapshot.setVerdictRuleVersionId(null);
            snapshot.setDataScope(effectiveScope.toSnapshotDataScope());
            snapshot.setFieldMappingSnapshot(fieldMappingSnapshot);
            snapshot.setCanonicalizationVersion(artifact.canonicalizationVersion());
            snapshot.setGeneratedAt(now);
            requireOneRow(exportSnapshotMapper.insert(snapshot), "insert export snapshot");
            auditLogService.record(
                AuditEventBuilder.forAction(AuditActions.EXPORT_SNAPSHOT_CREATE)
                    .actorUser(job.getRequestedBy())
                    .resource("export_snapshot", snapshot.getId())
                    .payload("snapshotId", snapshot.getId())
                    .payload("taskId", job.getTaskId())
                    .payload("exportJobId", job.getId())
                    .payload("fileHash", snapshot.getFileHash())
                    .payload("manifestHash", snapshot.getManifestHash())
                    .payload("sourceStateHash", snapshot.getSourceStateHash())
                    .payload("objectKey", snapshot.getObjectKey())
                    .payload("mode", effectiveScope.mode())
                    .payload("fieldMappingSnapshot", fieldMappingSnapshot)
                    .payload("trainingProfileSnapshot", trainingProfileSnapshot)
            );
            return snapshot;
        } catch (RuntimeException e) {
            cleanupBestEffort(writtenKeys);
            throw new ExportFailureException("Export failed for task " + job.getTaskId(), e);
        }
    }

    private ExportDataScope dataScopeFromJob(ExportJobEntity job) {
        Object mode = job.getParameters() == null ? null : job.getParameters().get("mode");
        return ExportDataScope.fromMode(mode == null ? null : String.valueOf(mode));
    }

    private TrainingExportProfile trainingProfileFromJob(ExportJobEntity job) {
        return TrainingExportProfile.fromParameter(job.getParameters() == null ? null : job.getParameters().get("trainingProfile"));
    }

    private Long totalFileSize(Map<String, Object> fileManifest) {
        long total = 0L;
        for (Map<String, Object> file : fileEntries(fileManifest)) {
            Object sizeBytes = file.get("sizeBytes");
            if (sizeBytes instanceof Number number) {
                total += number.longValue();
            } else if (sizeBytes != null) {
                total += Long.parseLong(String.valueOf(sizeBytes));
            }
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fieldMappingSnapshot(ExportArtifact artifact) {
        Object value = artifact.manifestContent().get("fieldMappingSnapshot");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> trainingProfileSnapshot(ExportArtifact artifact) {
        Object value = artifact.manifestContent().get("trainingProfileSnapshot");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    public PagedResult<ExportSnapshotEntity> listSnapshotsForOwner(
        Long taskId,
        Long ownerUserId,
        long page,
        long size,
        boolean archived
    ) {
        requireOwnedTask(taskId, ownerUserId);
        long offset = (page - 1) * size;
        List<ExportSnapshotEntity> items = exportSnapshotMapper.selectByTaskId(taskId, archived, offset, size);
        Long total = exportSnapshotMapper.selectCountByTaskId(taskId, archived);
        return new PagedResult<>(items, total == null ? 0 : total, page, size);
    }

    public ExportSnapshotEntity getSnapshotForOwner(Long snapshotId, Long ownerUserId) {
        ExportSnapshotEntity snapshot = exportSnapshotMapper.selectById(snapshotId);
        if (snapshot == null) {
            throw new ExportSnapshotNotFoundException(snapshotId);
        }
        TaskEntity task = taskMapper.selectById(snapshot.getTaskId());
        if (task == null || !Objects.equals(task.getOwnerId(), ownerUserId)) {
            throw new ExportSnapshotNotFoundException(snapshotId);
        }
        return snapshot;
    }

    @Transactional
    public ExportSnapshotEntity archiveSnapshotForOwner(Long snapshotId, Long ownerUserId) {
        ExportSnapshotEntity snapshot = getSnapshotForOwner(snapshotId, ownerUserId);
        if (snapshot.getArchivedAt() != null) {
            return snapshot;
        }
        LocalDateTime archivedAt = LocalDateTime.now(clock);
        requireOneRow(exportSnapshotMapper.archiveById(snapshot.getId(), archivedAt), "archive export snapshot");
        snapshot.setArchivedAt(archivedAt);
        auditLogService.record(
            AuditEventBuilder.forAction(AuditActions.EXPORT_SNAPSHOT_ARCHIVE)
                .actorUser(ownerUserId)
                .resource("export_snapshot", snapshot.getId())
                .payload("snapshotId", snapshot.getId())
                .payload("taskId", snapshot.getTaskId())
                .payload("exportJobId", snapshot.getExportJobId())
                .payload("fileHash", snapshot.getFileHash())
                .payload("manifestHash", snapshot.getManifestHash())
                .payload("sourceStateHash", snapshot.getSourceStateHash())
                .payload("objectKey", snapshot.getObjectKey())
                .payload("archivedAt", archivedAt.toString())
        );
        return snapshot;
    }

    @Transactional
    public ExportDownloadFile downloadSnapshotFile(Long snapshotId, String fileName, Long ownerUserId) {
        ExportSnapshotEntity snapshot = getSnapshotForOwner(snapshotId, ownerUserId);
        String resolvedFileName = requireManifestFile(snapshot, fileName);
        String objectKey = snapshot.getObjectKey() + resolvedFileName;
        byte[] content = storageWriter.getObject(objectKey);
        requireOneRow(exportJobMapper.incrementDownloadCount(snapshot.getExportJobId()), "increment export download count");
        return new ExportDownloadFile(resolvedFileName, storageWriter.contentTypeFor(resolvedFileName), content);
    }

    @Transactional
    public ExportDownloadPackage downloadSnapshotPackage(
        Long snapshotId,
        ExportSnapshotPackageType packageType,
        Long ownerUserId
    ) {
        ExportSnapshotEntity snapshot = getSnapshotForOwner(snapshotId, ownerUserId);
        ExportDownloadPackage download = switch (packageType) {
            case ANNOTATION_RESULTS -> packageBuilder.buildAnnotationResultsPackage(
                snapshot,
                fileName -> storageWriter.getObject(snapshot.getObjectKey() + fileName)
            );
            case TRAINING_DATA -> packageBuilder.buildTrainingDataPackage(
                snapshot,
                fileName -> storageWriter.getObject(snapshot.getObjectKey() + fileName)
            );
        };
        requireOneRow(exportJobMapper.incrementDownloadCount(snapshot.getExportJobId()), "increment export download count");
        return download;
    }

    public ExportSnapshotDiffView diffSnapshotsForOwner(
        Long baseSnapshotId,
        Long compareSnapshotId,
        Long ownerUserId
    ) {
        ExportSnapshotEntity base = getSnapshotForOwner(baseSnapshotId, ownerUserId);
        ExportSnapshotEntity compare = getSnapshotForOwner(compareSnapshotId, ownerUserId);

        boolean fileHashMatch = Objects.equals(base.getFileHash(), compare.getFileHash());
        boolean manifestHashMatch = Objects.equals(base.getManifestHash(), compare.getManifestHash());
        boolean sourceStateHashMatch = Objects.equals(base.getSourceStateHash(), compare.getSourceStateHash());

        return new ExportSnapshotDiffView(
            fileHashMatch && manifestHashMatch && sourceStateHashMatch,
            base.getId(),
            compare.getId(),
            fileHashMatch,
            manifestHashMatch,
            sourceStateHashMatch,
            computeFileLevelMatches(base.getFileManifest(), compare.getFileManifest())
        );
    }

    private void requireOwnedTask(Long taskId, Long ownerUserId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerUserId)) {
            throw new TaskNotFoundException(taskId);
        }
    }

    private String requireManifestFile(ExportSnapshotEntity snapshot, String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new ExportSnapshotNotFoundException(snapshot.getId());
        }
        for (Map<String, Object> file : fileEntries(snapshot.getFileManifest())) {
            String name = String.valueOf(file.get("name"));
            if (Objects.equals(name, fileName)) {
                return name;
            }
        }
        throw new ExportSnapshotNotFoundException(snapshot.getId());
    }

    private List<ExportSnapshotDiffView.FileLevelMatch> computeFileLevelMatches(
        Map<String, Object> baseManifest,
        Map<String, Object> compareManifest
    ) {
        Map<String, String> baseHashes = hashesByFileName(baseManifest);
        Map<String, String> compareHashes = hashesByFileName(compareManifest);
        Set<String> names = new TreeSet<>();
        names.addAll(baseHashes.keySet());
        names.addAll(compareHashes.keySet());

        List<ExportSnapshotDiffView.FileLevelMatch> matches = new ArrayList<>();
        for (String name : names) {
            String baseSha = baseHashes.get(name);
            String compareSha = compareHashes.get(name);
            matches.add(new ExportSnapshotDiffView.FileLevelMatch(
                name,
                baseSha,
                compareSha,
                Objects.equals(baseSha, compareSha)
            ));
        }
        return matches;
    }

    private Map<String, String> hashesByFileName(Map<String, Object> manifest) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Map<String, Object> file : fileEntries(manifest)) {
            hashes.put(String.valueOf(file.get("name")), String.valueOf(file.get("sha256")));
        }
        return hashes;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fileEntries(Map<String, Object> manifest) {
        if (manifest == null) {
            return List.of();
        }
        Object files = manifest.get("files");
        if (!(files instanceof List<?> rawFiles)) {
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

    private Map<String, Object> buildFullManifest(
        ExportArtifact artifact,
        ExportJobEntity job,
        LocalDateTime generatedAt,
        String objectKey
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("content", artifact.manifestContent());
        manifest.put("manifestHash", artifact.manifestHash());
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("exportJobId", job.getId());
        runtime.put("generatedAt", generatedAt.toString());
        runtime.put("objectKey", objectKey);
        manifest.put("runtime", runtime);
        return manifest;
    }

    private byte[] toJsonBytes(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new ExportFailureException("Unable to serialize export manifest", e);
        }
    }

    private void writeObject(String objectKey, byte[] content, List<String> writtenKeys) {
        storageWriter.putObject(objectKey, content);
        writtenKeys.add(objectKey);
    }

    private void cleanupBestEffort(List<String> writtenKeys) {
        for (String key : writtenKeys) {
            try {
                storageWriter.deleteObject(key);
            } catch (RuntimeException cleanupException) {
                log.warn("Failed to cleanup object key {} during export failure: {}", key, cleanupException.getMessage());
            }
        }
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
