package com.labelhub.api.module.ai.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallInFieldMapperContractTest {

    @Test
    void ai_call_in_field_mapper_has_no_parent_interfaces() {
        assertThat(AiCallInFieldMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void ai_call_in_field_mapper_exposes_only_insert_and_select_methods() {
        for (Method method : AiCallInFieldMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates append-only AiCallInFieldMapper contract")
                .doesNotStartWith("update")
                .doesNotStartWith("delete")
                .doesNotStartWith("remove")
                .doesNotStartWith("save");
            assertThat(name.startsWith("insert") || name.startsWith("select"))
                .as("Method " + name + " must be insert/select only")
                .isTrue();
        }
    }
}
