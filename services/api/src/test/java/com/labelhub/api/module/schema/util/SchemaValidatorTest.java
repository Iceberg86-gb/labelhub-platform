package com.labelhub.api.module.schema.util;

import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldOption;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator();

    @ParameterizedTest
    @EnumSource(SchemaFieldType.class)
    void validate_accepts_all_seven_field_types(SchemaFieldType type) {
        validator.validate(document(field("field-" + type.getValue(), type)));
    }

    @Test
    void validate_rejects_null_document() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields"));
    }

    @Test
    void validate_rejects_empty_fields() {
        SchemaDocument document = new SchemaDocument();
        document.setFields(List.of());

        assertThatThrownBy(() -> validator.validate(document))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields"));
    }

    @Test
    void validate_rejects_empty_stableId() {
        SchemaField field = field("valid", SchemaFieldType.TEXT);
        field.setStableId(" ");

        assertThatThrownBy(() -> validator.validate(document(field)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields[0].stableId"));
    }

    @Test
    void validate_rejects_blank_label() {
        SchemaField field = field("field-a", SchemaFieldType.TEXT);
        field.setLabel(" ");

        assertThatThrownBy(() -> validator.validate(document(field)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields[0].label"));
    }

    @Test
    void validate_rejects_duplicate_stableId_in_same_level() {
        assertThatThrownBy(() -> validator.validate(document(
                field("dup", SchemaFieldType.TEXT),
                field("dup", SchemaFieldType.NUMBER))))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> {
                    InvalidSchemaDocumentException exception = (InvalidSchemaDocumentException) error;
                    assertThat(exception.getField()).isEqualTo("fields[1].stableId");
                    assertThat(exception.getReason()).contains("duplicate stableId: dup");
                });
    }

    @Test
    void validate_rejects_duplicate_stableId_across_nested_levels() {
        SchemaField nested = field("parent", SchemaFieldType.NESTED_OBJECT);
        nested.setChildren(List.of(field("dup", SchemaFieldType.TEXT)));

        assertThatThrownBy(() -> validator.validate(document(
                field("dup", SchemaFieldType.TEXT),
                nested)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField())
                        .isEqualTo("fields[1].children[0].stableId"));
    }

    @Test
    void validate_rejects_single_select_without_options() {
        SchemaField field = field("single", SchemaFieldType.SINGLE_SELECT);
        field.setOptions(List.of());

        assertThatThrownBy(() -> validator.validate(document(field)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields[0].options"));
    }

    @Test
    void validate_rejects_multi_select_without_options() {
        SchemaField field = field("multi", SchemaFieldType.MULTI_SELECT);
        field.setOptions(null);

        assertThatThrownBy(() -> validator.validate(document(field)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields[0].options"));
    }

    @Test
    void validate_rejects_nested_object_without_children() {
        SchemaField field = field("nested", SchemaFieldType.NESTED_OBJECT);
        field.setChildren(List.of());

        assertThatThrownBy(() -> validator.validate(document(field)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields[0].children"));
    }

    @Test
    void validate_field_path_in_error_uses_array_indices() {
        SchemaField nested = field("parent", SchemaFieldType.NESTED_OBJECT);
        SchemaField child = field("child", SchemaFieldType.TEXT);
        child.setStableId("");
        nested.setChildren(List.of(child));

        assertThatThrownBy(() -> validator.validate(document(
                field("first", SchemaFieldType.TEXT),
                nested)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField())
                        .isEqualTo("fields[1].children[0].stableId"));
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
        if (type == SchemaFieldType.SINGLE_SELECT || type == SchemaFieldType.MULTI_SELECT) {
            SchemaFieldOption option = new SchemaFieldOption();
            option.setLabel("Option");
            option.setValue("option");
            field.setOptions(List.of(option));
        }
        if (type == SchemaFieldType.NESTED_OBJECT) {
            field.setChildren(List.of(field(stableId + "-child", SchemaFieldType.TEXT)));
        }
        return field;
    }
}
