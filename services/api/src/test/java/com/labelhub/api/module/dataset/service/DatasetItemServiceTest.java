package com.labelhub.api.module.dataset.service;

import com.labelhub.api.module.dataset.entity.DatasetEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.dataset.mapper.DatasetMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasetItemServiceTest {

    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final DatasetItemMapper datasetItemMapper = mock(DatasetItemMapper.class);
    private final Canonicalizer canonicalizer = new Canonicalizer(new ObjectMapper());
    private final DatasetItemService service = new DatasetItemService(datasetMapper, taskMapper, datasetItemMapper, canonicalizer);

    @Test
    void bulkUpdate_updates_available_items_and_skips_locked_rows() {
        when(datasetMapper.selectById(77L)).thenReturn(dataset(77L, 10L));
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(datasetItemMapper.selectByIdsOrdered(List.of(1L, 2L))).thenReturn(List.of(
            item(1L, 77L, 10L, "available", Map.of("text", "old")),
            item(2L, 77L, 10L, "claimed", Map.of("text", "locked"))
        ));

        Map<String, Object> changed = Map.of("text", "changed");
        String expectedHash = canonicalizer.sha256Hex(canonicalizer.canonicalJson(changed));
        when(datasetItemMapper.updatePayloadIfAvailable(1L, 77L, 10L, changed, expectedHash)).thenReturn(1);
        when(datasetItemMapper.updatePayloadIfAvailable(2L, 77L, 10L, Map.of("text", "ignored"), canonicalizer.sha256Hex(canonicalizer.canonicalJson(Map.of("text", "ignored"))))).thenReturn(0);

        DatasetItemBulkUpdateResult result = service.bulkUpdate(
            1001L,
            77L,
            List.of(
                new DatasetItemUpdateCommand(1L, changed),
                new DatasetItemUpdateCommand(2L, Map.of("text", "ignored"))
            )
        );

        assertThat(result.updated()).extracting(DatasetItemEntity::getId).containsExactly(1L);
        assertThat(result.updated().get(0).getItemPayload()).isEqualTo(changed);
        assertThat(result.updated().get(0).getItemHash()).isEqualTo(expectedHash);
        assertThat(result.skippedLocked()).extracting(DatasetItemUpdateSkipped::id).containsExactly(2L);
        assertThat(result.skippedLocked().get(0).reason()).contains("不可编辑");
        verify(datasetItemMapper).updatePayloadIfAvailable(1L, 77L, 10L, changed, expectedHash);
    }

    @Test
    void bulkUpdate_rejects_cross_owner_dataset() {
        when(datasetMapper.selectById(77L)).thenReturn(dataset(77L, 10L));
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 2002L));

        assertThatThrownBy(() -> service.bulkUpdate(1001L, 77L, List.of(new DatasetItemUpdateCommand(1L, Map.of("text", "x")))))
            .isInstanceOf(TaskNotFoundException.class);
    }

    private DatasetEntity dataset(Long id, Long taskId) {
        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(id);
        dataset.setTaskId(taskId);
        return dataset;
    }

    private TaskEntity task(Long id, Long ownerId) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setOwnerId(ownerId);
        return task;
    }

    private DatasetItemEntity item(Long id, Long datasetId, Long taskId, String status, Map<String, Object> payload) {
        DatasetItemEntity item = new DatasetItemEntity();
        item.setId(id);
        item.setDatasetId(datasetId);
        item.setTaskId(taskId);
        item.setStatus(status);
        item.setItemPayload(payload);
        return item;
    }
}
