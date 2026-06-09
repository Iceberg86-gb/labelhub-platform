package com.labelhub.api.module.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.exception.ExportFailureException;
import com.labelhub.api.module.export.exception.ExportPackageUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

@Component
public class ExportPackageBuilder {

    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final long FIXED_ENTRY_TIME = 0L;
    private static final String SCHEMA_VERSIONS_FILE = "schema-versions.jsonl";
    private static final String MANIFEST_FILE = "manifest.json";

    private static final Set<String> TRAINING_JSONL_FILES = Set.of(
        "openai-chat-sft.jsonl",
        "trl-sft.jsonl",
        "trl-dpo.jsonl"
    );

    private final ObjectMapper objectMapper;

    public ExportPackageBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExportDownloadPackage buildAnnotationResultsPackage(
        ExportSnapshotEntity snapshot,
        Function<String, byte[]> objectReader
    ) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("training-results.csv", objectReader.apply("training-results.csv"));
        entries.put("training-results.xlsx", objectReader.apply("training-results.xlsx"));
        entries.put(MANIFEST_FILE, objectReader.apply(MANIFEST_FILE));
        entries.put("schema.json", buildSchemaJson(objectReader));
        entries.put("field-mapping.json", buildFieldMappingJson(snapshot));
        return new ExportDownloadPackage(
            "labelhub-task-" + snapshot.getTaskId() + "-snapshot-" + snapshot.getId() + "-annotation-results.zip",
            ZIP_CONTENT_TYPE,
            zip(entries)
        );
    }

    public ExportDownloadPackage buildTrainingDataPackage(
        ExportSnapshotEntity snapshot,
        Function<String, byte[]> objectReader
    ) {
        List<String> trainingFiles = qualifyingTrainingFiles(snapshot);
        if (trainingFiles.isEmpty()) {
            throw new ExportPackageUnavailableException("当前快照没有有效训练数据，请选择训练格式重新导出");
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String fileName : trainingFiles) {
            entries.put(packagedTrainingName(fileName), objectReader.apply(fileName));
        }
        entries.put(MANIFEST_FILE, objectReader.apply(MANIFEST_FILE));
        entries.put("schema.json", buildSchemaJson(objectReader));
        entries.put("field-mapping.json", buildFieldMappingJson(snapshot));
        entries.put("training-profile.json", buildTrainingProfileJson(objectReader));
        return new ExportDownloadPackage(
            "labelhub-task-" + snapshot.getTaskId() + "-snapshot-" + snapshot.getId() + "-training-data.zip",
            ZIP_CONTENT_TYPE,
            zip(entries)
        );
    }

    private List<String> qualifyingTrainingFiles(ExportSnapshotEntity snapshot) {
        List<String> qualifying = new ArrayList<>();
        for (Map<String, Object> file : fileEntries(snapshot.getFileManifest())) {
            String name = String.valueOf(file.get("name"));
            if (TRAINING_JSONL_FILES.contains(name) && lineCount(file.get("lines")) > 0) {
                qualifying.add(name);
            }
        }
        return qualifying;
    }

    private String packagedTrainingName(String fileName) {
        return switch (fileName) {
            case "openai-chat-sft.jsonl" -> "openai-chat.jsonl";
            case "trl-sft.jsonl" -> "sft.jsonl";
            case "trl-dpo.jsonl" -> "preference-dpo.jsonl";
            default -> fileName;
        };
    }

    private byte[] buildSchemaJson(Function<String, byte[]> objectReader) {
        byte[] raw = objectReader.apply(SCHEMA_VERSIONS_FILE);
        List<Object> schemaVersions = new ArrayList<>();
        String content = new String(raw, StandardCharsets.UTF_8);
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                schemaVersions.add(objectMapper.readValue(trimmed, Object.class));
            } catch (JsonProcessingException e) {
                throw new ExportFailureException("Unable to parse schema-versions.jsonl while packaging export", e);
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("schemaVersions", schemaVersions);
        return toJsonBytes(schema);
    }

    private byte[] buildFieldMappingJson(ExportSnapshotEntity snapshot) {
        Map<String, Object> fieldMapping = snapshot.getFieldMappingSnapshot();
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("version", "p-f-field-mapping-v1");
            empty.put("columns", List.of());
            return toJsonBytes(empty);
        }
        return toJsonBytes(fieldMapping);
    }

    @SuppressWarnings("unchecked")
    private byte[] buildTrainingProfileJson(Function<String, byte[]> objectReader) {
        byte[] raw = objectReader.apply(MANIFEST_FILE);
        Map<String, Object> trainingProfile = new LinkedHashMap<>();
        try {
            Map<String, Object> manifest = objectMapper.readValue(raw, Map.class);
            Object contentValue = manifest.get("content");
            if (contentValue instanceof Map<?, ?> contentMap) {
                Object profile = contentMap.get("trainingProfileSnapshot");
                if (profile instanceof Map<?, ?> profileMap) {
                    trainingProfile = (Map<String, Object>) profileMap;
                }
            }
        } catch (IOException e) {
            throw new ExportFailureException("Unable to parse manifest.json while packaging export", e);
        }
        return toJsonBytes(trainingProfile);
    }

    private byte[] zip(Map<String, byte[]> entries) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(FIXED_ENTRY_TIME);
                zipOut.putNextEntry(zipEntry);
                zipOut.write(entry.getValue());
                zipOut.closeEntry();
            }
            zipOut.finish();
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to build export package zip", e);
        }
    }

    private long lineCount(Object lines) {
        if (lines instanceof Number number) {
            return number.longValue();
        }
        if (lines == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(lines));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fileEntries(Map<String, Object> manifest) {
        if (manifest == null) {
            return List.of();
        }
        Object files = manifest.get("files");
        if (!(files instanceof List<?> rawFiles)) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object rawFile : rawFiles) {
            if (rawFile instanceof Map<?, ?> rawMap) {
                entries.add((Map<String, Object>) rawMap);
            }
        }
        return entries;
    }

    private byte[] toJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new ExportFailureException("Unable to serialize export package file", e);
        }
    }
}
