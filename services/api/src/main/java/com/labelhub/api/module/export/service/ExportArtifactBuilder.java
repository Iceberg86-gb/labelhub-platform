package com.labelhub.api.module.export.service;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExportArtifactBuilder {

    public static final String CANONICALIZATION_VERSION = "labelhub-canonical-v1";

    private final Canonicalizer canonicalizer;

    public ExportArtifactBuilder(Canonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public ExportArtifact build(ExportFactBundle bundle) {
        return build(bundle, ExportFieldMapping.empty());
    }

    public ExportArtifact build(ExportFactBundle bundle, ExportFieldMapping fieldMapping) {
        ExportFieldMapping effectiveMapping = fieldMapping == null ? ExportFieldMapping.empty() : fieldMapping;
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
        List<Map<String, String>> trainingRows = trainingResultRows(bundle);
        List<String> trainingHeaders = trainingHeaders(trainingRows);
        List<ExportFieldMappingColumn> trainingColumns = effectiveMapping.effectiveColumns(trainingHeaders);
        Map<String, Object> fieldMappingSnapshot = effectiveMapping.snapshot(trainingHeaders);
        files.add(buildCsvFile("training-results.csv", trainingColumns, trainingRows));
        files.add(buildXlsxFile("training-results.xlsx", trainingColumns, trainingRows));

        Map<String, Integer> recordCounts = buildRecordCounts(bundle);
        recordCounts.put("trainingResults", trainingRows.size());
        Map<String, Object> manifestContent = new LinkedHashMap<>();
        manifestContent.put("taskId", bundle.task().getId());
        manifestContent.put("canonicalizationVersion", CANONICALIZATION_VERSION);
        manifestContent.put("dataScope", bundle.dataScope().toSnapshotDataScope());
        manifestContent.put("fieldMappingSnapshot", fieldMappingSnapshot);
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

    private ArtifactFile buildCsvFile(String name, List<ExportFieldMappingColumn> columns, List<Map<String, String>> rows) {
        StringBuilder builder = new StringBuilder();
        appendCsvRow(builder, columns.stream().map(ExportFieldMappingColumn::columnName).toList());
        for (Map<String, String> row : rows) {
            appendCsvRow(builder, columns.stream().map(column -> row.getOrDefault(column.source(), "")).toList());
        }
        byte[] content = builder.toString().getBytes(StandardCharsets.UTF_8);
        return new ArtifactFile(name, content, sha256(content), rows.size() + 1, content.length);
    }

    private ArtifactFile buildXlsxFile(String name, List<ExportFieldMappingColumn> columns, List<Map<String, String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var coreProperties = workbook.getProperties().getCoreProperties();
            Date fixedDate = Date.from(Instant.EPOCH);
            coreProperties.setCreated(Optional.of(fixedDate));
            coreProperties.setModified(Optional.of(fixedDate));
            coreProperties.setCreator("LabelHub");
            coreProperties.setLastModifiedByUser("LabelHub");
            coreProperties.setTitle("training-results");
            coreProperties.setRevision("1");
            var sheet = workbook.createSheet("training-results");
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < columns.size(); index++) {
                headerRow.createCell(index).setCellValue(columns.get(index).columnName());
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Map<String, String> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values.getOrDefault(columns.get(columnIndex).source(), ""));
                }
            }
            workbook.write(output);
            byte[] content = output.toByteArray();
            return new ArtifactFile(name, content, sha256(content), rows.size() + 1, content.length);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to build Excel export artifact", exception);
        }
    }

    private void appendCsvRow(StringBuilder builder, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(csv(values.get(index)));
        }
        builder.append('\n');
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
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
        return map("dataScope", bundle.dataScope().toSnapshotDataScope(),
            "lastSchemaVersionId", maxId(bundle.schemaVersions().stream().map(SchemaVersionEntity::getId).toList()),
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

    private List<Map<String, String>> trainingResultRows(ExportFactBundle bundle) {
        Map<Long, DatasetItemEntity> itemsById = new HashMap<>();
        for (DatasetItemEntity item : bundle.datasetItems()) {
            itemsById.put(item.getId(), item);
        }
        Map<Long, QualityLedgerEntryEntity> ledgerById = new HashMap<>();
        for (QualityLedgerEntryEntity entry : bundle.ledgerEntries()) {
            ledgerById.put(entry.getId(), entry);
        }
        return bundle.submissions().stream()
            .sorted(Comparator.comparing(SubmissionEntity::getId, Comparator.nullsLast(Long::compareTo)))
            .map(submission -> trainingResultRow(
                submission,
                itemsById.get(submission.getDatasetItemId()),
                bundle.verdicts().get(submission.getId()),
                ledgerById
            ))
            .toList();
    }

    private Map<String, String> trainingResultRow(
        SubmissionEntity submission,
        DatasetItemEntity item,
        DerivedVerdictSnapshot verdict,
        Map<Long, QualityLedgerEntryEntity> ledgerById
    ) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("task_id", cell(submission.getTaskId()));
        row.put("dataset_item_id", cell(submission.getDatasetItemId()));
        row.put("submission_id", cell(submission.getId()));
        row.put("schema_version_id", cell(submission.getSchemaVersionId()));
        row.put("submitted_at", cell(submission.getCreatedAt()));
        row.put("final_verdict", verdict == null ? "" : cell(verdict.status()));
        QualityLedgerEntryEntity entry = verdict == null ? null : ledgerById.get(verdict.derivedFromEntryId());
        row.put("reviewed_at", entry == null ? "" : cell(entry.getCreatedAt()));
        addPayloadColumns(row, "item.", item == null ? Map.of() : item.getItemPayload());
        addPayloadColumns(row, "answer.", submission.getAnswerPayload());
        return row;
    }

    private void addPayloadColumns(Map<String, String> row, String prefix, Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        TreeSet<String> keys = new TreeSet<>(payload.keySet());
        for (String key : keys) {
            row.put(prefix + key, cell(payload.get(key)));
        }
    }

    private List<String> trainingHeaders(List<Map<String, String>> rows) {
        List<String> headers = new ArrayList<>(List.of(
            "task_id",
            "dataset_item_id",
            "submission_id",
            "schema_version_id",
            "submitted_at",
            "final_verdict",
            "reviewed_at"
        ));
        TreeSet<String> itemHeaders = new TreeSet<>();
        TreeSet<String> answerHeaders = new TreeSet<>();
        for (Map<String, String> row : rows) {
            for (String header : row.keySet()) {
                if (header.startsWith("item.")) {
                    itemHeaders.add(header);
                }
                if (header.startsWith("answer.")) {
                    answerHeaders.add(header);
                }
            }
        }
        headers.addAll(itemHeaders);
        headers.addAll(answerHeaders);
        return headers;
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
        Map<String, Object> row = map("id", call.getId(), "submissionId", call.getSubmissionId(),
            "fieldPath", call.getFieldPath(), "purpose", call.getPurpose(), "promptVersion", call.getPromptVersion());
        putIfNotNull(row, "promptVersionId", call.getPromptVersionId());
        putIfNotNull(row, "aiReviewRuleId", call.getAiReviewRuleId());
        row.putAll(map("providerAdapterVersion", call.getProviderAdapterVersion(),
            "modelProvider", call.getModelProvider(), "modelName", call.getModelName(),
            "inputHash", call.getInputHash(), "responsePayload", call.getResponsePayload(),
            "scores", call.getScores(), "verdict", call.getVerdict(), "tokenInput", call.getTokenInput(),
            "tokenOutput", call.getTokenOutput(), "costDecimal", call.getCostDecimal(),
            "latencyMs", call.getLatencyMs(), "status", call.getStatus(),
            "idempotencyKey", call.getIdempotencyKey(), "createdAt", asString(call.getCreatedAt()),
            "completedAt", asString(call.getCompletedAt())));
        return row;
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

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String asString(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return asString(dateTime);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return canonicalizer.canonicalJson(value);
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value);
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String sha256(String value) {
        return canonicalizer.sha256Hex(value);
    }
}
