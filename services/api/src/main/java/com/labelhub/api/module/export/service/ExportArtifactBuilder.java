package com.labelhub.api.module.export.service;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExportArtifactBuilder {

    public static final String CANONICALIZATION_VERSION = "labelhub-canonical-v1";

    private final Canonicalizer canonicalizer;

    public ExportArtifactBuilder(Canonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public ExportArtifact build(ExportFactBundle bundle) {
        Map<String, Object> sourceState = buildSourceStateRef(bundle);
        List<ArtifactFile> files = new ArrayList<>();
        files.add(buildJsonFile("task.json", taskToCanonical(bundle.task())));
        files.add(buildJsonFile("source-state.json", sourceState));
        files.add(buildJsonlFile("schema-versions.jsonl",
            bundle.schemaVersions().stream().map(this::schemaVersionToCanonical).toList()));
        files.add(buildJsonlFile("dataset-items.jsonl",
            bundle.datasetItems().stream().map(this::datasetItemToCanonical).toList()));
        files.add(buildJsonlFile("submissions.jsonl",
            bundle.submissions().stream().map(this::submissionMetadataToCanonical).toList()));
        files.add(buildJsonlFile("answers.jsonl",
            bundle.submissions().stream().map(this::answerToCanonical).toList()));
        files.add(buildJsonlFile("ai-calls.jsonl",
            bundle.aiCalls().stream().map(this::aiCallToCanonical).toList()));
        files.add(buildJsonlFile("ai-call-in-fields.jsonl",
            bundle.aiCallInFields().stream().map(this::aiCallInFieldToCanonical).toList()));
        files.add(buildJsonlFile("ledger-entries.jsonl",
            bundle.ledgerEntries().stream().map(this::ledgerEntryToCanonical).toList()));
        files.add(buildJsonlFile("verdicts.jsonl",
            bundle.verdicts().values().stream().map(this::verdictToCanonical).toList()));

        Map<String, Integer> recordCounts = buildRecordCounts(bundle);
        Map<String, Object> manifestContent = new LinkedHashMap<>();
        manifestContent.put("taskId", bundle.task().getId());
        manifestContent.put("canonicalizationVersion", CANONICALIZATION_VERSION);
        manifestContent.put("exportedAtSourceState", sourceState);
        manifestContent.put("files", files.stream().map(ArtifactFile::toManifestEntry).toList());
        manifestContent.put("recordCounts", recordCounts);

        String manifestHash = sha256(canonicalizer.canonicalJson(manifestContent));
        String sourceStateHash = computeSourceStateHash(files);
        String fileHash = computeFileHash(files);
        return new ExportArtifact(
            List.copyOf(files),
            manifestContent,
            recordCounts,
            manifestHash,
            sourceStateHash,
            fileHash,
            CANONICALIZATION_VERSION
        );
    }

    private ArtifactFile buildJsonFile(String name, Map<String, Object> value) {
        byte[] content = (canonicalizer.canonicalJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
        return new ArtifactFile(name, content, sha256(content), 1, content.length);
    }

    private ArtifactFile buildJsonlFile(String name, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> row : rows) {
            builder.append(canonicalizer.canonicalJson(row)).append('\n');
        }
        byte[] content = builder.toString().getBytes(StandardCharsets.UTF_8);
        return new ArtifactFile(name, content, sha256(content), rows.size(), content.length);
    }

    private String computeSourceStateHash(List<ArtifactFile> files) {
        List<Map<String, Object>> fileEntries = files.stream()
            .map(file -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", file.name());
                entry.put("sha256", file.sha256());
                return entry;
            })
            .toList();
        return sha256(canonicalizer.canonicalJson(fileEntries));
    }

    private String computeFileHash(List<ArtifactFile> files) {
        StringBuilder concatenated = new StringBuilder();
        for (ArtifactFile file : files) {
            concatenated.append(file.sha256());
        }
        return sha256(concatenated.toString());
    }

    private Map<String, Object> buildSourceStateRef(ExportFactBundle bundle) {
        return map("lastSchemaVersionId", maxId(bundle.schemaVersions().stream().map(SchemaVersionEntity::getId).toList()),
            "lastDatasetItemId", maxId(bundle.datasetItems().stream().map(DatasetItemEntity::getId).toList()),
            "lastSubmissionId", maxId(bundle.submissions().stream().map(SubmissionEntity::getId).toList()),
            "lastAiCallId", maxId(bundle.aiCalls().stream().map(AiCallEntity::getId).toList()),
            "lastAiCallInFieldId", maxId(bundle.aiCallInFields().stream().map(AiCallInFieldEntity::getId).toList()),
            "lastLedgerEntryId", maxId(bundle.ledgerEntries().stream().map(QualityLedgerEntryEntity::getId).toList()));
    }

    private Map<String, Integer> buildRecordCounts(ExportFactBundle bundle) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("tasks", 1); counts.put("sourceStates", 1);
        counts.put("schemaVersions", bundle.schemaVersions().size()); counts.put("datasetItems", bundle.datasetItems().size());
        counts.put("submissions", bundle.submissions().size()); counts.put("answers", bundle.submissions().size());
        counts.put("aiCalls", bundle.aiCalls().size()); counts.put("aiCallInFields", bundle.aiCallInFields().size());
        counts.put("ledgerEntries", bundle.ledgerEntries().size()); counts.put("verdicts", bundle.verdicts().size());
        return counts;
    }

    private Long maxId(List<Long> ids) {
        return ids.stream().filter(id -> id != null).max(Long::compareTo).orElse(null);
    }

    private Map<String, Object> taskToCanonical(TaskEntity task) {
        return map("id", task.getId(), "title", task.getTitle(), "description", task.getDescription(),
            "instructionRichText", task.getInstructionRichText(), "tags", task.getTags(), "rewardRule", task.getRewardRule(),
            "deadlineAt", asString(task.getDeadlineAt()), "quotaTotal", task.getQuotaTotal(), "quotaClaimed", task.getQuotaClaimed(),
            "status", task.getStatusCode(), "ownerId", task.getOwnerId(), "currentSchemaVersionId", task.getCurrentSchemaVersionId(),
            "currentDatasetId", task.getCurrentDatasetId(), "createdAt", asString(task.getCreatedAt()), "updatedAt", asString(task.getUpdatedAt()));
    }

    private Map<String, Object> schemaVersionToCanonical(SchemaVersionEntity version) {
        return map("id", version.getId(), "schemaId", version.getSchemaId(), "versionNumber", version.getVersionNumber(),
            "schemaJson", version.getSchemaJson(), "fieldStableIds", version.getFieldStableIds(), "contentHash", version.getContentHash(),
            "status", version.getStatusCode(), "publishedAt", asString(version.getPublishedAt()), "createdAt", asString(version.getCreatedAt()));
    }

    private Map<String, Object> datasetItemToCanonical(DatasetItemEntity item) {
        return map("id", item.getId(), "datasetId", item.getDatasetId(), "taskId", item.getTaskId(), "ordinal", item.getOrdinal(),
            "itemPayload", item.getItemPayload(), "itemHash", item.getItemHash(), "status", item.getStatus(), "createdAt", asString(item.getCreatedAt()));
    }

    private Map<String, Object> submissionMetadataToCanonical(SubmissionEntity submission) {
        return map("id", submission.getId(), "sessionId", submission.getSessionId(), "taskId", submission.getTaskId(),
            "datasetItemId", submission.getDatasetItemId(), "labelerId", submission.getLabelerId(), "schemaVersionId", submission.getSchemaVersionId(),
            "provenance", submission.getProvenance(), "contentHash", submission.getContentHash(), "status", submission.getStatusCode(),
            "createdAt", asString(submission.getCreatedAt()), "supersededById", submission.getSupersededById());
    }

    private Map<String, Object> answerToCanonical(SubmissionEntity submission) {
        return map("submissionId", submission.getId(), "payload", submission.getAnswerPayload());
    }

    private Map<String, Object> aiCallToCanonical(AiCallEntity call) {
        return map("id", call.getId(), "submissionId", call.getSubmissionId(), "fieldPath", call.getFieldPath(), "purpose", call.getPurpose(),
            "promptVersion", call.getPromptVersion(), "modelProvider", call.getModelProvider(), "modelName", call.getModelName(),
            "inputHash", call.getInputHash(), "responsePayload", call.getResponsePayload(), "scores", call.getScores(), "verdict", call.getVerdict(),
            "tokenInput", call.getTokenInput(), "tokenOutput", call.getTokenOutput(), "costDecimal", call.getCostDecimal(), "latencyMs", call.getLatencyMs(),
            "status", call.getStatus(), "idempotencyKey", call.getIdempotencyKey(), "createdAt", asString(call.getCreatedAt()), "completedAt", asString(call.getCompletedAt()));
    }

    private Map<String, Object> aiCallInFieldToCanonical(AiCallInFieldEntity field) {
        return map("id", field.getId(), "submissionId", field.getSubmissionId(), "fieldPath", field.getFieldPath(), "aiCallId", field.getAiCallId(),
            "accepted", field.getAccepted(), "userModifiedAfter", field.getUserModifiedAfter(), "ordinal", field.getOrdinal(), "createdAt", asString(field.getCreatedAt()));
    }

    private Map<String, Object> ledgerEntryToCanonical(QualityLedgerEntryEntity entry) {
        return map("id", entry.getId(), "submissionId", entry.getSubmissionId(), "taskId", entry.getTaskId(),
            "evidenceType", entry.getEvidenceType(), "actorType", entry.getActorType(), "actorId", entry.getActorId(),
            "aiCallId", entry.getAiCallId(), "payload", entry.getPayload(), "createdAt", asString(entry.getCreatedAt()));
    }

    private Map<String, Object> verdictToCanonical(DerivedVerdictSnapshot verdict) {
        return map("submissionId", verdict.submissionId(), "status", verdict.status(), "derivedFromEntryId", verdict.derivedFromEntryId());
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private String asString(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String sha256(byte[] value) {
        return canonicalizer.sha256Hex(new String(value, StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        return canonicalizer.sha256Hex(value);
    }
}
