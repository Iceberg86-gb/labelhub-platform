package com.labelhub.api.module.submission.validation;

import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldOption;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.generated.model.SchemaFieldValidation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AnswerPayloadValidator {

    private static final String REQUIRED = "此字段必填";
    private static final String TEXT_REQUIRED = "必须是文本";
    private static final String NUMBER_REQUIRED = "必须是数字";
    private static final String SELECT_REQUIRED = "请从选项中选择";
    private static final String OBJECT_REQUIRED = "必须是对象";
    private static final String FORMAT_INVALID = "格式不正确";
    private static final String REGEX_INVALID = "正则表达式无效";

    private final LinkageEvaluator linkageEvaluator;

    public AnswerPayloadValidator() {
        this(new LinkageEvaluator());
    }

    @Autowired
    public AnswerPayloadValidator(LinkageEvaluator linkageEvaluator) {
        this.linkageEvaluator = linkageEvaluator;
    }

    public List<AnswerValidationError> validate(SchemaDocument schema, Map<String, Object> answerPayload) {
        List<SchemaField> fields = schema == null || schema.getFields() == null ? List.of() : schema.getFields();
        Map<String, Object> payload = answerPayload == null ? Map.of() : answerPayload;
        Map<String, Object> flatValues = buildFlatValueIndex(fields, payload);
        return fields.stream()
            .flatMap(field -> validateField(field, payload.get(field.getStableId()), flatValues).stream())
            .toList();
    }

    private List<AnswerValidationError> validateField(SchemaField field, Object value, Map<String, Object> flatValues) {
        if (field == null) {
            return List.of();
        }
        if (!isVisible(field, flatValues)) {
            return List.of();
        }
        SchemaFieldType type = field.getType();
        if (type == null || type == SchemaFieldType.SHOW_ITEM) {
            return List.of();
        }
        boolean required = isRequired(field) || isConditionallyRequired(field, flatValues);
        boolean empty = isEmpty(value);

        if (required && empty) {
            return List.of(error(field, REQUIRED));
        }
        if (empty) {
            return List.of();
        }

        return switch (type) {
            case TEXT, RICH_TEXT -> validateText(field, value);
            case NUMBER -> validateNumber(field, value);
            case SINGLE_SELECT -> validateSingleSelect(field, value);
            case MULTI_SELECT -> validateMultiSelect(field, value);
            case DATE -> validateStringShape(field, value);
            case FILE_UPLOAD -> validateFileUpload(field, value);
            case JSON_EDITOR -> List.of();
            case LLM_INTERACTION -> validateObjectShape(field, value);
            case SHOW_ITEM -> List.of();
            case NESTED_OBJECT -> validateNestedObject(field, value, flatValues);
        };
    }

    private List<AnswerValidationError> validateText(SchemaField field, Object value) {
        if (!(value instanceof String text)) {
            return List.of(error(field, TEXT_REQUIRED));
        }

        SchemaFieldValidation validation = field.getValidation();
        if (validation == null) {
            return List.of();
        }

        List<AnswerValidationError> errors = new ArrayList<>();
        if (validation.getMinLength() != null && text.length() < validation.getMinLength()) {
            errors.add(error(field, "最少 " + validation.getMinLength() + " 字"));
        }
        if (validation.getMaxLength() != null && text.length() > validation.getMaxLength()) {
            errors.add(error(field, "最多 " + validation.getMaxLength() + " 字"));
        }
        if (validation.getPattern() != null && !validation.getPattern().isEmpty()) {
            try {
                if (!Pattern.compile(validation.getPattern()).matcher(text).find()) {
                    errors.add(error(field, FORMAT_INVALID));
                }
            } catch (PatternSyntaxException exception) {
                errors.add(error(field, REGEX_INVALID));
            }
        }
        return errors;
    }

    private List<AnswerValidationError> validateNumber(SchemaField field, Object value) {
        BigDecimal number = asBigDecimal(value);
        if (number == null) {
            return List.of(error(field, NUMBER_REQUIRED));
        }

        SchemaFieldValidation validation = field.getValidation();
        if (validation == null) {
            return List.of();
        }

        List<AnswerValidationError> errors = new ArrayList<>();
        if (validation.getMin() != null && number.compareTo(validation.getMin()) < 0) {
            errors.add(error(field, "不能小于 " + formatNumber(validation.getMin())));
        }
        if (validation.getMax() != null && number.compareTo(validation.getMax()) > 0) {
            errors.add(error(field, "不能大于 " + formatNumber(validation.getMax())));
        }
        return errors;
    }

    private List<AnswerValidationError> validateSingleSelect(SchemaField field, Object value) {
        if (!(value instanceof String selected) || !optionValues(field).contains(selected)) {
            return List.of(error(field, SELECT_REQUIRED));
        }
        return List.of();
    }

    private List<AnswerValidationError> validateMultiSelect(SchemaField field, Object value) {
        if (!(value instanceof List<?> selected)
            || !selected.stream().allMatch(item -> item instanceof String option && optionValues(field).contains(option))) {
            return List.of(error(field, SELECT_REQUIRED));
        }
        return List.of();
    }

    private List<AnswerValidationError> validateStringShape(SchemaField field, Object value) {
        if (!(value instanceof String)) {
            return List.of(error(field, TEXT_REQUIRED));
        }
        return List.of();
    }

    private List<AnswerValidationError> validateObjectShape(SchemaField field, Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return List.of(error(field, OBJECT_REQUIRED));
        }
        return List.of();
    }

    private List<AnswerValidationError> validateFileUpload(SchemaField field, Object value) {
        if (!(value instanceof Map<?, ?> file)) {
            return List.of(error(field, OBJECT_REQUIRED));
        }
        if (blank(file.get("objectKey")) || blank(file.get("fileName")) || !positiveLong(file.get("sizeBytes"))) {
            return List.of(error(field, FORMAT_INVALID));
        }
        return List.of();
    }

    private List<AnswerValidationError> validateNestedObject(SchemaField field, Object value, Map<String, Object> flatValues) {
        if (!(value instanceof Map<?, ?> nested)) {
            return List.of(error(field, OBJECT_REQUIRED));
        }
        List<SchemaField> children = field.getChildren() == null ? List.of() : field.getChildren();
        return children.stream()
            .flatMap(child -> validateField(child, nested.get(child.getStableId()), flatValues).stream())
            .toList();
    }

    private Map<String, Object> buildFlatValueIndex(List<SchemaField> fields, Map<String, Object> payload) {
        Map<String, Object> values = new LinkedHashMap<>();
        indexFieldValues(fields, payload, values);
        return values;
    }

    private void indexFieldValues(List<SchemaField> fields, Map<?, ?> source, Map<String, Object> values) {
        for (SchemaField field : fields == null ? List.<SchemaField>of() : fields) {
            Object value = source == null ? null : source.get(field.getStableId());
            values.put(field.getStableId(), value);
            if (field.getType() == SchemaFieldType.NESTED_OBJECT) {
                Map<?, ?> nestedSource = value instanceof Map<?, ?> nested ? nested : null;
                indexFieldValues(field.getChildren(), nestedSource, values);
            }
        }
    }

    private boolean isRequired(SchemaField field) {
        return field.getValidation() != null && Boolean.TRUE.equals(field.getValidation().getRequired());
    }

    private boolean isVisible(SchemaField field, Map<String, Object> flatValues) {
        return field.getVisibleWhen() == null || linkageEvaluator.evaluate(field.getVisibleWhen(), flatValues);
    }

    private boolean isConditionallyRequired(SchemaField field, Map<String, Object> flatValues) {
        return field.getRequiredWhen() != null && linkageEvaluator.evaluate(field.getRequiredWhen(), flatValues);
    }

    private boolean isEmpty(Object value) {
        return value == null
            || value instanceof String text && text.isEmpty()
            || value instanceof Collection<?> collection && collection.isEmpty()
            || value instanceof Map<?, ?> map && map.isEmpty();
    }

    private boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private boolean positiveLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue() > 0;
        }
        try {
            return value != null && Long.parseLong(String.valueOf(value)) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private List<String> optionValues(SchemaField field) {
        List<SchemaFieldOption> options = field.getOptions() == null ? List.of() : field.getOptions();
        return options.stream()
            .map(SchemaFieldOption::getValue)
            .toList();
    }

    private AnswerValidationError error(SchemaField field, String reason) {
        return new AnswerValidationError(field.getStableId(), reason);
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Byte number) {
            return BigDecimal.valueOf(number.longValue());
        }
        if (value instanceof Short number) {
            return BigDecimal.valueOf(number.longValue());
        }
        if (value instanceof Integer number) {
            return BigDecimal.valueOf(number.longValue());
        }
        if (value instanceof Long number) {
            return BigDecimal.valueOf(number);
        }
        if (value instanceof Float number && Float.isFinite(number)) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            return BigDecimal.valueOf(number);
        }
        return null;
    }

    private String formatNumber(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }
}
