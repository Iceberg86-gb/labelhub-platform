package com.labelhub.api.module.session.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DraftMapperContractTest {

    @Test
    void draftMapper_has_no_parent_interfaces() {
        assertThat(DraftMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void draftMapper_only_exposes_insert_and_select_methods() {
        for (Method method : DraftMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            boolean allowedPrefix = name.startsWith("insert") || name.startsWith("select");
            assertThat(allowedPrefix)
                .as("Method %s violates append-only DraftMapper contract", name)
                .isTrue();
            assertThat(name)
                .doesNotStartWith("update")
                .doesNotStartWith("delete")
                .doesNotStartWith("remove")
                .doesNotStartWith("save");
        }
    }
}
