package com.labelhub.api.module.schema.service;

import com.labelhub.api.module.schema.entity.LabelSchemaEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;

public record SchemaTemplateApplyResultView(LabelSchemaEntity schema, SchemaVersionEntity version) {
}
