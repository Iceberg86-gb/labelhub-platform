package com.labelhub.api.module.export.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportJobMapperContractTest {

    @Test
    void export_job_mapper_has_no_parent_interfaces() {
        assertThat(ExportJobMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void export_job_mapper_exposes_only_insert_and_select_methods() {
        for (Method method : ExportJobMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates append-only ExportJobMapper contract")
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
