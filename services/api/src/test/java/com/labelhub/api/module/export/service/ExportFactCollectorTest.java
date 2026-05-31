package com.labelhub.api.module.export.service;

import com.labelhub.api.module.ai.mapper.AiCallInFieldMapper;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportFactCollectorTest {

    private static final Long TASK_ID = 100L;

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private final SchemaVersionMapper schemaVersionMapper = mock(SchemaVersionMapper.class);
    private final DatasetItemMapper datasetItemMapper = mock(DatasetItemMapper.class);
    private final AiCallMapper aiCallMapper = mock(AiCallMapper.class);
    private final AiCallInFieldMapper aiCallInFieldMapper = mock(AiCallInFieldMapper.class);
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper = mock(QualityLedgerEntryMapper.class);

    private final ExportFactCollector collector = new ExportFactCollector(
        taskMapper,
        submissionMapper,
        schemaVersionMapper,
        datasetItemMapper,
        aiCallMapper,
        aiCallInFieldMapper,
        qualityLedgerEntryMapper
    );

    @Test
    void collectForTask_uses_approved_only_submission_selection_by_default() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());

        collector.collectForTask(TASK_ID);

        verify(submissionMapper).selectApprovedByTaskOrderedById(TASK_ID);
    }

    @Test
    void collectForTask_full_scope_uses_all_submitted_selection() {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(300L);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(submissionMapper.selectSubmittedByTaskOrderedById(TASK_ID)).thenReturn(List.of(submission));

        collector.collectForTask(TASK_ID, ExportDataScope.FULL);

        verify(submissionMapper).selectSubmittedByTaskOrderedById(TASK_ID);
    }

    private static TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        return task;
    }
}
