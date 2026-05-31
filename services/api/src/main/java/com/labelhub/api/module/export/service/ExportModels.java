package com.labelhub.api.module.export.service;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.task.entity.TaskEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record ExportFactBundle(TaskEntity task, List<SchemaVersionEntity> schemaVersions, List<DatasetItemEntity> datasetItems,
                        List<SubmissionEntity> submissions, List<AiCallEntity> aiCalls,
                        List<AiCallInFieldEntity> aiCallInFields, List<QualityLedgerEntryEntity> ledgerEntries,
                        Map<Long, DerivedVerdictSnapshot> verdicts, ExportDataScope dataScope) {}

record DerivedVerdictSnapshot(Long submissionId, String status, Long derivedFromEntryId) {
    static DerivedVerdictSnapshot derive(Long submissionId, QualityLedgerEntryEntity latest) {
        if (latest == null || latest.getPayload() == null) {
            return new DerivedVerdictSnapshot(submissionId, "pending", null);
        }
        Object verdict = latest.getPayload().get("verdict");
        String status = "approve".equals(verdict) ? "approved" : "reject".equals(verdict) ? "rejected" : "pending";
        return new DerivedVerdictSnapshot(submissionId, status, latest.getId());
    }
}

record ArtifactFile(String name, byte[] content, String sha256, int lines, long sizeBytes) {
    Map<String, Object> toManifestEntry() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name); entry.put("sha256", sha256); entry.put("lines", lines); entry.put("sizeBytes", sizeBytes);
        return entry;
    }
}

record ExportArtifact(List<ArtifactFile> files, Map<String, Object> manifestContent, Map<String, Integer> recordCounts,
                      String manifestHash, String sourceStateHash, String fileHash, String canonicalizationVersion) {}
