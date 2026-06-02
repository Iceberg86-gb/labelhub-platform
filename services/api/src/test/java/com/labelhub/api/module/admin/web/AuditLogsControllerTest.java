package com.labelhub.api.module.admin.web;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogsControllerTest {

    @Test
    void auditLogEndpointsRequirePlatformAdminRole() throws Exception {
        assertPlatformAdminOnly(
            "exportAuditLogs",
            String.class,
            String.class,
            Long.class,
            Long.class,
            OffsetDateTime.class,
            OffsetDateTime.class
        );
        assertPlatformAdminOnly(
            "listAuditLogs",
            Integer.class,
            Integer.class,
            String.class,
            String.class,
            Long.class,
            Long.class,
            OffsetDateTime.class,
            OffsetDateTime.class
        );
    }

    private void assertPlatformAdminOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AuditLogsController.class.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }
}
