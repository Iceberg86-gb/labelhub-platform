package com.labelhub.api.module.dataset.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.api.module.dataset.entity.DatasetEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.dataset.mapper.DatasetMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetItemService {

    private static final String LOCKED_REASON = "已被领取/标注的题目不可编辑";

    private final DatasetMapper datasetMapper;
    private final TaskMapper taskMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final Canonicalizer canonicalizer;

    public DatasetItemService(
        DatasetMapper datasetMapper,
        TaskMapper taskMapper,
        DatasetItemMapper datasetItemMapper,
        Canonicalizer canonicalizer
    ) {
        this.datasetMapper = datasetMapper;
        this.taskMapper = taskMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.canonicalizer = canonicalizer;
    }

    public IPage<DatasetItemEntity> listItems(Long ownerId, Long datasetId, long page, long size) {
        DatasetEntity dataset = requireOwnedDataset(ownerId, datasetId);
        return datasetItemMapper.selectPageByDataset(Page.of(page, size), dataset.getId(), dataset.getTaskId());
    }

    @Transactional
    public DatasetItemBulkUpdateResult bulkUpdate(Long ownerId, Long datasetId, List<DatasetItemUpdateCommand> commands) {
        DatasetEntity dataset = requireOwnedDataset(ownerId, datasetId);
        if (commands == null || commands.isEmpty()) {
            return new DatasetItemBulkUpdateResult(List.of(), List.of());
        }

        List<Long> ids = commands.stream().map(DatasetItemUpdateCommand::id).filter(Objects::nonNull).distinct().toList();
        Map<Long, DatasetItemEntity> existingById = datasetItemMapper.selectByIdsOrdered(ids).stream()
            .filter(item -> Objects.equals(item.getDatasetId(), dataset.getId()))
            .filter(item -> Objects.equals(item.getTaskId(), dataset.getTaskId()))
            .collect(Collectors.toMap(DatasetItemEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<DatasetItemEntity> updated = new ArrayList<>();
        List<DatasetItemUpdateSkipped> skipped = new ArrayList<>();
        for (DatasetItemUpdateCommand command : commands) {
            DatasetItemEntity existing = existingById.get(command.id());
            if (existing == null) {
                skipped.add(new DatasetItemUpdateSkipped(command.id(), "题目不存在或不属于该数据集"));
                continue;
            }
            Map<String, Object> itemPayload = command.itemPayload() == null ? Map.of() : command.itemPayload();
            String itemHash = canonicalizer.sha256Hex(canonicalizer.canonicalJson(itemPayload));
            int affectedRows = datasetItemMapper.updatePayloadIfAvailable(
                existing.getId(),
                dataset.getId(),
                dataset.getTaskId(),
                itemPayload,
                itemHash
            );
            if (affectedRows == 1) {
                existing.setItemPayload(itemPayload);
                existing.setItemHash(itemHash);
                updated.add(existing);
            } else {
                skipped.add(new DatasetItemUpdateSkipped(existing.getId(), LOCKED_REASON));
            }
        }
        return new DatasetItemBulkUpdateResult(updated, skipped);
    }

    private DatasetEntity requireOwnedDataset(Long ownerId, Long datasetId) {
        DatasetEntity dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new TaskNotFoundException(datasetId);
        }
        TaskEntity task = taskMapper.selectById(dataset.getTaskId());
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(dataset.getTaskId());
        }
        return dataset;
    }
}
