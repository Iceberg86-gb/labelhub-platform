package com.labelhub.api.module.export.service;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.mapper.AiCallInFieldMapper;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class ExportFactCollector {

    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final SchemaVersionMapper schemaVersionMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final AiCallMapper aiCallMapper;
    private final AiCallInFieldMapper aiCallInFieldMapper;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;

    public ExportFactCollector(TaskMapper taskMapper, SubmissionMapper submissionMapper, SchemaVersionMapper schemaVersionMapper,
                               DatasetItemMapper datasetItemMapper, AiCallMapper aiCallMapper,
                               AiCallInFieldMapper aiCallInFieldMapper, QualityLedgerEntryMapper qualityLedgerEntryMapper) {
        this.taskMapper = taskMapper; this.submissionMapper = submissionMapper; this.schemaVersionMapper = schemaVersionMapper;
        this.datasetItemMapper = datasetItemMapper; this.aiCallMapper = aiCallMapper;
        this.aiCallInFieldMapper = aiCallInFieldMapper; this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
    }

    public ExportFactBundle collectForTask(Long taskId) {
        return collectForTask(taskId, ExportDataScope.APPROVED_ONLY);
    }

    public ExportFactBundle collectForTask(Long taskId, ExportDataScope dataScope) {
        TaskEntity task = taskMapper.selectById(taskId);
        ExportDataScope effectiveScope = dataScope == null ? ExportDataScope.APPROVED_ONLY : dataScope;
        List<SubmissionEntity> submissions;
        if (effectiveScope == ExportDataScope.FULL) {
            submissions = submissionMapper.selectSubmittedByTaskOrderedById(taskId);
        } else if (effectiveScope == ExportDataScope.APPROVED_WITH_POLICY) {
            submissions = new java.util.ArrayList<>(submissionMapper.selectApprovedByTaskOrderedById(taskId));
            submissions.addAll(submissionMapper.selectPolicyAcceptedByTaskOrderedById(taskId));
            submissions.sort(java.util.Comparator.comparing(SubmissionEntity::getId));
        } else {
            submissions = submissionMapper.selectApprovedByTaskOrderedById(taskId);
        }
        List<Long> submissionIds = submissions.stream().map(SubmissionEntity::getId).toList();

        TreeSet<Long> schemaVersionIds = new TreeSet<>();
        TreeSet<Long> datasetItemIds = new TreeSet<>();
        for (SubmissionEntity submission : submissions) {
            if (submission.getSchemaVersionId() != null) {
                schemaVersionIds.add(submission.getSchemaVersionId());
            }
            if (submission.getDatasetItemId() != null) {
                datasetItemIds.add(submission.getDatasetItemId());
            }
        }

        List<SchemaVersionEntity> schemaVersions = schemaVersionIds.isEmpty() ? List.of() : schemaVersionMapper.selectByIdsOrdered(List.copyOf(schemaVersionIds));
        List<DatasetItemEntity> datasetItems = datasetItemIds.isEmpty() ? List.of() : datasetItemMapper.selectByIdsOrdered(List.copyOf(datasetItemIds));
        List<AiCallEntity> aiCalls = submissionIds.isEmpty() ? List.of() : aiCallMapper.selectBySubmissionIdsOrdered(submissionIds);
        List<AiCallInFieldEntity> aiCallInFields = submissionIds.isEmpty() ? List.of() : aiCallInFieldMapper.selectBySubmissionIdsOrdered(submissionIds);
        List<QualityLedgerEntryEntity> ledgerEntries = submissionIds.isEmpty() ? List.of() : qualityLedgerEntryMapper.selectBySubmissionIdsOrdered(submissionIds);

        Map<Long, DerivedVerdictSnapshot> verdicts = new LinkedHashMap<>();
        for (SubmissionEntity submission : submissions) {
            QualityLedgerEntryEntity latest =
                qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(submission.getId());
            List<QualityLedgerEntryEntity> policy = latest != null
                ? List.of()
                : qualityLedgerEntryMapper.selectBySubmissionIdAndType(submission.getId(), "policy_auto_accept");
            verdicts.put(submission.getId(), policy.isEmpty()
                ? DerivedVerdictSnapshot.derive(submission.getId(), latest)
                : DerivedVerdictSnapshot.acceptedByPolicy(submission.getId(), policy.get(policy.size() - 1)));
        }

        return new ExportFactBundle(
            task,
            schemaVersions,
            datasetItems,
            submissions,
            aiCalls,
            aiCallInFields,
            ledgerEntries,
            verdicts,
            effectiveScope
        );
    }
}
