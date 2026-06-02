package com.labelhub.api.module.platform.web;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMetricsControllerTest {

    @Test
    void platformMetricsEndpointsRequirePlatformAdmin() throws Exception {
        Method laborMethod = PlatformMetricsController.class.getDeclaredMethod("getLaborMetrics");
        Method efficiencyMethod = PlatformMetricsController.class.getDeclaredMethod("getEfficiencyMetrics");

        assertThat(laborMethod.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasRole('PLATFORM_ADMIN')");
        assertThat(efficiencyMethod.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasRole('PLATFORM_ADMIN')");
    }
}
