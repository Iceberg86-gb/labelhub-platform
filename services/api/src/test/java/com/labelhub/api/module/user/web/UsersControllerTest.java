package com.labelhub.api.module.user.web;

import com.labelhub.api.generated.model.GrantRoleRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class UsersControllerTest {

    @Test
    void roleGrantEndpointRequiresOwnerOrSeniorReviewer() throws Exception {
        Method method = UsersController.class.getMethod("grantUserRole", Long.class, GrantRoleRequest.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('OWNER','SENIOR_REVIEWER')");
    }

    @Test
    void userListEndpointRequiresOwnerOrSeniorReviewer() throws Exception {
        Method method = UsersController.class.getMethod("listUsers", Integer.class, Integer.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('OWNER','SENIOR_REVIEWER')");
    }

    @Test
    void deleteUserEndpointRequiresOwnerOnly() throws Exception {
        Method method = UsersController.class.getMethod("deleteUser", Long.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('OWNER')");
    }
}
