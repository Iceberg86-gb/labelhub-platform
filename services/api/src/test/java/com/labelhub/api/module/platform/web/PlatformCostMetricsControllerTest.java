package com.labelhub.api.module.platform.web;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCostMetricsControllerTest {

    @Test
    void costMetricsEndpointRequiresPlatformAdminOnly() throws Exception {
        Method method = PlatformCostMetricsController.class.getMethod("getPlatformCostMetrics", OffsetDateTime.class, OffsetDateTime.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }
}
