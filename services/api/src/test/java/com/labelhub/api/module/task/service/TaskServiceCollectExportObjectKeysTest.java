package com.labelhub.api.module.task.service;

import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.mapper.ExportJobMapper;
import com.labelhub.api.module.export.mapper.ExportSnapshotMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskServiceCollectExportObjectKeysTest {

    @Test
    void collectExportObjectKeys_skipsBlankAndNonStringFileManifestNames() {
        ExportSnapshotMapper snapshotMapper = mock(ExportSnapshotMapper.class);
        ExportJobMapper jobMapper = mock(ExportJobMapper.class);
        TaskService service = new TaskService(null, null, null, null, null, snapshotMapper, jobMapper, null, null, null, null);
        ExportSnapshotEntity snapshot = new ExportSnapshotEntity();
        snapshot.setObjectKey("exports/tasks/100/jobs/10/");
        snapshot.setFileManifest(Map.of("files", List.of(
                Map.of("name", (Object) "data.jsonl"),
                Map.of("name", (Object) ""),
                Map.of("name", (Object) "   "),
                Map.of("name", (Object) 42),
                Map.<String, Object>of())));
        when(snapshotMapper.selectAllByTaskId(100L)).thenReturn(List.of(snapshot));
        when(jobMapper.selectAllByTaskId(100L)).thenReturn(List.of());

        assertThat(service.collectExportObjectKeys(100L)).containsExactly(
                "exports/tasks/100/jobs/10/manifest.json",
                "exports/tasks/100/jobs/10/data.jsonl");
    }
}
