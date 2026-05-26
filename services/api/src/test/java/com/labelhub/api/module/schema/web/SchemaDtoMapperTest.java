package com.labelhub.api.module.schema.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.LabelSchema;
import com.labelhub.api.generated.model.PagedSchemas;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaVersion;
import com.labelhub.api.generated.model.SubmissionRenderSchema;
import com.labelhub.api.module.schema.entity.LabelSchemaEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.service.view.SubmissionRenderSchemaView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaDtoMapperTest {

    private final SchemaDtoMapper mapper = new SchemaDtoMapper(new ObjectMapper());

    @Test
    void toLabelSchema_maps_current_version_and_metadata() {
        LabelSchemaEntity entity = new LabelSchemaEntity();
        entity.setId(10L);
        entity.setTaskId(20L);
        entity.setName("Product schema");
        entity.setDescription("For product labels");
        entity.setOwnerId(1001L);
        entity.setCurrentVersionId(99L);
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        entity.setUpdatedAt(LocalDateTime.of(2026, 1, 3, 4, 5, 6));

        LabelSchema dto = mapper.toLabelSchema(entity);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getTaskId()).isEqualTo(20L);
        assertThat(dto.getName()).isEqualTo("Product schema");
        assertThat(dto.getDescription()).isEqualTo("For product labels");
        assertThat(dto.getOwnerId()).isEqualTo(1001L);
        assertThat(dto.getCurrentVersionId()).isEqualTo(99L);
        assertThat(dto.getCreatedAt()).isNotNull();
        assertThat(dto.getUpdatedAt()).isNotNull();
    }

    @Test
    void toSchemaVersion_converts_schema_json_map_back_to_schema_document() {
        SchemaVersionEntity entity = versionWithFields(42L, 10L, 1, 1001L, 2);

        SchemaVersion dto = mapper.toSchemaVersion(entity);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getSchemaId()).isEqualTo(10L);
        assertThat(dto.getVersionNumber()).isEqualTo(1);
        assertThat(dto.getOwnerId()).isEqualTo(1001L);
        assertThat(dto.getFieldStableIds()).containsExactly("field-0", "field-1");
        assertThat(dto.getSchemaJson().getFields()).hasSize(2);
        assertThat(dto.getSchemaJson().getFields().get(0).getStableId()).isEqualTo("field-0");
    }

    @Test
    void toSubmissionRenderSchema_preserves_historical_schema_and_answer_payload() {
        SchemaVersionEntity version = versionWithFields(42L, 10L, 1, 1001L, 3);
        SubmissionRenderSchemaView view = new SubmissionRenderSchemaView();
        view.setSubmissionId(700L);
        view.setSchemaVersion(version);
        view.setAnswerPayload(Map.of("field-0", "answer"));
        view.setProvenance(Map.of("source", "manual"));

        SubmissionRenderSchema dto = mapper.toSubmissionRenderSchema(view);

        assertThat(dto.getSubmissionId()).isEqualTo(700L);
        assertThat(dto.getSchemaVersion().getId()).isEqualTo(42L);
        assertThat(dto.getSchemaVersion().getSchemaJson().getFields()).hasSize(3);
        assertThat(dto.getAnswerPayload()).containsEntry("field-0", "answer");
        assertThat(dto.getProvenance()).containsEntry("source", "manual");
    }

    @Test
    void toPagedSchemas_maps_page_items_and_totals() {
        LabelSchemaEntity first = new LabelSchemaEntity();
        first.setId(1L);
        first.setName("First");
        first.setOwnerId(1001L);
        first.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));

        PagedSchemas dto = mapper.toPagedSchemas(List.of(first), 12L, 2, 5);

        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().get(0).getId()).isEqualTo(1L);
        assertThat(dto.getTotal()).isEqualTo(12L);
        assertThat(dto.getPage()).isEqualTo(2);
        assertThat(dto.getSize()).isEqualTo(5);
    }

    private static SchemaVersionEntity versionWithFields(Long id, Long schemaId, Integer versionNumber, Long ownerId, int fieldCount) {
        List<Map<String, Object>> fields = java.util.stream.IntStream.range(0, fieldCount)
            .mapToObj(index -> Map.<String, Object>of(
                "stableId", "field-" + index,
                "label", "Field " + index,
                "type", "text"
            ))
            .toList();
        SchemaVersionEntity entity = new SchemaVersionEntity();
        entity.setId(id);
        entity.setSchemaId(schemaId);
        entity.setVersionNumber(versionNumber);
        entity.setSchemaJson(Map.of("fields", fields));
        entity.setFieldStableIds(fields.stream().map(field -> (String) field.get("stableId")).toList());
        entity.setContentHash("abc");
        entity.setOwnerId(ownerId);
        entity.setPublishedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return entity;
    }
}
