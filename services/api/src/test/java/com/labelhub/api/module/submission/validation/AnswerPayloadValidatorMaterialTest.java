package com.labelhub.api.module.submission.validation;

import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.generated.model.SchemaFieldValidation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPayloadValidatorMaterialTest {

    private final AnswerPayloadValidator validator = new AnswerPayloadValidator();

    @Test
    void fileUpload_requiresPersistedObjectReference() {
        SchemaDocument schema = document(field("receipt", SchemaFieldType.FILE_UPLOAD));

        assertThat(validator.validate(schema, Map.of(
            "receipt", Map.of(
                "objectKey", "session-attachments/20260530/task-1/session-2/file.png",
                "fileName", "file.png",
                "sizeBytes", 1024L
            )
        ))).isEmpty();

        assertThat(validator.validate(schema, Map.of("receipt", "s3://legacy/string")))
            .extracting(AnswerValidationError::stableId)
            .containsExactly("receipt");
    }

    @Test
    void jsonEditor_acceptsArbitraryJsonValues() {
        SchemaDocument schema = document(field("metadata", SchemaFieldType.JSON_EDITOR));

        assertThat(validator.validate(schema, Map.of(
            "metadata", Map.of("score", 0.98, "tags", List.of("a", "b"))
        ))).isEmpty();
    }

    @Test
    void llmInteraction_requiresObjectEvidencePayload() {
        SchemaDocument schema = document(field("assist", SchemaFieldType.LLM_INTERACTION));

        assertThat(validator.validate(schema, Map.of("assist", Map.of("input", "draft", "aiCallId", 9L)))).isEmpty();
        assertThat(validator.validate(schema, Map.of("assist", "draft")))
            .extracting(AnswerValidationError::reason)
            .containsExactly("必须是对象");
    }

    @Test
    void showItem_isDisplayOnlyAndIgnoredEvenWhenMarkedRequired() {
        SchemaField showItem = field("show", SchemaFieldType.SHOW_ITEM);
        SchemaFieldValidation validation = new SchemaFieldValidation();
        validation.setRequired(true);
        showItem.setValidation(validation);

        assertThat(validator.validate(document(showItem), Map.of())).isEmpty();
    }

    private static SchemaDocument document(SchemaField... fields) {
        SchemaDocument document = new SchemaDocument();
        document.setFields(List.of(fields));
        return document;
    }

    private static SchemaField field(String stableId, SchemaFieldType type) {
        SchemaField field = new SchemaField();
        field.setStableId(stableId);
        field.setLabel("Label " + stableId);
        field.setType(type);
        return field;
    }
}
