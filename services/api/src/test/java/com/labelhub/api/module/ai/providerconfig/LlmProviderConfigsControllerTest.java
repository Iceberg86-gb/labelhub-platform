package com.labelhub.api.module.ai.providerconfig;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderConfigsControllerTest {

    @Test
    void providerManagementEndpointsRequirePlatformAdminRole() throws Exception {
        assertPlatformAdminOnly("listLlmProviders");
        assertPlatformAdminOnly("createLlmProvider", com.labelhub.api.generated.model.LlmProviderConfigRequest.class);
        assertPlatformAdminOnly("getLlmProvider", Long.class);
        assertPlatformAdminOnly("updateLlmProvider", Long.class, com.labelhub.api.generated.model.LlmProviderConfigRequest.class);
        assertPlatformAdminOnly("deleteLlmProvider", Long.class);
        assertPlatformAdminOnly("testLlmProvider", Long.class, com.labelhub.api.generated.model.LlmProviderTestConnectionRequest.class);
        assertPlatformAdminOnly("testUnsavedLlmProvider", com.labelhub.api.generated.model.LlmProviderTestConnectionRequest.class);
    }

    private void assertPlatformAdminOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = LlmProviderConfigsController.class.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }
}
