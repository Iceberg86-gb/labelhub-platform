package com.labelhub.api.module.dataset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.generated.model.TaskStatus;
import com.labelhub.api.module.dataset.entity.DatasetEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.exception.EmptyDatasetException;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.dataset.mapper.DatasetMapper;
import com.labelhub.api.module.dataset.service.parser.DatasetParser;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasetImportServiceTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final DatasetMapper datasetMapper = mock(DatasetMapper.class);
    private final DatasetItemMapper datasetItemMapper = mock(DatasetItemMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Canonicalizer canonicalizer = new Canonicalizer(objectMapper);
    private DatasetImportService service;

    @BeforeEach
    void setUp() {
        service = new DatasetImportService(
            taskMapper,
            datasetMapper,
            datasetItemMapper,
            new DatasetParser(objectMapper),
            canonicalizer
        );
    }

    @Test
    void importDataset_creates_completed_dataset_and_items_with_1_based_ordinal() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        doAnswer(invocation -> {
            DatasetEntity dataset = invocation.getArgument(0);
            dataset.setId(77L);
            return 1;
        }).when(datasetMapper).insert(any(DatasetEntity.class));
        when(datasetItemMapper.insert(any(DatasetItemEntity.class))).thenReturn(1);

        DatasetEntity dataset = service.importDataset(
            1001L,
            10L,
            "sample.json",
            DatasetImportFormat.JSON,
            stream("[{\"text\":\"first\"},{\"text\":\"second\"}]")
        );

        assertThat(dataset.getId()).isEqualTo(77L);

        ArgumentCaptor<DatasetEntity> datasetCaptor = ArgumentCaptor.forClass(DatasetEntity.class);
        verify(datasetMapper).insert(datasetCaptor.capture());
        assertThat(datasetCaptor.getValue().getTaskId()).isEqualTo(10L);
        assertThat(datasetCaptor.getValue().getSourceType()).isEqualTo("upload");
        assertThat(datasetCaptor.getValue().getSourceName()).isEqualTo("sample.json");
        assertThat(datasetCaptor.getValue().getItemCount()).isEqualTo(2);
        assertThat(datasetCaptor.getValue().getImportStatus()).isEqualTo("completed");

        ArgumentCaptor<DatasetItemEntity> itemCaptor = ArgumentCaptor.forClass(DatasetItemEntity.class);
        verify(datasetItemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting(DatasetItemEntity::getOrdinal).containsExactly(1, 2);
        assertThat(itemCaptor.getAllValues()).extracting(DatasetItemEntity::getStatus).containsOnly("available");
    }

    @Test
    void importDataset_computes_canonical_item_hash_and_allows_duplicate_items() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        doAnswer(invocation -> {
            DatasetEntity dataset = invocation.getArgument(0);
            dataset.setId(77L);
            return 1;
        }).when(datasetMapper).insert(any(DatasetEntity.class));
        when(datasetItemMapper.insert(any(DatasetItemEntity.class))).thenReturn(1);

        service.importDataset(
            1001L,
            10L,
            "sample.jsonl",
            DatasetImportFormat.JSONL,
            stream("{\"b\":2,\"a\":1}\n{\"a\":1,\"b\":2}\n")
        );

        ArgumentCaptor<DatasetItemEntity> itemCaptor = ArgumentCaptor.forClass(DatasetItemEntity.class);
        verify(datasetItemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        String expectedHash = canonicalizer.sha256Hex(canonicalizer.canonicalJson(Map.of("a", 1, "b", 2)));
        assertThat(itemCaptor.getAllValues()).extracting(DatasetItemEntity::getItemHash)
            .containsExactly(expectedHash, expectedHash);
    }

    @Test
    void importDataset_rejects_empty_dataset_without_inserting_rows() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));

        assertThatThrownBy(() -> service.importDataset(
            1001L,
            10L,
            "empty.json",
            DatasetImportFormat.JSON,
            stream("[]")
        )).isInstanceOf(EmptyDatasetException.class);

        verify(datasetMapper, never()).insert(any(DatasetEntity.class));
        verify(datasetItemMapper, never()).insert(any(DatasetItemEntity.class));
    }

    @Test
    void importDataset_maps_cross_owner_task_to_not_found() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 2002L));

        assertThatThrownBy(() -> service.importDataset(
            1001L,
            10L,
            "sample.json",
            DatasetImportFormat.JSON,
            stream("[{\"text\":\"item\"}]")
        )).isInstanceOf(TaskNotFoundException.class);

        verify(datasetMapper, never()).insert(any(DatasetEntity.class));
    }

    @Test
    void importDataset_falls_back_to_extension_when_format_is_omitted() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        doAnswer(invocation -> {
            DatasetEntity dataset = invocation.getArgument(0);
            dataset.setId(77L);
            return 1;
        }).when(datasetMapper).insert(any(DatasetEntity.class));
        when(datasetItemMapper.insert(any(DatasetItemEntity.class))).thenReturn(1);

        service.importDataset(1001L, 10L, "sample.jsonl", null, stream("{\"text\":\"line\"}\n"));

        verify(datasetItemMapper).insert(any(DatasetItemEntity.class));
    }

    private ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private TaskEntity task(Long id, Long ownerId) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setOwnerId(ownerId);
        task.setStatus(TaskStatus.DRAFT);
        return task;
    }
}
