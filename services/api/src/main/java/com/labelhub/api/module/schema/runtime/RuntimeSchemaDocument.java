package com.labelhub.api.module.schema.runtime;

import com.labelhub.api.generated.model.SchemaField;
import java.util.List;

public record RuntimeSchemaDocument(SchemaRuntimeFormat format, List<SchemaField> fields) {
}
