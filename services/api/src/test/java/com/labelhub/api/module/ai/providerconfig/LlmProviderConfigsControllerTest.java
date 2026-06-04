package com.labelhub.api.module.ai.providerconfig;

import com.labelhub.api.generated.model.LlmProviderConfig;
import com.labelhub.api.security.JwtPrincipal;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmProviderConfigsControllerTest {

    private final LlmProviderConfigService service = mock(LlmProviderConfigService.class);
    private final LlmProviderConfigDtoMapper dtoMapper = new LlmProviderConfigDtoMapper();
    private final LlmProviderConfigsController controller = new LlmProviderConfigsController(service, dtoMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void providerManagementEndpointsRequirePlatformAdminRole() throws Exception {
        assertPlatformAdminOnly("listLlmProviders");
        assertPlatformAdminOnly("createLlmProvider", com.labelhub.api.generated.model.LlmProviderConfigRequest.class);
        assertPlatformAdminOnly("getLlmProvider", Long.class);
        assertPlatformAdminOnly("updateLlmProvider", Long.class, com.labelhub.api.generated.model.LlmProviderConfigRequest.class);
        assertPlatformAdminOnly("activateLlmProvider", Long.class);
        assertPlatformAdminOnly("deleteLlmProvider", Long.class);
        assertPlatformAdminOnly("testLlmProvider", Long.class, com.labelhub.api.generated.model.LlmProviderTestConnectionRequest.class);
        assertPlatformAdminOnly("testUnsavedLlmProvider", com.labelhub.api.generated.model.LlmProviderTestConnectionRequest.class);
    }

    @Test
    void activateLlmProvider_returnsActivatedProviderForPlatformAdmin() {
        LlmProviderConfigEntity entity = platformProvider();
        when(service.activate(9001L, 43L)).thenReturn(entity);
        authenticatePlatformAdmin(9001L);

        var response = controller.activateLlmProvider(43L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LlmProviderConfig body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isEqualTo(43L);
        assertThat(body.getScope()).isEqualTo(LlmProviderConfig.ScopeEnum.PLATFORM);
        assertThat(body.getEnabled()).isTrue();
        verify(service).activate(9001L, 43L);
    }

    @Test
    void activateLlmProvider_preservesNotFoundForMissingProvider() {
        when(service.activate(9001L, 404L)).thenThrow(new LlmProviderConfigNotFoundException(404L));
        authenticatePlatformAdmin(9001L);

        assertThatThrownBy(() -> controller.activateLlmProvider(404L))
            .isInstanceOf(LlmProviderConfigNotFoundException.class);
    }

    private void assertPlatformAdminOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = LlmProviderConfigsController.class.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
    }

    private void authenticatePlatformAdmin(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(userId, "platform_admin", List.of("PLATFORM_ADMIN")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        ));
    }

    private LlmProviderConfigEntity platformProvider() {
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        entity.setId(43L);
        entity.setOwnerId(9001L);
        entity.setScope("platform");
        entity.setProviderType("openai-compatible");
        entity.setProviderName("deepseek");
        entity.setBaseUrl("https://api.deepseek.test/v1");
        entity.setModelName("deepseek-chat");
        entity.setSecretCiphertext("ciphertext");
        entity.setSecretLast4("0000");
        entity.setEnabled(true);
        return entity;
    }
}
