package com.labelhub.api.module.schema.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaRuntimeAdapterTest {
    private static final String LEGACY_HASH = "69c5c06a0a6ccbd65f357882163061c303109dc139fdfe3e7f78ea6313beda2f";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SchemaRuntimeAdapter adapter = new SchemaRuntimeAdapter(objectMapper);
    private final Canonicalizer canonicalizer = new Canonicalizer(objectMapper);

    @Test
    void detectsLegacyFieldsWithoutMutatingOldHash() {
        Map<String, Object> legacy = legacySchema();

        RuntimeSchemaDocument runtime = adapter.toRuntimeDocument(legacy);

        assertThat(runtime.format()).isEqualTo(SchemaRuntimeFormat.LEGACY_FIELDS);
        assertThat(runtime.fields()).extracting("stableId").containsExactly("title");
        assertThat(canonicalizer.sha256Hex(canonicalizer.canonicalJson(legacy))).isEqualTo(LEGACY_HASH);
    }

    @Test
    void detectsJsonSchemaV2ByExplicitFormatMarker() {
        Map<String, Object> schema = jsonSchemaV2();

        RuntimeSchemaDocument runtime = adapter.toRuntimeDocument(schema);

        assertThat(runtime.format()).isEqualTo(SchemaRuntimeFormat.JSON_SCHEMA_V2);
        assertThat(runtime.fields()).extracting("stableId").containsExactly("title");
    }

    @Test
    void rejectsUnknownRootInsteadOfFallingBackAfterParseFailure() {
        assertThatThrownBy(() -> adapter.toRuntimeDocument(Map.of("type", "object")))
            .isInstanceOf(InvalidSchemaDocumentException.class)
            .hasMessageContaining("legacy fields[] or JSON Schema v2");
    }

    @Test
    void convertsLegacyPublishInputToJsonSchemaV2StorageShape() {
        Map<String, Object> storage = adapter.toStorageJson(legacySchema());

        assertThat(storage).containsEntry("x-labelhub-schemaFormatVersion", 2);
        assertThat(storage).containsEntry("type", "object");
        assertThat(storage).containsKeys("$schema", "properties", "x-labelhub-fields");
        assertThat(storage).doesNotContainKey("fields");
    }

    private Map<String, Object> legacySchema() {
        return Map.of(
            "fields",
            List.of(Map.of("stableId", "title", "label", "Title", "type", "text"))
        );
    }

    private Map<String, Object> jsonSchemaV2() {
        return Map.of(
            "x-labelhub-schemaFormatVersion", 2,
            "$schema", "https://json-schema.org/draft/2020-12/schema",
            "type", "object",
            "properties", Map.of("title", Map.of("type", "string")),
            "x-labelhub-fields", List.of(Map.of("stableId", "title", "label", "Title", "type", "text"))
        );
    }
}
