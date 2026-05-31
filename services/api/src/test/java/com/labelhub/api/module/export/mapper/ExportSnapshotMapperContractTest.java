package com.labelhub.api.module.export.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportSnapshotMapperContractTest {

    @Test
    void export_snapshot_mapper_has_no_parent_interfaces() {
        assertThat(ExportSnapshotMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void export_snapshot_mapper_exposes_only_insert_select_and_archive_methods() {
        for (Method method : ExportSnapshotMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates append-only ExportSnapshotMapper contract")
                .doesNotStartWith("update")
                .doesNotStartWith("delete")
                .doesNotStartWith("remove")
                .doesNotStartWith("save");
            assertThat(name.startsWith("insert") || name.startsWith("select") || name.startsWith("archive"))
                .as("Method " + name + " must be insert/select/archive only")
                .isTrue();
        }
    }
}
