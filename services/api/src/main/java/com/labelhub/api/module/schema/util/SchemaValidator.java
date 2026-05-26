package com.labelhub.api.module.schema.util;

import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SchemaValidator {

    public void validate(SchemaDocument document) {
        if (document == null || document.getFields() == null || document.getFields().isEmpty()) {
            throw new InvalidSchemaDocumentException("fields", "schema must have at least one field");
        }

        validateFields(document.getFields(), new HashSet<>(), "fields");
    }

    private void validateFields(List<SchemaField> fields, Set<String> seenStableIds, String pathPrefix) {
        for (int i = 0; i < fields.size(); i++) {
            SchemaField field = fields.get(i);
            String fieldPath = pathPrefix + "[" + i + "]";

            if (field.getStableId() == null || field.getStableId().isBlank()) {
                throw new InvalidSchemaDocumentException(fieldPath + ".stableId", "stableId is required");
            }
            if (!seenStableIds.add(field.getStableId())) {
                throw new InvalidSchemaDocumentException(fieldPath + ".stableId",
                        "duplicate stableId: " + field.getStableId());
            }
            if (field.getLabel() == null || field.getLabel().isBlank()) {
                throw new InvalidSchemaDocumentException(fieldPath + ".label", "label is required");
            }
            if (field.getType() == null) {
                throw new InvalidSchemaDocumentException(fieldPath + ".type", "type is required");
            }
            if (field.getType() == SchemaFieldType.SINGLE_SELECT
                    || field.getType() == SchemaFieldType.MULTI_SELECT) {
                if (field.getOptions() == null || field.getOptions().isEmpty()) {
                    throw new InvalidSchemaDocumentException(fieldPath + ".options",
                            "select type requires at least one option");
                }
            }
            if (field.getType() == SchemaFieldType.NESTED_OBJECT) {
                if (field.getChildren() == null || field.getChildren().isEmpty()) {
                    throw new InvalidSchemaDocumentException(fieldPath + ".children",
                            "nested_object must have at least one child field");
                }
                validateFields(field.getChildren(), seenStableIds, fieldPath + ".children");
            }
        }
    }
}
