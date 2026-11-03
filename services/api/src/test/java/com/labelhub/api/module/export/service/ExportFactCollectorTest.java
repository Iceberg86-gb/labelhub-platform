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

    @Test
    void approved_with_policy_scope_merges_policy_admitted_submissions_in_id_order_and_marks_their_admission() {
        SubmissionEntity human = new SubmissionEntity();
        human.setId(302L);
        SubmissionEntity policy = new SubmissionEntity();
        policy.setId(301L);
        com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity accept = new com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity();
        accept.setId(950L);
        accept.setEvidenceType("policy_auto_accept");
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(submissionMapper.selectApprovedByTaskOrderedById(TASK_ID)).thenReturn(List.of(human));
        when(submissionMapper.selectPolicyAcceptedByTaskOrderedById(TASK_ID)).thenReturn(List.of(policy));
        when(qualityLedgerEntryMapper.selectBySubmissionIdAndType(301L, "policy_auto_accept")).thenReturn(List.of(accept));

        ExportFactBundle bundle = collector.collectForTask(TASK_ID, ExportDataScope.APPROVED_WITH_POLICY);

        org.assertj.core.api.Assertions.assertThat(bundle.submissions()).extracting(SubmissionEntity::getId).containsExactly(301L, 302L);
        org.assertj.core.api.Assertions.assertThat(bundle.verdicts().get(301L).status()).isEqualTo("accepted_by_policy");
        org.assertj.core.api.Assertions.assertThat(bundle.verdicts().get(301L).derivedFromEntryId()).isEqualTo(950L);
    }

    @Test
    void default_scope_never_includes_policy_admitted_submissions() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        collector.collectForTask(TASK_ID);
        verify(submissionMapper, org.mockito.Mockito.never()).selectPolicyAcceptedByTaskOrderedById(TASK_ID);
        org.assertj.core.api.Assertions.assertThat(ExportDataScope.APPROVED_WITH_POLICY.toSnapshotDataScope()).containsEntry("includePolicyAccepted", true);
        org.assertj.core.api.Assertions.assertThat(ExportDataScope.APPROVED_ONLY.toSnapshotDataScope()).doesNotContainKey("includePolicyAccepted");
    }

    private static TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        return task;
    }
}
