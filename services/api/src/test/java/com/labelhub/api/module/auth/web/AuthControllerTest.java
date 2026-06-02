package com.labelhub.api.module.auth.web;

import com.labelhub.api.generated.model.LoginRequest;
import com.labelhub.api.module.auth.service.RefreshTokenService;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.service.UserRegistrationService;
import com.labelhub.api.module.user.service.UserService;
import com.labelhub.api.security.JwtIssuer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UserRegistrationService registrationService = mock(UserRegistrationService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtIssuer jwtIssuer = mock(JwtIssuer.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AuthController controller = new AuthController(
        userService,
        registrationService,
        passwordEncoder,
        jwtIssuer,
        refreshTokenService
    );

    @Test
    void loginResponseIncludesMustChangePasswordFlag() {
        UserEntity user = new UserEntity();
        user.setId(9001L);
        user.setUsername("platform_admin");
        user.setDisplayName("Platform Administrator");
        user.setPasswordHash("encoded");
        user.setStatus("active");
        user.setMustChangePassword(true);
        when(userService.findByUsername("platform_admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(userService.loadRoles(9001L)).thenReturn(List.of("PLATFORM_ADMIN"));
        when(jwtIssuer.issue(user, List.of("PLATFORM_ADMIN")))
            .thenReturn(new JwtIssuer.IssuedToken("access-token", Instant.parse("2026-06-02T12:00:00Z")));
        when(refreshTokenService.issueForUser(9001L))
            .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", Instant.parse("2026-06-09T12:00:00Z"), 604800));
        LoginRequest request = new LoginRequest();
        request.setUsername("platform_admin");
        request.setPassword("secret");

        var response = controller.login(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUser().getRoles()).containsExactly("PLATFORM_ADMIN");
        assertThat(response.getBody().getUser().getMustChangePassword()).isTrue();
    }
}
