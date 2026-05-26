package com.labelhub.api.module.dataset.service.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DatasetParser {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public DatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> parse(InputStream stream, DatasetImportFormat format) {
        return switch (format) {
            case JSON -> parseJsonArray(stream);
            case JSONL -> parseJsonl(stream);
        };
    }

    private List<Map<String, Object>> parseJsonArray(InputStream stream) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stream);
        } catch (IOException exception) {
            throw new InvalidDatasetFileException("Invalid JSON dataset file");
        }
        if (root == null || !root.isArray()) {
            throw new InvalidDatasetFileException("JSON dataset file must be a JSON array");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < root.size(); index++) {
            JsonNode item = root.get(index);
            if (!item.isObject()) {
                throw new InvalidDatasetFileException("JSON dataset item " + (index + 1) + " must be an object");
            }
            items.add(objectMapper.convertValue(item, OBJECT_MAP));
        }
        return items;
    }

    private List<Map<String, Object>> parseJsonl(InputStream stream) {
        List<Map<String, Object>> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JsonNode item = parseJsonlLine(trimmed, lineNumber);
                if (!item.isObject()) {
                    throw new InvalidDatasetFileException(
                        "JSONL dataset line " + lineNumber + " must be an object",
                        lineNumber
                    );
                }
                items.add(objectMapper.convertValue(item, OBJECT_MAP));
            }
        } catch (IOException exception) {
            throw new InvalidDatasetFileException("Unable to read JSONL dataset file");
        }
        if (items.isEmpty()) {
            throw new InvalidDatasetFileException("JSONL dataset file is blank");
        }
        return items;
    }

    private JsonNode parseJsonlLine(String line, int lineNumber) {
        try {
            return objectMapper.readTree(line);
        } catch (JsonProcessingException exception) {
            throw new InvalidDatasetFileException("Invalid JSONL dataset file at line " + lineNumber, lineNumber);
        }
    }
}
