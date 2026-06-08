package com.labelhub.api.module.schema.service;

import com.labelhub.api.module.schema.entity.LabelSchemaEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;

public record SchemaTemplateImportResultView(LabelSchemaEntity schema, SchemaVersionEntity version) {
}
