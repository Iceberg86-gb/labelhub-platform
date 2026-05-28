package com.labelhub.api.module.submission.validation;

import com.labelhub.api.generated.model.LinkageAtomicCondition;
import com.labelhub.api.generated.model.LinkageConditionOp;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldOption;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.generated.model.SchemaFieldValidation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPayloadValidatorLinkageTest {

    private final AnswerPayloadValidator validator = new AnswerPayloadValidator();

    @Test
    void hidden_static_required_field_skips_required_validation() {
        SchemaField driver = text("type");
        SchemaField details = requiredText("details");
        details.setVisibleWhen(atomic("type", LinkageConditionOp.EQ, "other"));

        assertThat(validate(document(driver, details), Map.of("type", "standard")))
                .isEmpty();
    }

    @Test
    void visible_static_required_field_keeps_existing_required_message() {
        SchemaField driver = text("type");
        SchemaField details = requiredText("details");
        details.setVisibleWhen(atomic("type", LinkageConditionOp.EQ, "other"));

        assertThat(validate(document(driver, details), Map.of("type", "other")))
                .containsExactly(new ExpectedError("details", "此字段必填"));
    }

    @Test
    void required_when_true_makes_optional_field_required() {
        SchemaField driver = text("type");
        SchemaField details = text("details");
        details.setRequiredWhen(atomic("type", LinkageConditionOp.EQ, "other"));

        assertThat(validate(document(driver, details), Map.of("type", "other")))
                .containsExactly(new ExpectedError("details", "此字段必填"));
    }

    @Test
    void required_when_false_keeps_optional_field_optional() {
        SchemaField driver = text("type");
        SchemaField details = text("details");
        details.setRequiredWhen(atomic("type", LinkageConditionOp.EQ, "other"));

        assertThat(validate(document(driver, details), Map.of("type", "standard")))
                .isEmpty();
    }

    @Test
    void hidden_field_skips_type_and_rule_validation() {
        SchemaField driver = text("type");
        SchemaField score = number("score");
        SchemaFieldValidation validation = new SchemaFieldValidation();
        validation.setMin(BigDecimal.TEN);
        score.setValidation(validation);
        score.setVisibleWhen(atomic("type", LinkageConditionOp.EQ, "other"));

        assertThat(validate(document(driver, score), Map.of("type", "standard", "score", "not-a-number")))
                .isEmpty();
    }

    @Test
    void hidden_nested_parent_skips_child_required_validation() {
        SchemaField driver = text("type");
        SchemaField parent = nested("profile", requiredText("city"));
        parent.setVisibleWhen(atomic("type", LinkageConditionOp.EQ, "other"));

        assertThat(validate(document(driver, parent), Map.of("type", "standard", "profile", Map.of())))
                .isEmpty();
    }

    @Test
    void nested_child_required_when_can_reference_top_level_flat_stable_id() {
        SchemaField driver = text("type");
        SchemaField city = text("city");
        city.setRequiredWhen(atomic("type", LinkageConditionOp.EQ, "other"));
        SchemaField note = text("note");
        SchemaField parent = nested("profile", city, note);

        assertThat(validate(document(driver, parent), Map.of("type", "other", "profile", Map.of("note", "present"))))
                .containsExactly(new ExpectedError("city", "此字段必填"));
    }

    @Test
    void nested_child_visibility_can_reference_sibling_flat_stable_id() {
        SchemaField kind = text("kind");
        SchemaField details = requiredText("details");
        details.setVisibleWhen(atomic("kind", LinkageConditionOp.EQ, "other"));
        SchemaField parent = nested("profile", kind, details);

        assertThat(validate(document(parent), Map.of("profile", Map.of("kind", "standard"))))
                .isEmpty();
    }

    @Test
    void schema_without_linkage_preserves_p3a_validation_behavior() {
        SchemaField title = requiredText("title");
        SchemaField score = number("score");
        SchemaField choice = singleSelect("choice");

        assertThat(validate(document(title, score, choice), Map.of(
                "title", "",
                "score", "bad",
                "choice", "missing")))
                .containsExactly(
                        new ExpectedError("title", "此字段必填"),
                        new ExpectedError("score", "必须是数字"),
                        new ExpectedError("choice", "请从选项中选择"));
    }

    private List<ExpectedError> validate(SchemaDocument schema, Map<String, Object> payload) {
        return validator.validate(schema, payload).stream()
                .map(error -> new ExpectedError(error.stableId(), error.reason()))
                .toList();
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

    private static SchemaField text(String stableId) {
        return field(stableId, SchemaFieldType.TEXT);
    }

    private static SchemaField requiredText(String stableId) {
        SchemaField field = text(stableId);
        SchemaFieldValidation validation = new SchemaFieldValidation();
        validation.setRequired(true);
        field.setValidation(validation);
        return field;
    }

    private static SchemaField number(String stableId) {
        return field(stableId, SchemaFieldType.NUMBER);
    }

    private static SchemaField singleSelect(String stableId) {
        SchemaField field = field(stableId, SchemaFieldType.SINGLE_SELECT);
        SchemaFieldOption option = new SchemaFieldOption();
        option.setLabel("Allowed");
        option.setValue("allowed");
        field.setOptions(List.of(option));
        return field;
    }

    private static SchemaField nested(String stableId, SchemaField... children) {
        SchemaField field = field(stableId, SchemaFieldType.NESTED_OBJECT);
        field.setChildren(List.of(children));
        return field;
    }

    private static SchemaField field(String stableId, SchemaFieldType type) {
        SchemaField field = new SchemaField();
        field.setStableId(stableId);
        field.setLabel(stableId);
        field.setType(type);
        return field;
    }

    private record ExpectedError(String stableId, String reason) {
    }
}
