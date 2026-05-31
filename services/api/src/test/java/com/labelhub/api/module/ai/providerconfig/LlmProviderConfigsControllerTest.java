package com.labelhub.api.module.ai.providerconfig;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderConfigsControllerTest {

    @Test
    void providerManagementEndpointsRequireOwnerRole() throws Exception {
        assertOwnerOnly("listLlmProviders");
        assertOwnerOnly("createLlmProvider", com.labelhub.api.generated.model.LlmProviderConfigRequest.class);
        assertOwnerOnly("getLlmProvider", Long.class);
        assertOwnerOnly("updateLlmProvider", Long.class, com.labelhub.api.generated.model.LlmProviderConfigRequest.class);
        assertOwnerOnly("deleteLlmProvider", Long.class);
        assertOwnerOnly("testLlmProvider", Long.class, com.labelhub.api.generated.model.LlmProviderTestConnectionRequest.class);
        assertOwnerOnly("testUnsavedLlmProvider", com.labelhub.api.generated.model.LlmProviderTestConnectionRequest.class);
    }

    private void assertOwnerOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = LlmProviderConfigsController.class.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('OWNER')");
    }
}
