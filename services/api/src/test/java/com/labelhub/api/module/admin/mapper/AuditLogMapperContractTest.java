package com.labelhub.api.module.admin.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogMapperContractTest {

    @Test
    void audit_log_mapper_exposes_only_append_only_methods() {
        assertThat(AuditLogMapper.class.getInterfaces()).isEmpty();

        for (Method method : AuditLogMapper.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("update");
            assertThat(method.getName()).doesNotStartWith("delete");
            assertThat(method.getName()).isIn("insert", "selectByResource");
        }
    }
}
