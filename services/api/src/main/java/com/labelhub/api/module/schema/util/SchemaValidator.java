package com.labelhub.api.module.schema.util;

import com.labelhub.api.generated.model.LinkageAtomicCondition;
import com.labelhub.api.generated.model.LinkageCondition;
import com.labelhub.api.generated.model.LinkageConditionGroup;
import com.labelhub.api.generated.model.LinkageConditionOp;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.generated.model.SchemaTab;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SchemaValidator {
    private static final String MISSING_REFERENCE = "联动条件引用的字段不存在";
    private static final String SELF_REFERENCE = "联动条件不能引用自身";
    private static final String CYCLE = "联动条件存在循环依赖";
    private static final String EMPTY_GROUP = "联动条件分组至少需要一个条件";
    private static final String GROUP_SHAPE = "联动条件分组必须且只能设置 allOf 或 anyOf";
    private static final String ATOMIC_SHAPE = "联动条件必须包含 field 和 op";
    private static final String EMPTY_VALUE = "empty/notEmpty 不应设置 value";
    private static final String SCALAR_VALUE = "联动操作符需要标量 value";
    private static final String ARRAY_VALUE = "联动操作符需要数组 value";
    private static final String NUMERIC_FIELD = "数值比较只能引用数字字段";

    private static final Set<LinkageConditionOp> EMPTY_OPS = EnumSet.of(
            LinkageConditionOp.EMPTY,
            LinkageConditionOp.NOTEMPTY);
    private static final Set<LinkageConditionOp> ARRAY_OPS = EnumSet.of(
            LinkageConditionOp.IN,
            LinkageConditionOp.NOTIN);
    private static final Set<LinkageConditionOp> NUMERIC_OPS = EnumSet.of(
            LinkageConditionOp.GT,
            LinkageConditionOp.GTE,
            LinkageConditionOp.LT,
            LinkageConditionOp.LTE);

    public void validate(SchemaDocument document) {
        if (document == null || document.getFields() == null || document.getFields().isEmpty()) {
            throw new InvalidSchemaDocumentException("fields", "schema must have at least one field");
        }

        validateFields(document.getFields(), new HashSet<>(), "fields");
        Map<String, FieldContext> fieldIndex = new LinkedHashMap<>();
        indexFields(document.getFields(), "fields", fieldIndex);
        validateLinkage(document.getFields(), "fields", fieldIndex);
        validateAcyclicLinkage(fieldIndex);
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
            if (field.getType() == SchemaFieldType.TAB_CONTAINER) {
                if (field.getTabs() == null || field.getTabs().isEmpty()) {
                    throw new InvalidSchemaDocumentException(fieldPath + ".tabs",
                            "tab_container must have at least one tab");
                }
                validateTabs(field.getTabs(), seenStableIds, fieldPath + ".tabs");
            }
        }
    }

    private void validateTabs(List<SchemaTab> tabs, Set<String> seenStableIds, String pathPrefix) {
        for (int i = 0; i < tabs.size(); i++) {
            SchemaTab tab = tabs.get(i);
            String tabPath = pathPrefix + "[" + i + "]";
            if (tab.getStableId() == null || tab.getStableId().isBlank()) {
                throw new InvalidSchemaDocumentException(tabPath + ".stableId", "stableId is required");
            }
            if (!seenStableIds.add(tab.getStableId())) {
                throw new InvalidSchemaDocumentException(tabPath + ".stableId",
                        "duplicate stableId: " + tab.getStableId());
            }
            if (tab.getLabel() == null || tab.getLabel().isBlank()) {
                throw new InvalidSchemaDocumentException(tabPath + ".label", "label is required");
            }
            if (tab.getChildren() == null || tab.getChildren().isEmpty()) {
                throw new InvalidSchemaDocumentException(tabPath + ".children",
                        "tab must have at least one child field");
            }
            for (int childIndex = 0; childIndex < tab.getChildren().size(); childIndex++) {
                if (tab.getChildren().get(childIndex).getType() == SchemaFieldType.TAB_CONTAINER) {
                    throw new InvalidSchemaDocumentException(tabPath + ".children[" + childIndex + "].type",
                            "tab_container cannot be nested inside tab_container");
                }
            }
            validateFields(tab.getChildren(), seenStableIds, tabPath + ".children");
        }
    }

    private void indexFields(List<SchemaField> fields, String pathPrefix, Map<String, FieldContext> fieldIndex) {
        for (int i = 0; i < fields.size(); i++) {
            SchemaField field = fields.get(i);
            String fieldPath = pathPrefix + "[" + i + "]";
            fieldIndex.put(field.getStableId(), new FieldContext(field, fieldPath));

            if (field.getChildren() != null && !field.getChildren().isEmpty()) {
                indexFields(field.getChildren(), fieldPath + ".children", fieldIndex);
            }
            if (field.getTabs() != null && !field.getTabs().isEmpty()) {
                for (int tabIndex = 0; tabIndex < field.getTabs().size(); tabIndex++) {
                    SchemaTab tab = field.getTabs().get(tabIndex);
                    indexFields(tab.getChildren(), fieldPath + ".tabs[" + tabIndex + "].children", fieldIndex);
                }
            }
        }
    }

    private void validateLinkage(List<SchemaField> fields, String pathPrefix, Map<String, FieldContext> fieldIndex) {
        for (int i = 0; i < fields.size(); i++) {
            SchemaField field = fields.get(i);
            String fieldPath = pathPrefix + "[" + i + "]";
            validateCondition(field, field.getVisibleWhen(), fieldPath + ".visibleWhen", fieldIndex);
            validateCondition(field, field.getRequiredWhen(), fieldPath + ".requiredWhen", fieldIndex);

            if (field.getChildren() != null && !field.getChildren().isEmpty()) {
                validateLinkage(field.getChildren(), fieldPath + ".children", fieldIndex);
            }
            if (field.getTabs() != null && !field.getTabs().isEmpty()) {
                for (int tabIndex = 0; tabIndex < field.getTabs().size(); tabIndex++) {
                    SchemaTab tab = field.getTabs().get(tabIndex);
                    validateLinkage(tab.getChildren(), fieldPath + ".tabs[" + tabIndex + "].children", fieldIndex);
                }
            }
        }
    }

    private void validateCondition(
            SchemaField owner,
            LinkageCondition condition,
            String conditionPath,
            Map<String, FieldContext> fieldIndex) {
        if (condition == null) {
            return;
        }

        if (condition instanceof LinkageAtomicCondition atomic) {
            validateAtomicCondition(owner, atomic, conditionPath, fieldIndex);
            return;
        }

        if (condition instanceof LinkageConditionGroup group) {
            List<LinkageAtomicCondition> allOf = group.getAllOf();
            List<LinkageAtomicCondition> anyOf = group.getAnyOf();
            boolean hasAllOf = allOf != null && !allOf.isEmpty();
            boolean hasAnyOf = anyOf != null && !anyOf.isEmpty();
            if (hasAllOf && hasAnyOf) {
                throw new InvalidSchemaDocumentException(conditionPath, GROUP_SHAPE);
            }
            if (!hasAllOf && !hasAnyOf) {
                throw new InvalidSchemaDocumentException(conditionPath + ".anyOf", EMPTY_GROUP);
            }

            List<LinkageAtomicCondition> conditions = hasAllOf ? allOf : anyOf;
            String groupPath = conditionPath + (hasAllOf ? ".allOf" : ".anyOf");
            for (int i = 0; i < conditions.size(); i++) {
                validateAtomicCondition(owner, conditions.get(i), groupPath + "[" + i + "]", fieldIndex);
            }
            return;
        }

        throw new InvalidSchemaDocumentException(conditionPath, ATOMIC_SHAPE);
    }

    private void validateAtomicCondition(
            SchemaField owner,
            LinkageAtomicCondition condition,
            String conditionPath,
            Map<String, FieldContext> fieldIndex) {
        if (condition == null
                || condition.getField() == null
                || condition.getField().isBlank()
                || condition.getOp() == null) {
            throw new InvalidSchemaDocumentException(conditionPath, ATOMIC_SHAPE);
        }

        String referencedStableId = condition.getField();
        if (referencedStableId.equals(owner.getStableId())) {
            throw new InvalidSchemaDocumentException(conditionPath + ".field", SELF_REFERENCE);
        }

        FieldContext referenced = fieldIndex.get(referencedStableId);
        if (referenced == null) {
            throw new InvalidSchemaDocumentException(conditionPath + ".field", MISSING_REFERENCE);
        }

        LinkageConditionOp op = condition.getOp();
        Object value = condition.getValue();
        if (EMPTY_OPS.contains(op)) {
            if (value != null) {
                throw new InvalidSchemaDocumentException(conditionPath + ".value", EMPTY_VALUE);
            }
            return;
        }

        if (ARRAY_OPS.contains(op)) {
            if (!isArrayValue(value)) {
                throw new InvalidSchemaDocumentException(conditionPath + ".value", ARRAY_VALUE);
            }
            return;
        }

        if (!isScalarValue(value)) {
            throw new InvalidSchemaDocumentException(conditionPath + ".value", SCALAR_VALUE);
        }
        if (NUMERIC_OPS.contains(op) && referenced.field().getType() != SchemaFieldType.NUMBER) {
            throw new InvalidSchemaDocumentException(conditionPath + ".field", NUMERIC_FIELD);
        }
    }

    private boolean isScalarValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private boolean isArrayValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().allMatch(this::isScalarValue);
        }
        if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                if (!isScalarValue(Array.get(value, i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void validateAcyclicLinkage(Map<String, FieldContext> fieldIndex) {
        Map<String, VisitState> states = new HashMap<>();
        for (String stableId : fieldIndex.keySet()) {
            detectCycle(stableId, fieldIndex, states, firstConditionPath(fieldIndex.get(stableId)));
        }
    }

    private void detectCycle(
            String stableId,
            Map<String, FieldContext> fieldIndex,
            Map<String, VisitState> states,
            String rootConditionPath) {
        VisitState state = states.get(stableId);
        if (state == VisitState.VISITING) {
            throw new InvalidSchemaDocumentException(rootConditionPath, CYCLE);
        }
        if (state == VisitState.VISITED) {
            return;
        }

        FieldContext context = fieldIndex.get(stableId);
        if (context == null) {
            return;
        }

        states.put(stableId, VisitState.VISITING);
        for (String dependency : referencedStableIds(context.field())) {
            detectCycle(dependency, fieldIndex, states, rootConditionPath);
        }
        states.put(stableId, VisitState.VISITED);
    }

    private Set<String> referencedStableIds(SchemaField field) {
        Set<String> refs = new HashSet<>();
        collectReferencedStableIds(field.getVisibleWhen(), refs);
        collectReferencedStableIds(field.getRequiredWhen(), refs);
        return refs;
    }

    private void collectReferencedStableIds(LinkageCondition condition, Set<String> refs) {
        if (condition instanceof LinkageAtomicCondition atomic) {
            if (atomic.getField() != null && !atomic.getField().isBlank()) {
                refs.add(atomic.getField());
            }
            return;
        }
        if (condition instanceof LinkageConditionGroup group) {
            if (group.getAllOf() != null) {
                group.getAllOf().forEach(atomic -> collectReferencedStableIds(atomic, refs));
            }
            if (group.getAnyOf() != null) {
                group.getAnyOf().forEach(atomic -> collectReferencedStableIds(atomic, refs));
            }
        }
    }

    private String firstConditionPath(FieldContext context) {
        if (context == null) {
            return "fields";
        }
        if (context.field().getVisibleWhen() != null) {
            return context.path() + ".visibleWhen";
        }
        return context.path() + ".requiredWhen";
    }

    private record FieldContext(SchemaField field, String path) {}

    private enum VisitState {
        VISITING,
        VISITED
    }
}
