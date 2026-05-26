package com.labelhub.api.module.schema.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionMapperContractTest {

    @Test
    void submission_mapper_has_no_parent_interfaces() {
        assertThat(SubmissionMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void submission_mapper_exposes_only_insert_and_select_methods() {
        for (Method method : SubmissionMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                    .as("Method " + name + " violates append-only Mapper contract")
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
