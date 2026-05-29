package com.labelhub.api.module.outbox.mapper;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventMapperContractTest {

    @Test
    void outbox_event_mapper_has_no_parent_interfaces() {
        assertThat(OutboxEventMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void outbox_event_mapper_exposes_only_insert_and_select_methods_for_api_side_enqueue() {
        for (Method method : OutboxEventMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates API outbox append-only contract")
                .doesNotStartWith("update")
                .doesNotStartWith("delete")
                .doesNotStartWith("remove")
                .doesNotStartWith("save");
            assertThat(name.startsWith("insert") || name.startsWith("select"))
                .as("Method " + name + " must be insert/select only")
                .isTrue();
        }
    }

    @Test
    void aggregate_lookup_uses_exact_submission_event_identity() throws NoSuchMethodException {
        Method method = OutboxEventMapper.class
            .getDeclaredMethod("selectByAggregateAndEvent", String.class, Long.class, String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("aggregate_type = #{aggregateType}");
        assertThat(sql).contains("aggregate_id = #{aggregateId}");
        assertThat(sql).contains("event_type = #{eventType}");
        assertThat(sql).doesNotContain("LIKE");
    }
}
