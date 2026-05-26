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
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SchemaDtoMapper {

    private final ObjectMapper objectMapper;

    public SchemaDtoMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LabelSchema toLabelSchema(LabelSchemaEntity entity) {
        LabelSchema dto = new LabelSchema();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setOwnerId(entity.getOwnerId());
        dto.setCurrentVersionId(entity.getCurrentVersionId());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        dto.setUpdatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    public SchemaVersion toSchemaVersion(SchemaVersionEntity entity) {
        SchemaVersion dto = new SchemaVersion();
        dto.setId(entity.getId());
        dto.setSchemaId(entity.getSchemaId());
        dto.setVersionNumber(entity.getVersionNumber());
        dto.setSchemaJson(objectMapper.convertValue(entity.getSchemaJson(), SchemaDocument.class));
        dto.setFieldStableIds(entity.getFieldStableIds());
        dto.setContentHash(entity.getContentHash());
        dto.setPublishedAt(entity.getPublishedAt() == null ? null : entity.getPublishedAt().atOffset(ZoneOffset.UTC));
        dto.setOwnerId(entity.getOwnerId());
        return dto;
    }

    public SubmissionRenderSchema toSubmissionRenderSchema(SubmissionRenderSchemaView view) {
        SubmissionRenderSchema dto = new SubmissionRenderSchema();
        dto.setSubmissionId(view.getSubmissionId());
        dto.setSchemaVersion(toSchemaVersion(view.getSchemaVersion()));
        dto.setAnswerPayload(view.getAnswerPayload());
        dto.setProvenance(view.getProvenance());
        return dto;
    }

    public PagedSchemas toPagedSchemas(List<LabelSchemaEntity> items, long total, int page, int size) {
        PagedSchemas dto = new PagedSchemas();
        dto.setItems(items.stream().map(this::toLabelSchema).toList());
        dto.setTotal(total);
        dto.setPage(page);
        dto.setSize(size);
        return dto;
    }
}
