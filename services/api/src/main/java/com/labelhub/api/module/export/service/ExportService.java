package com.labelhub.api.module.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.exception.ExportFailureException;
import com.labelhub.api.module.export.exception.ExportSnapshotNotFoundException;
import com.labelhub.api.module.export.mapper.ExportJobMapper;
import com.labelhub.api.module.export.mapper.ExportSnapshotMapper;
import com.labelhub.api.module.export.storage.ObjectStorageWriter;
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
    private final ObjectStorageWriter storageWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExportService(TaskMapper taskMapper, ExportJobMapper exportJobMapper, ExportSnapshotMapper exportSnapshotMapper,
                         ExportFactCollector factCollector, ExportArtifactBuilder artifactBuilder,
                         ObjectStorageWriter storageWriter, ObjectMapper objectMapper, Clock clock) {
        this.taskMapper = taskMapper; this.exportJobMapper = exportJobMapper; this.exportSnapshotMapper = exportSnapshotMapper;
        this.factCollector = factCollector; this.artifactBuilder = artifactBuilder; this.storageWriter = storageWriter;
        this.objectMapper = objectMapper; this.clock = clock;
    }

    @Transactional
    public ExportSnapshotEntity createSnapshot(Long taskId, Long ownerUserId) {
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
        job.setParameters(Map.of());
        job.setCreatedAt(now);
        job.setStartedAt(now);
        job.setDownloadCount(0);
        requireOneRow(exportJobMapper.insert(job), "insert export job");

        List<String> writtenKeys = new ArrayList<>();
        try {
            ExportFactBundle bundle = factCollector.collectForTask(taskId);
            ExportArtifact artifact = artifactBuilder.build(bundle);
            String objectKeyPrefix = "exports/tasks/" + taskId + "/jobs/" + job.getId() + "/";

            for (ArtifactFile file : artifact.files()) {
                writeObject(objectKeyPrefix + file.name(), file.content(), writtenKeys);
            }

            Map<String, Object> fullManifest = buildFullManifest(artifact, job, now, objectKeyPrefix);
            writeObject(objectKeyPrefix + "manifest.json", toJsonBytes(fullManifest), writtenKeys);

            ExportSnapshotEntity snapshot = new ExportSnapshotEntity();
            snapshot.setExportJobId(job.getId());
            snapshot.setTaskId(taskId);
            snapshot.setFileHash(artifact.fileHash());
            snapshot.setManifestHash(artifact.manifestHash());
            snapshot.setSourceStateHash(artifact.sourceStateHash());
            snapshot.setObjectKey(objectKeyPrefix);
            snapshot.setFileManifest(Map.of("files", artifact.files().stream().map(ArtifactFile::toManifestEntry).toList()));
            snapshot.setRecordCounts(artifact.recordCounts());
            snapshot.setSchemaVersionIds(bundle.schemaVersions().stream().map(SchemaVersionEntity::getId).toList());
            snapshot.setVerdictRuleVersionId(null);
            snapshot.setDataScope(Map.of("type", "task_complete"));
            snapshot.setFieldMappingSnapshot(Map.of());
            snapshot.setCanonicalizationVersion(artifact.canonicalizationVersion());
            snapshot.setGeneratedAt(now);
            requireOneRow(exportSnapshotMapper.insert(snapshot), "insert export snapshot");
            return snapshot;
        } catch (RuntimeException e) {
            cleanupBestEffort(writtenKeys);
            throw new ExportFailureException("Export failed for task " + taskId, e);
        }
    }

    public PagedResult<ExportSnapshotEntity> listSnapshotsForOwner(
        Long taskId,
        Long ownerUserId,
        long page,
        long size
    ) {
        requireOwnedTask(taskId, ownerUserId);
        long offset = (page - 1) * size;
        List<ExportSnapshotEntity> items = exportSnapshotMapper.selectByTaskId(taskId, offset, size);
        Long total = exportSnapshotMapper.selectCountByTaskId(taskId);
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
