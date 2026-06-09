package com.labelhub.api.module.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.exception.ExportPackageUnavailableException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportPackageBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExportPackageBuilder builder = new ExportPackageBuilder(objectMapper);

    @Test
    void buildAnnotationResultsPackage_includes_csv_excel_manifest_schema_and_field_mapping() throws Exception {
        ExportSnapshotEntity snapshot = snapshot(List.of(
            file("training-results.csv", 13),
            file("training-results.xlsx", 13),
            file("schema-versions.jsonl", 1)
        ));
        snapshot.setFieldMappingSnapshot(null);
        Function<String, byte[]> reader = reader(Map.of(
            "training-results.csv", "task_id\n".getBytes(StandardCharsets.UTF_8),
            "training-results.xlsx", "xlsx-bytes".getBytes(StandardCharsets.UTF_8),
            "manifest.json", "{\"content\":{}}".getBytes(StandardCharsets.UTF_8),
            "schema-versions.jsonl", "{\"id\":50}\n".getBytes(StandardCharsets.UTF_8)
        ));

        ExportDownloadPackage download = builder.buildAnnotationResultsPackage(snapshot, reader);

        assertThat(download.contentType()).isEqualTo("application/zip");
        assertThat(download.fileName()).isEqualTo("labelhub-task-22-snapshot-91-annotation-results.zip");
        Map<String, byte[]> entries = unzip(download.content());
        assertThat(entries.keySet()).containsExactlyInAnyOrder(
            "training-results.csv",
            "training-results.xlsx",
            "manifest.json",
            "schema.json",
            "field-mapping.json"
        );
        Map<String, Object> schema = objectMapper.readValue(entries.get("schema.json"), Map.class);
        assertThat((List<?>) schema.get("schemaVersions")).hasSize(1);
        Map<String, Object> fieldMapping = objectMapper.readValue(entries.get("field-mapping.json"), Map.class);
        assertThat(fieldMapping).containsEntry("version", "p-f-field-mapping-v1");
        assertThat((List<?>) fieldMapping.get("columns")).isEmpty();
    }

    @Test
    void buildTrainingDataPackage_includes_only_non_empty_training_file() throws Exception {
        ExportSnapshotEntity snapshot = snapshot(List.of(
            file("training-results.csv", 13),
            file("trl-dpo.jsonl", 12),
            file("schema-versions.jsonl", 1)
        ));
        Function<String, byte[]> reader = reader(Map.of(
            "trl-dpo.jsonl", "{\"prompt\":\"p\"}\n".getBytes(StandardCharsets.UTF_8),
            "manifest.json", "{\"content\":{\"trainingProfileSnapshot\":{\"format\":\"trl_dpo_jsonl\"}}}".getBytes(StandardCharsets.UTF_8),
            "schema-versions.jsonl", "{\"id\":50}\n".getBytes(StandardCharsets.UTF_8)
        ));

        ExportDownloadPackage download = builder.buildTrainingDataPackage(snapshot, reader);

        assertThat(download.fileName()).isEqualTo("labelhub-task-22-snapshot-91-training-data.zip");
        Map<String, byte[]> entries = unzip(download.content());
        assertThat(entries).containsKey("preference-dpo.jsonl");
        assertThat(entries).doesNotContainKey("training-results.csv");
        assertThat(entries.keySet()).containsExactlyInAnyOrder(
            "preference-dpo.jsonl",
            "manifest.json",
            "schema.json",
            "field-mapping.json",
            "training-profile.json"
        );
        Map<String, Object> trainingProfile = objectMapper.readValue(entries.get("training-profile.json"), Map.class);
        assertThat(trainingProfile).containsEntry("format", "trl_dpo_jsonl");
    }

    @Test
    void buildTrainingDataPackage_rejects_snapshot_without_training_file() {
        ExportSnapshotEntity snapshot = snapshot(List.of(
            file("training-results.csv", 13),
            file("training-results.xlsx", 13),
            file("schema-versions.jsonl", 1)
        ));

        assertThatThrownBy(() -> builder.buildTrainingDataPackage(snapshot, reader(Map.of())))
            .isInstanceOf(ExportPackageUnavailableException.class);
    }

    @Test
    void buildTrainingDataPackage_ignores_zero_line_training_file() {
        ExportSnapshotEntity snapshot = snapshot(List.of(
            file("openai-chat-sft.jsonl", 0),
            file("schema-versions.jsonl", 1)
        ));

        assertThatThrownBy(() -> builder.buildTrainingDataPackage(snapshot, reader(Map.of())))
            .isInstanceOf(ExportPackageUnavailableException.class);
    }

    private static ExportSnapshotEntity snapshot(List<Map<String, Object>> files) {
        ExportSnapshotEntity snapshot = new ExportSnapshotEntity();
        snapshot.setId(91L);
        snapshot.setTaskId(22L);
        snapshot.setFileManifest(Map.of("files", files));
        return snapshot;
    }

    private static Map<String, Object> file(String name, int lines) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("lines", lines);
        entry.put("sha256", "sha-" + name);
        entry.put("sizeBytes", lines * 10);
        return entry;
    }

    private static Function<String, byte[]> reader(Map<String, byte[]> objects) {
        return fileName -> {
            byte[] content = objects.get(fileName);
            if (content == null) {
                throw new AssertionError("Unexpected object read: " + fileName);
            }
            return content;
        };
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entries.put(entry.getName(), zipIn.readAllBytes());
                zipIn.closeEntry();
            }
        }
        return entries;
    }
}
