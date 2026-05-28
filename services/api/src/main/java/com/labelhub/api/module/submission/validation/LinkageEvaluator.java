package com.labelhub.api.module.submission.validation;

import com.labelhub.api.generated.model.LinkageAtomicCondition;
import com.labelhub.api.generated.model.LinkageCondition;
import com.labelhub.api.generated.model.LinkageConditionGroup;
import com.labelhub.api.generated.model.LinkageConditionOp;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.springframework.stereotype.Component;

@Component
public class LinkageEvaluator {

    public boolean evaluate(LinkageCondition condition, Map<String, Object> flatValues) {
        if (condition instanceof LinkageAtomicCondition atomic) {
            return evaluateAtomic(atomic, flatValues == null ? Map.of() : flatValues);
        }
        if (condition instanceof LinkageConditionGroup group) {
            return evaluateGroup(group, flatValues == null ? Map.of() : flatValues);
        }
        return false;
    }

    private boolean evaluateGroup(LinkageConditionGroup group, Map<String, Object> flatValues) {
        List<LinkageAtomicCondition> allOf = group.getAllOf();
        if (allOf != null && !allOf.isEmpty()) {
            return allOf.stream().allMatch(condition -> evaluateAtomic(condition, flatValues));
        }

        List<LinkageAtomicCondition> anyOf = group.getAnyOf();
        if (anyOf != null && !anyOf.isEmpty()) {
            return anyOf.stream().anyMatch(condition -> evaluateAtomic(condition, flatValues));
        }

        return false;
    }

    private boolean evaluateAtomic(LinkageAtomicCondition condition, Map<String, Object> flatValues) {
        if (condition == null || condition.getField() == null || condition.getOp() == null) {
            return false;
        }

        Object fieldValue = flatValues.get(condition.getField());
        LinkageConditionOp op = condition.getOp();
        if (op == LinkageConditionOp.EMPTY) {
            return isEmpty(fieldValue);
        }
        if (op == LinkageConditionOp.NOTEMPTY) {
            return !isEmpty(fieldValue);
        }
        if (isEmpty(fieldValue)) {
            return false;
        }

        return switch (op) {
            case EQ -> compareEquality(fieldValue, condition.getValue(), false);
            case NEQ -> compareEquality(fieldValue, condition.getValue(), true);
            case IN -> matchesAny(fieldValue, condition.getValue());
            case NOTIN -> matchesNone(fieldValue, condition.getValue());
            case GT -> compareNumbers(fieldValue, condition.getValue(), comparison -> comparison > 0);
            case GTE -> compareNumbers(fieldValue, condition.getValue(), comparison -> comparison >= 0);
            case LT -> compareNumbers(fieldValue, condition.getValue(), comparison -> comparison < 0);
            case LTE -> compareNumbers(fieldValue, condition.getValue(), comparison -> comparison <= 0);
            case EMPTY, NOTEMPTY -> false;
        };
    }

    private boolean compareEquality(Object fieldValue, Object conditionValue, boolean negate) {
        if (!isScalarLike(fieldValue) || !isScalarLike(conditionValue)) {
            return false;
        }
        boolean equal = valuesEqual(fieldValue, conditionValue);
        return negate ? !equal : equal;
    }

    private boolean matchesAny(Object fieldValue, Object conditionValue) {
        List<?> conditionItems = asScalarList(conditionValue);
        if (conditionItems == null) {
            return false;
        }

        if (fieldValue instanceof Collection<?> selectedItems) {
            return selectedItems.stream()
                    .anyMatch(selected -> conditionItems.stream().anyMatch(item -> valuesEqual(selected, item)));
        }

        return isScalarLike(fieldValue) && conditionItems.stream().anyMatch(item -> valuesEqual(fieldValue, item));
    }

    private boolean matchesNone(Object fieldValue, Object conditionValue) {
        List<?> conditionItems = asScalarList(conditionValue);
        if (conditionItems == null) {
            return false;
        }

        if (fieldValue instanceof Collection<?> selectedItems) {
            return selectedItems.stream()
                    .noneMatch(selected -> conditionItems.stream().anyMatch(item -> valuesEqual(selected, item)));
        }

        return isScalarLike(fieldValue) && conditionItems.stream().noneMatch(item -> valuesEqual(fieldValue, item));
    }

    private boolean compareNumbers(Object fieldValue, Object conditionValue, IntPredicate predicate) {
        BigDecimal left = asBigDecimal(fieldValue);
        BigDecimal right = asBigDecimal(conditionValue);
        return left != null && right != null && predicate.test(left.compareTo(right));
    }

    private boolean valuesEqual(Object left, Object right) {
        BigDecimal leftNumber = asBigDecimal(left);
        BigDecimal rightNumber = asBigDecimal(right);
        if (leftNumber != null || rightNumber != null) {
            return leftNumber != null && rightNumber != null && leftNumber.compareTo(rightNumber) == 0;
        }
        return left instanceof String && right instanceof String && left.equals(right)
                || left instanceof Boolean && right instanceof Boolean && left.equals(right);
    }

    private boolean isScalarLike(Object value) {
        return value instanceof String || value instanceof Boolean || asBigDecimal(value) != null;
    }

    private boolean isEmpty(Object value) {
        return value == null
                || value instanceof String text && text.isEmpty()
                || value instanceof Collection<?> collection && collection.isEmpty()
                || value instanceof Map<?, ?> map && map.isEmpty();
    }

    private List<?> asScalarList(Object value) {
        List<?> items = asList(value);
        if (items == null || items.stream().anyMatch(item -> !isScalarLike(item))) {
            return null;
        }
        return items;
    }

    private List<?> asList(Object value) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(Array.get(value, i));
            }
            return items;
        }
        return null;
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
}
