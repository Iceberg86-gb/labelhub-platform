package com.labelhub.api.module.dataset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.module.dataset.entity.DatasetEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.exception.EmptyDatasetException;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.dataset.mapper.DatasetMapper;
import com.labelhub.api.module.dataset.service.parser.DatasetParser;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetImportService {

    private static final String SOURCE_TYPE_UPLOAD = "upload";
    private static final String IMPORT_COMPLETED = "completed";
    private static final String ITEM_AVAILABLE = "available";

    private final TaskMapper taskMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final DatasetParser datasetParser;
    private final Canonicalizer canonicalizer;

    public DatasetImportService(
        TaskMapper taskMapper,
        DatasetMapper datasetMapper,
        DatasetItemMapper datasetItemMapper,
        DatasetParser datasetParser,
        Canonicalizer canonicalizer
    ) {
        this.taskMapper = taskMapper;
        this.datasetMapper = datasetMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.datasetParser = datasetParser;
        this.canonicalizer = canonicalizer;
    }

    @Transactional
    public DatasetEntity importDataset(
        Long ownerId,
        Long taskId,
        String sourceName,
        DatasetImportFormat format,
        InputStream fileStream
    ) {
        requireOwnedTask(ownerId, taskId);
        DatasetImportFormat resolvedFormat = resolveFormat(format, sourceName);
        List<Map<String, Object>> items = datasetParser.parse(fileStream, resolvedFormat);
        if (items.isEmpty()) {
            throw new EmptyDatasetException();
        }

        DatasetEntity dataset = new DatasetEntity();
        dataset.setTaskId(taskId);
        dataset.setSourceType(SOURCE_TYPE_UPLOAD);
        dataset.setSourceName(sourceName);
        dataset.setItemCount(items.size());
        dataset.setImportStatus(IMPORT_COMPLETED);
        requireOneRow(datasetMapper.insert(dataset), "insert dataset");

        int ordinal = 1;
        for (Map<String, Object> item : items) {
            DatasetItemEntity entity = new DatasetItemEntity();
            entity.setDatasetId(dataset.getId());
            entity.setTaskId(taskId);
            entity.setOrdinal(ordinal++);
            entity.setItemPayload(item);
            entity.setItemHash(canonicalizer.sha256Hex(canonicalizer.canonicalJson(item)));
            entity.setStatus(ITEM_AVAILABLE);
            requireOneRow(datasetItemMapper.insert(entity), "insert dataset item");
        }
        return dataset;
    }

    public Page<DatasetEntity> listByTask(Long ownerId, Long taskId, long page, long size) {
        requireOwnedTask(ownerId, taskId);
        return datasetMapper.selectPage(
            Page.of(page, size),
            new LambdaQueryWrapper<DatasetEntity>()
                .eq(DatasetEntity::getTaskId, taskId)
                .orderByDesc(DatasetEntity::getCreatedAt)
                .orderByDesc(DatasetEntity::getId)
        );
    }

    public DatasetEntity getDataset(Long datasetId, Long ownerId) {
        DatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new TaskNotFoundException(datasetId);
        }
        requireOwnedTask(ownerId, dataset.getTaskId());
        return dataset;
    }

    private TaskEntity requireOwnedTask(Long ownerId, Long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    private DatasetImportFormat resolveFormat(DatasetImportFormat format, String sourceName) {
        if (format != null) {
            return format;
        }
        String normalized = sourceName == null ? "" : sourceName.toLowerCase();
        if (normalized.endsWith(".xlsx")) {
            return DatasetImportFormat.EXCEL;
        }
        if (normalized.endsWith(".jsonl")) {
            return DatasetImportFormat.JSONL;
        }
        if (normalized.endsWith(".json")) {
            return DatasetImportFormat.JSON;
        }
        throw new InvalidDatasetFileException("Dataset import format is required when filename is not .json, .jsonl, or .xlsx");
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
