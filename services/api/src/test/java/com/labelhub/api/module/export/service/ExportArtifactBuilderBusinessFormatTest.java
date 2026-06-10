package com.labelhub.api.module.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.shared.canonical.Canonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportArtifactBuilderBusinessFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExportArtifactBuilder builder = new ExportArtifactBuilder(new Canonicalizer(objectMapper));

    @Test
    void build_includes_flat_csv_training_results_for_approved_submissions() {
        ExportArtifact artifact = builder.build(bundle());

        String csv = utf8(file(artifact, "training-results.csv"));

        assertThat(csv).contains("task_id,dataset_item_id,submission_id,schema_version_id,submitted_at,final_verdict,reviewed_at,item.meta,item.prompt,answer.label,answer.notes");
        assertThat(csv).contains("100,200,300,400,2026-05-30T10:15,approved,2026-05-30T11:00");
        assertThat(csv).contains("\"{\"\"lang\"\":\"\"en\"\"}\"");
        assertThat(csv).contains("hello world");
        assertThat(csv).contains("positive");
        assertThat(csv).contains("\"[\"\"clear\"\"]\"");
    }

    @Test
    void build_includes_flat_excel_training_results_for_approved_submissions() throws Exception {
        ExportArtifact artifact = builder.build(bundle());

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file(artifact, "training-results.xlsx").content()))) {
            var sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            Row row = sheet.getRow(1);

            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("task_id");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("item.meta");
            assertThat(header.getCell(10).getStringCellValue()).isEqualTo("answer.notes");
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("100");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("approved");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("{\"lang\":\"en\"}");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("[\"clear\"]");
        }
    }

    @Test
    void build_neutralizes_formula_injection_in_csv_and_xlsx() throws Exception {
        ExportArtifact artifact = builder.build(injectionBundle());

        String csv = utf8(file(artifact, "training-results.csv"));
        assertThat(csv).contains("'+inc");          // item.prompt (col 8)
        assertThat(csv).contains("'=1+1");           // answer.label (col 9)
        assertThat(csv).contains("\"'=a,b\"");       // answer.notes (col 10): neutralized then RFC-4180 quoted

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file(artifact, "training-results.xlsx").content()))) {
            Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("'+inc");
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("'=1+1");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("'=a,b");
        }
    }

    @Test
    void build_applies_field_mapping_to_business_formats_and_manifest_hash() {
        ExportArtifact defaultArtifact = builder.build(bundle());
        ExportArtifact mappedArtifact = builder.build(bundle(), new ExportFieldMapping(List.of(
            new ExportFieldMappingColumn("item.prompt", "prompt_text", true),
            new ExportFieldMappingColumn("answer.label", "gold_label", true),
            new ExportFieldMappingColumn("answer.notes", "hidden_notes", false)
        )));

        String csv = utf8(file(mappedArtifact, "training-results.csv"));

        assertThat(csv).startsWith("prompt_text,gold_label\n");
        assertThat(csv).contains("hello world,positive");
        assertThat(csv).doesNotContain("hidden_notes");
        assertThat(mappedArtifact.manifestContent()).containsKey("fieldMappingSnapshot");
        assertThat(mappedArtifact.manifestHash()).isNotEqualTo(defaultArtifact.manifestHash());
    }

    @Test
    void build_includes_openai_chat_sft_jsonl_when_profile_is_configured() throws Exception {
        ExportArtifact artifact = builder.build(
            bundle(),
            ExportFieldMapping.empty(),
            TrainingExportProfile.openAiChatSft("item.prompt", "answer.label")
        );

        JsonNode line = objectMapper.readTree(utf8(file(artifact, "openai-chat-sft.jsonl")));

        assertThat(line.get("messages")).hasSize(2);
        assertThat(line.get("messages").get(0).get("role").asText()).isEqualTo("user");
        assertThat(line.get("messages").get(0).get("content").asText()).isEqualTo("hello world");
        assertThat(line.get("messages").get(1).get("role").asText()).isEqualTo("assistant");
        assertThat(line.get("messages").get(1).get("content").asText()).isEqualTo("positive");
        assertThat(artifact.recordCounts()).containsEntry("openaiChatSft", 1);
    }

    @Test
    void build_omits_empty_training_jsonl_file_when_profile_fields_do_not_match() {
        ExportArtifact artifact = builder.build(
            preferenceBundle(),
            ExportFieldMapping.empty(),
            TrainingExportProfile.openAiChatSft("item.prompt", "answer.label")
        );

        assertThat(artifact.files()).noneMatch(file -> "openai-chat-sft.jsonl".equals(file.name()));
        assertThat(artifact.recordCounts()).containsEntry("openaiChatSft", 0);
        assertThat(artifact.files()).anyMatch(file -> "training-results.csv".equals(file.name()));
        assertThat(artifact.files()).anyMatch(file -> "training-results.xlsx".equals(file.name()));
    }

    @Test
    void build_includes_trl_sft_jsonl_when_profile_is_configured() throws Exception {
        ExportArtifact artifact = builder.build(
            bundle(),
            ExportFieldMapping.empty(),
            TrainingExportProfile.trlSft("item.prompt", "answer.label")
        );

        JsonNode line = objectMapper.readTree(utf8(file(artifact, "trl-sft.jsonl")));

        assertThat(line.get("prompt").asText()).isEqualTo("hello world");
        assertThat(line.get("completion").asText()).isEqualTo("positive");
        assertThat(artifact.recordCounts()).containsEntry("trlSft", 1);
    }

    @Test
    void build_includes_trl_dpo_jsonl_when_profile_maps_preferred_option() throws Exception {
        ExportArtifact artifact = builder.build(
            preferenceBundle(),
            ExportFieldMapping.empty(),
            TrainingExportProfile.trlDpo(
                "item.prompt",
                "answer.preferred",
                linkedStringMap("A", "item.response_a", "B", "item.response_b")
            )
        );

        JsonNode line = objectMapper.readTree(utf8(file(artifact, "trl-dpo.jsonl")));

        assertThat(line.get("prompt").asText()).isEqualTo("Which answer is safer?");
        assertThat(line.get("chosen").asText()).isEqualTo("Refuse unsafe data deletion.");
        assertThat(line.get("rejected").asText()).isEqualTo("Run the destructive SQL.");
        assertThat(artifact.recordCounts()).containsEntry("trlDpo", 1);
    }

    private static ArtifactFile file(ExportArtifact artifact, String name) {
        return artifact.files().stream()
            .filter(file -> name.equals(file.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing artifact file " + name));
    }

    private static String utf8(ArtifactFile file) {
        return new String(file.content(), StandardCharsets.UTF_8);
    }

    private static ExportFactBundle bundle() {
        QualityLedgerEntryEntity ledger = ledgerEntry();
        return new ExportFactBundle(
            task(),
            List.of(),
            List.of(datasetItem()),
            List.of(submission()),
            List.of(),
            List.of(),
            List.of(ledger),
            Map.of(300L, DerivedVerdictSnapshot.derive(300L, ledger)),
            ExportDataScope.APPROVED_ONLY
        );
    }

    private static ExportFactBundle preferenceBundle() {
        QualityLedgerEntryEntity ledger = ledgerEntry();
        return new ExportFactBundle(
            task(),
            List.of(),
            List.of(preferenceDatasetItem()),
            List.of(preferenceSubmission()),
            List.of(),
            List.of(),
            List.of(ledger),
            Map.of(300L, DerivedVerdictSnapshot.derive(300L, ledger)),
            ExportDataScope.APPROVED_ONLY
        );
    }

    private static ExportFactBundle injectionBundle() {
        QualityLedgerEntryEntity ledger = ledgerEntry();
        DatasetItemEntity item = new DatasetItemEntity();
        item.setId(200L);
        item.setTaskId(100L);
        item.setItemPayload(linkedMap("prompt", "+inc", "meta", linkedMap("lang", "en")));
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(300L);
        submission.setTaskId(100L);
        submission.setDatasetItemId(200L);
        submission.setSchemaVersionId(400L);
        submission.setCreatedAt(LocalDateTime.parse("2026-05-30T10:15:00"));
        submission.setAnswerPayload(linkedMap("label", "=1+1", "notes", "=a,b"));
        return new ExportFactBundle(
            task(), List.of(), List.of(item), List.of(submission), List.of(), List.of(),
            List.of(ledger), Map.of(300L, DerivedVerdictSnapshot.derive(300L, ledger)),
            ExportDataScope.APPROVED_ONLY
        );
    }

    private static TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(100L);
        task.setTitle("Training export");
        return task;
    }

    private static DatasetItemEntity datasetItem() {
        DatasetItemEntity item = new DatasetItemEntity();
        item.setId(200L);
        item.setTaskId(100L);
        item.setItemPayload(linkedMap(
            "prompt", "hello world",
            "meta", linkedMap("lang", "en")
        ));
        return item;
    }

    private static SubmissionEntity submission() {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(300L);
        submission.setTaskId(100L);
        submission.setDatasetItemId(200L);
        submission.setSchemaVersionId(400L);
        submission.setCreatedAt(LocalDateTime.parse("2026-05-30T10:15:00"));
        submission.setAnswerPayload(linkedMap(
            "label", "positive",
            "notes", List.of("clear")
        ));
        return submission;
    }

    private static DatasetItemEntity preferenceDatasetItem() {
        DatasetItemEntity item = new DatasetItemEntity();
        item.setId(200L);
        item.setTaskId(100L);
        item.setItemPayload(linkedMap(
            "prompt", "Which answer is safer?",
            "response_a", "Refuse unsafe data deletion.",
            "response_b", "Run the destructive SQL."
        ));
        return item;
    }

    private static SubmissionEntity preferenceSubmission() {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(300L);
        submission.setTaskId(100L);
        submission.setDatasetItemId(200L);
        submission.setSchemaVersionId(400L);
        submission.setCreatedAt(LocalDateTime.parse("2026-05-30T10:15:00"));
        submission.setAnswerPayload(linkedMap("preferred", "A"));
        return submission;
    }

    private static QualityLedgerEntryEntity ledgerEntry() {
        QualityLedgerEntryEntity entry = new QualityLedgerEntryEntity();
        entry.setId(700L);
        entry.setSubmissionId(300L);
        entry.setTaskId(100L);
        entry.setEvidenceType("reviewer_overall_verdict");
        entry.setPayload(linkedMap("verdict", "approve", "reviewLevel", "senior_reviewer"));
        entry.setCreatedAt(LocalDateTime.parse("2026-05-30T11:00:00"));
        return entry;
    }

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private static Map<String, String> linkedStringMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
