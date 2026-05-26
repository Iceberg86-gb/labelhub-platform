package com.labelhub.api.module.ai.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Select;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallMapperContractTest {

    @Test
    void ai_call_mapper_has_no_parent_interfaces() {
        assertThat(AiCallMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void ai_call_mapper_exposes_only_insert_and_select_methods() {
        for (Method method : AiCallMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates append-only AiCallMapper contract")
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
    void selectByIdempotencyKey_uses_exact_canonical_key_lookup() throws Exception {
        Method method = AiCallMapper.class.getDeclaredMethod("selectByIdempotencyKey", String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("WHERE idempotency_key = #{idempotencyKey}");
        assertThat(sql).doesNotContain("LIKE");
        assertThat(sql).doesNotContain("failed-attempt");
    }
}
