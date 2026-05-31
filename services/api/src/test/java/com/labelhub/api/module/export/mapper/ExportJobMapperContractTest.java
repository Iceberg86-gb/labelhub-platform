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
    void export_job_mapper_exposes_insert_select_and_status_transition_methods_only() {
        for (Method method : ExportJobMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates export job status-flow contract")
                .doesNotStartWith("update")
                .doesNotStartWith("delete")
                .doesNotStartWith("remove")
                .doesNotStartWith("save");
            assertThat(name.startsWith("insert") || name.startsWith("select") || name.startsWith("mark") || name.startsWith("increment"))
                .as("Method " + name + " must be insert/select/mark/increment only")
                .isTrue();
        }
    }
}
