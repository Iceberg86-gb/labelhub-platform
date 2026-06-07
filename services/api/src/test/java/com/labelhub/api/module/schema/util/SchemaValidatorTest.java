package com.labelhub.api.module.schema.util;

import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldOption;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.generated.model.SchemaFieldValidation;
import com.labelhub.api.generated.model.LinkageAtomicCondition;
import com.labelhub.api.generated.model.LinkageConditionGroup;
import com.labelhub.api.generated.model.LinkageConditionOp;
import com.labelhub.api.generated.model.SchemaTab;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import java.util.ArrayList;
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
    void validate_rejects_tab_container_without_tabs() {
        SchemaField field = field("tabs", SchemaFieldType.TAB_CONTAINER);
        field.setTabs(List.of());

        assertThatThrownBy(() -> validator.validate(document(field)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField()).isEqualTo("fields[0].tabs"));
    }

    @Test
    void validate_indexes_tab_children_for_duplicate_and_linkage_checks() {
        SchemaField tabs = field("tabs", SchemaFieldType.TAB_CONTAINER);
        tabs.getTabs().get(0).setChildren(List.of(field("driver", SchemaFieldType.TEXT)));
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(atomic("driver", LinkageConditionOp.NOTEMPTY, null));
        tabs.getTabs().add(tab("second", "Second", List.of(details)));

        validator.validate(document(tabs));

        tabs.getTabs().get(1).getChildren().get(0).setStableId("driver");
        assertThatThrownBy(() -> validator.validate(document(tabs)))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> assertThat(((InvalidSchemaDocumentException) error).getField())
                        .isEqualTo("fields[0].tabs[1].children[0].stableId"));
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

    @Test
    void validate_accepts_valid_linkage_conditions() {
        SchemaField driver = field("type", SchemaFieldType.SINGLE_SELECT);
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(groupAnyOf(atomic("type", LinkageConditionOp.EQ, "other")));
        details.setRequiredWhen(atomic("type", LinkageConditionOp.IN, List.of("other", "manual")));

        validator.validate(document(driver, details));
    }

    @Test
    void validate_rejects_linkage_reference_to_missing_field() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(atomic("missing", LinkageConditionOp.EQ, "x"));

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen.field",
                "联动条件引用的字段不存在");
    }

    @Test
    void validate_rejects_linkage_self_reference() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setRequiredWhen(atomic("details", LinkageConditionOp.NOTEMPTY, null));

        assertInvalid(document(details),
                "fields[0].requiredWhen.field",
                "联动条件不能引用自身");
    }

    @Test
    void validate_rejects_linkage_cycle() {
        SchemaField a = field("a", SchemaFieldType.TEXT);
        SchemaField b = field("b", SchemaFieldType.TEXT);
        a.setVisibleWhen(atomic("b", LinkageConditionOp.EQ, "yes"));
        b.setRequiredWhen(atomic("a", LinkageConditionOp.EQ, "yes"));

        assertInvalid(document(a, b),
                "fields[0].visibleWhen",
                "联动条件存在循环依赖");
    }

    @Test
    void validate_rejects_empty_linkage_group() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(groupAnyOf(List.of()));

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen.anyOf",
                "联动条件分组至少需要一个条件");
    }

    @Test
    void validate_rejects_linkage_group_with_both_allOf_and_anyOf() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        LinkageConditionGroup group = new LinkageConditionGroup();
        group.setAllOf(new ArrayList<>(List.of(atomic("type", LinkageConditionOp.EQ, "a"))));
        group.setAnyOf(new ArrayList<>(List.of(atomic("type", LinkageConditionOp.EQ, "b"))));
        details.setVisibleWhen(group);

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen",
                "联动条件分组必须且只能设置 allOf 或 anyOf");
    }

    @Test
    void validate_rejects_linkage_atomic_condition_without_field_or_op() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(atomic("", null, "x"));

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen",
                "联动条件必须包含 field 和 op");
    }

    @Test
    void validate_rejects_empty_operator_with_value() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(atomic("type", LinkageConditionOp.EMPTY, "x"));

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen.value",
                "empty/notEmpty 不应设置 value");
    }

    @Test
    void validate_rejects_scalar_operator_without_scalar_value() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(atomic("type", LinkageConditionOp.EQ, List.of("x")));

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen.value",
                "联动操作符需要标量 value");
    }

    @Test
    void validate_rejects_membership_operator_without_array_value() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(atomic("type", LinkageConditionOp.IN, "x"));

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen.value",
                "联动操作符需要数组 value");
    }

    @Test
    void validate_rejects_numeric_comparison_against_non_number_field() {
        SchemaField details = field("details", SchemaFieldType.TEXT);
        details.setVisibleWhen(atomic("type", LinkageConditionOp.GT, 5));

        assertInvalid(document(field("type", SchemaFieldType.TEXT), details),
                "fields[1].visibleWhen.field",
                "数值比较只能引用数字字段");
    }

    @Test
    void validate_rejects_unknown_custom_validation_function() {
        SchemaField field = field("title", SchemaFieldType.TEXT);
        SchemaFieldValidation validation = new SchemaFieldValidation();
        validation.setCustomFunction("customJs");
        field.setValidation(validation);

        assertInvalid(document(field),
                "fields[0].validation.customFunction",
                "未知自定义校验函数");
    }

    @Test
    void validate_accepts_textarea_custom_validation_like_text() {
        SchemaField field = field("long_reason", SchemaFieldType.valueOf("TEXTAREA"));
        SchemaFieldValidation validation = new SchemaFieldValidation();
        validation.setMinLength(10);
        validation.setMaxLength(500);
        validation.setPattern("^[\\s\\S]+$");
        validation.setCustomFunction("httpsUrl");
        field.setValidation(validation);

        validator.validate(document(field));
    }

    @Test
    void validate_rejects_custom_validation_function_for_incompatible_type() {
        SchemaField field = field("score", SchemaFieldType.NUMBER);
        SchemaFieldValidation validation = new SchemaFieldValidation();
        validation.setCustomFunction("httpsUrl");
        field.setValidation(validation);

        assertInvalid(document(field),
                "fields[0].validation.customFunction",
                "自定义校验函数不适用于该字段类型");
    }

    private static SchemaDocument document(SchemaField... fields) {
        SchemaDocument document = new SchemaDocument();
        document.setFields(List.of(fields));
        return document;
    }

    private static LinkageAtomicCondition atomic(String field, LinkageConditionOp op, Object value) {
        LinkageAtomicCondition condition = new LinkageAtomicCondition();
        condition.setField(field);
        condition.setOp(op);
        condition.setValue(value);
        return condition;
    }

    private static LinkageConditionGroup groupAnyOf(LinkageAtomicCondition condition) {
        return groupAnyOf(List.of(condition));
    }

    private static LinkageConditionGroup groupAnyOf(List<LinkageAtomicCondition> conditions) {
        LinkageConditionGroup group = new LinkageConditionGroup();
        group.setAnyOf(new ArrayList<>(conditions));
        return group;
    }

    private void assertInvalid(SchemaDocument document, String fieldPath, String reason) {
        assertThatThrownBy(() -> validator.validate(document))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .satisfies(error -> {
                    InvalidSchemaDocumentException exception = (InvalidSchemaDocumentException) error;
                    assertThat(exception.getField()).isEqualTo(fieldPath);
                    assertThat(exception.getReason()).isEqualTo(reason);
                });
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
        if (type == SchemaFieldType.TAB_CONTAINER) {
            field.setTabs(new ArrayList<>(List.of(tab(stableId + "-tab", "Tab", List.of(field(stableId + "-child", SchemaFieldType.TEXT))))));
        }
        return field;
    }

    private static SchemaTab tab(String stableId, String label, List<SchemaField> children) {
        SchemaTab tab = new SchemaTab();
        tab.setStableId(stableId);
        tab.setLabel(label);
        tab.setChildren(new ArrayList<>(children));
        return tab;
    }
}
