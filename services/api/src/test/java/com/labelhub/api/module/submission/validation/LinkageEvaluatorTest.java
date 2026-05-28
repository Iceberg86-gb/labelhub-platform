package com.labelhub.api.module.submission.validation;

import com.labelhub.api.generated.model.LinkageAtomicCondition;
import com.labelhub.api.generated.model.LinkageCondition;
import com.labelhub.api.generated.model.LinkageConditionGroup;
import com.labelhub.api.generated.model.LinkageConditionOp;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkageEvaluatorTest {

    private final LinkageEvaluator evaluator = new LinkageEvaluator();

    @Test
    void evaluates_scalar_equality_without_cross_type_coercion() {
        Map<String, Object> values = Map.of(
                "textNumber", "1",
                "number", 1,
                "bool", true);

        assertThat(evaluator.evaluate(atomic("textNumber", LinkageConditionOp.EQ, "1"), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("number", LinkageConditionOp.EQ, 1.0d), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("textNumber", LinkageConditionOp.EQ, 1), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("bool", LinkageConditionOp.NEQ, false), values)).isTrue();
    }

    @Test
    void treats_empty_driver_values_as_false_for_non_empty_ops() {
        Map<String, Object> values = Map.of(
                "blank", "",
                "emptyList", List.of());

        assertThat(evaluator.evaluate(atomic("missing", LinkageConditionOp.EQ, "x"), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("missing", LinkageConditionOp.NEQ, "x"), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("blank", LinkageConditionOp.IN, List.of("x")), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("emptyList", LinkageConditionOp.NOTIN, List.of("x")), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("missing", LinkageConditionOp.GT, 1), values)).isFalse();
    }

    @Test
    void reuses_p3a_empty_semantics() {
        Map<String, Object> values = Map.of(
                "blank", "",
                "spaces", "   ",
                "emptyList", List.of(),
                "emptyMap", Map.of(),
                "text", "x");

        assertThat(evaluator.evaluate(atomic("missing", LinkageConditionOp.EMPTY, null), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("blank", LinkageConditionOp.EMPTY, null), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("emptyList", LinkageConditionOp.EMPTY, null), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("emptyMap", LinkageConditionOp.EMPTY, null), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("spaces", LinkageConditionOp.EMPTY, null), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("text", LinkageConditionOp.NOTEMPTY, null), values)).isTrue();
    }

    @Test
    void evaluates_membership_for_scalars_and_collections() {
        Map<String, Object> values = Map.of(
                "single", "manual",
                "multi", List.of("alpha", "beta"));

        assertThat(evaluator.evaluate(atomic("single", LinkageConditionOp.IN, List.of("manual", "other")), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("single", LinkageConditionOp.NOTIN, List.of("other")), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("multi", LinkageConditionOp.IN, List.of("beta", "gamma")), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("multi", LinkageConditionOp.NOTIN, List.of("gamma")), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("multi", LinkageConditionOp.NOTIN, List.of("alpha")), values)).isFalse();
    }

    @Test
    void evaluates_numeric_comparisons_with_big_decimal_semantics() {
        Map<String, Object> values = Map.of(
                "count", 10,
                "decimal", new BigDecimal("1.50"),
                "text", "10");

        assertThat(evaluator.evaluate(atomic("count", LinkageConditionOp.GT, 5), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("count", LinkageConditionOp.GTE, 10.0d), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("decimal", LinkageConditionOp.LT, 2), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("decimal", LinkageConditionOp.LTE, new BigDecimal("1.5")), values)).isTrue();
        assertThat(evaluator.evaluate(atomic("text", LinkageConditionOp.GT, 5), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("text", LinkageConditionOp.LT, 5), values)).isFalse();
        assertThat(evaluator.evaluate(atomic("count", LinkageConditionOp.GT, "5"), values)).isFalse();
    }

    @Test
    void evaluates_one_level_all_of_and_any_of_groups() {
        Map<String, Object> values = Map.of("type", "other", "score", 7);

        assertThat(evaluator.evaluate(groupAllOf(
                atomic("type", LinkageConditionOp.EQ, "other"),
                atomic("score", LinkageConditionOp.GTE, 5)), values)).isTrue();
        assertThat(evaluator.evaluate(groupAllOf(
                atomic("type", LinkageConditionOp.EQ, "other"),
                atomic("score", LinkageConditionOp.LT, 5)), values)).isFalse();
        assertThat(evaluator.evaluate(groupAnyOf(
                atomic("type", LinkageConditionOp.EQ, "manual"),
                atomic("score", LinkageConditionOp.GTE, 5)), values)).isTrue();
    }

    @Test
    void returns_false_for_null_or_malformed_conditions() {
        assertThat(evaluator.evaluate(null, Map.of("field", "value"))).isFalse();
        assertThat(evaluator.evaluate(new UnknownCondition(), Map.of("field", "value"))).isFalse();

        LinkageAtomicCondition missingOp = new LinkageAtomicCondition();
        missingOp.setField("field");

        assertThat(evaluator.evaluate(missingOp, Map.of("field", "value"))).isFalse();
        assertThat(evaluator.evaluate(atomic("field", LinkageConditionOp.NEQ, null), Map.of("field", "value"))).isFalse();
        assertThat(evaluator.evaluate(atomic("field", LinkageConditionOp.NOTIN, "not-array"), Map.of("field", "value"))).isFalse();
        assertThat(evaluator.evaluate(atomic("object", LinkageConditionOp.NEQ, "value"), Map.of("object", Map.of("nested", "value")))).isFalse();
    }

    private static LinkageAtomicCondition atomic(String field, LinkageConditionOp op, Object value) {
        LinkageAtomicCondition condition = new LinkageAtomicCondition();
        condition.setField(field);
        condition.setOp(op);
        condition.setValue(value);
        return condition;
    }

    private static LinkageConditionGroup groupAllOf(LinkageAtomicCondition... conditions) {
        LinkageConditionGroup group = new LinkageConditionGroup();
        group.setAllOf(List.of(conditions));
        group.setAnyOf(null);
        return group;
    }

    private static LinkageConditionGroup groupAnyOf(LinkageAtomicCondition... conditions) {
        LinkageConditionGroup group = new LinkageConditionGroup();
        group.setAllOf(null);
        group.setAnyOf(List.of(conditions));
        return group;
    }

    private static final class UnknownCondition implements LinkageCondition {
    }
}
