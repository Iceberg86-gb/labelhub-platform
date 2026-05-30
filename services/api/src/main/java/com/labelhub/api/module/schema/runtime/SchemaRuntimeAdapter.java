package com.labelhub.api.module.schema.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldOption;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SchemaRuntimeAdapter {
    public static final String FORMAT_VERSION = "x-labelhub-schemaFormatVersion";
    public static final String RUNTIME_FIELDS = "x-labelhub-fields";
    public static final int JSON_SCHEMA_FORMAT_VERSION = 2;

    private final ObjectMapper objectMapper;

    public SchemaRuntimeAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuntimeSchemaDocument toRuntimeDocument(Map<String, Object> schemaJson) {
        Map<String, Object> normalized = withoutNullValues(schemaJson);
        SchemaRuntimeFormat format = detectFormat(normalized);
        Object rawFields = switch (format) {
            case LEGACY_FIELDS -> normalized.get("fields");
            case JSON_SCHEMA_V2 -> normalized.get(RUNTIME_FIELDS);
        };
        if (!(rawFields instanceof List<?>)) {
            String field = format == SchemaRuntimeFormat.JSON_SCHEMA_V2 ? RUNTIME_FIELDS : "fields";
            throw new InvalidSchemaDocumentException(field, "schema field list is required");
        }
        List<SchemaField> fields = objectMapper.convertValue(rawFields, new TypeReference<>() {});
        return new RuntimeSchemaDocument(format, fields);
    }

    public SchemaDocument toSchemaDocument(Map<String, Object> schemaJson) {
        SchemaDocument document = new SchemaDocument();
        document.setFields(toRuntimeDocument(schemaJson).fields());
        return document;
    }

    public Map<String, Object> toStorageJson(Map<String, Object> schemaJson) {
        Map<String, Object> normalized = withoutNullValues(schemaJson);
        RuntimeSchemaDocument runtime = toRuntimeDocument(normalized);
        if (runtime.format() == SchemaRuntimeFormat.JSON_SCHEMA_V2) {
            return normalized;
        }
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put(FORMAT_VERSION, JSON_SCHEMA_FORMAT_VERSION);
        storage.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        storage.put("type", "object");
        storage.put("properties", propertiesFor(runtime.fields()));
        List<String> required = requiredStableIds(runtime.fields());
        if (!required.isEmpty()) {
            storage.put("required", required);
        }
        storage.put(RUNTIME_FIELDS, objectMapper.convertValue(runtime.fields(), new TypeReference<List<Map<String, Object>>>() {}));
        return storage;
    }

    public List<Map<String, Object>> fieldMapsForAi(Map<String, Object> schemaJson) {
        Map<String, Object> normalized = withoutNullValues(schemaJson);
        SchemaRuntimeFormat format = detectFormat(normalized);
        Object rawFields = format == SchemaRuntimeFormat.JSON_SCHEMA_V2
            ? normalized.get(RUNTIME_FIELDS)
            : normalized.get("fields");
        return objectMapper.convertValue(rawFields, new TypeReference<>() {});
    }

    public SchemaRuntimeFormat detectFormat(Map<String, Object> schemaJson) {
        Map<String, Object> normalized = withoutNullValues(schemaJson);
        if (normalized == null || normalized.isEmpty()) {
            throw new InvalidSchemaDocumentException("schemaJson", "schema must not be empty");
        }
        if (normalized.containsKey(FORMAT_VERSION)) {
            validateJsonSchemaV2(normalized);
            return SchemaRuntimeFormat.JSON_SCHEMA_V2;
        }
        if (normalized.get("fields") instanceof List<?>) {
            return SchemaRuntimeFormat.LEGACY_FIELDS;
        }
        throw new InvalidSchemaDocumentException(
            "schemaJson",
            "schema must be legacy fields[] or JSON Schema v2 with x-labelhub-schemaFormatVersion"
        );
    }

    private void validateJsonSchemaV2(Map<String, Object> schemaJson) {
        Object version = schemaJson.get(FORMAT_VERSION);
        if (!(version instanceof Number number) || number.intValue() != JSON_SCHEMA_FORMAT_VERSION) {
            throw new InvalidSchemaDocumentException(FORMAT_VERSION, "must be 2");
        }
        if (!schemaJson.containsKey("$schema")) {
            throw new InvalidSchemaDocumentException("$schema", "JSON Schema URI is required");
        }
        if (!"object".equals(schemaJson.get("type"))) {
            throw new InvalidSchemaDocumentException("type", "root JSON Schema type must be object");
        }
        if (!(schemaJson.get("properties") instanceof Map<?, ?>)) {
            throw new InvalidSchemaDocumentException("properties", "root JSON Schema properties are required");
        }
        if (!(schemaJson.get(RUNTIME_FIELDS) instanceof List<?>)) {
            throw new InvalidSchemaDocumentException(RUNTIME_FIELDS, "LabelHub runtime fields are required");
        }
    }

    private Map<String, Object> propertiesFor(List<SchemaField> fields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (SchemaField field : fields == null ? List.<SchemaField>of() : fields) {
            properties.put(field.getStableId(), propertyFor(field));
        }
        return properties;
    }

    private Map<String, Object> propertyFor(SchemaField field) {
        Map<String, Object> property = new LinkedHashMap<>();
        if (field.getLabel() != null) {
            property.put("title", field.getLabel());
        }
        if (field.getHelp() != null && !field.getHelp().isBlank()) {
            property.put("description", field.getHelp());
        }
        SchemaFieldType type = field.getType();
        if (type == SchemaFieldType.NUMBER) {
            property.put("type", "number");
        } else if (type == SchemaFieldType.MULTI_SELECT) {
            property.put("type", "array");
            property.put("items", mapOf("type", "string", "enum", optionValues(field)));
        } else if (type == SchemaFieldType.NESTED_OBJECT) {
            List<SchemaField> children = field.getChildren() == null ? List.of() : field.getChildren();
            property.put("type", "object");
            property.put("properties", propertiesFor(children));
            List<String> required = requiredStableIds(children);
            if (!required.isEmpty()) {
                property.put("required", required);
            }
        } else {
            property.put("type", "string");
            if (type == SchemaFieldType.SINGLE_SELECT) {
                property.put("enum", optionValues(field));
            }
        }
        applyValidation(field, property);
        return property;
    }

    private void applyValidation(SchemaField field, Map<String, Object> property) {
        if (field.getValidation() == null) {
            return;
        }
        if (field.getValidation().getMinLength() != null) {
            property.put("minLength", field.getValidation().getMinLength());
        }
        if (field.getValidation().getMaxLength() != null) {
            property.put("maxLength", field.getValidation().getMaxLength());
        }
        if (field.getValidation().getMin() != null) {
            property.put("minimum", field.getValidation().getMin());
        }
        if (field.getValidation().getMax() != null) {
            property.put("maximum", field.getValidation().getMax());
        }
        if (field.getValidation().getPattern() != null && !field.getValidation().getPattern().isBlank()) {
            property.put("pattern", field.getValidation().getPattern());
        }
    }

    private List<String> requiredStableIds(List<SchemaField> fields) {
        List<String> required = new ArrayList<>();
        for (SchemaField field : fields == null ? List.<SchemaField>of() : fields) {
            if (field.getValidation() != null && Boolean.TRUE.equals(field.getValidation().getRequired())) {
                required.add(field.getStableId());
            }
        }
        return required;
    }

    private List<String> optionValues(SchemaField field) {
        return (field.getOptions() == null ? List.<SchemaFieldOption>of() : field.getOptions()).stream()
            .map(SchemaFieldOption::getValue)
            .toList();
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

    private Map<String, Object> withoutNullValues(Map<String, Object> schemaJson) {
        if (schemaJson == null) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schemaJson.entrySet()) {
            if (entry.getValue() != null) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return normalized;
    }
}
