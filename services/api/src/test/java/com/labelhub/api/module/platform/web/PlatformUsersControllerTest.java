package com.labelhub.api.module.platform.web;

import com.labelhub.api.generated.model.GrantRoleRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformUsersControllerTest {

    @Test
    void platformRoleGrantEndpointRequiresPlatformAdminOnly() throws Exception {
        Method method = PlatformUsersController.class.getMethod("grantPlatformUserRole", Long.class, GrantRoleRequest.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }
}
