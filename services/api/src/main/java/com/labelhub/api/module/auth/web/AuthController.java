package com.labelhub.api.module.auth.web;

import com.labelhub.api.generated.model.LoginRequest;
import com.labelhub.api.generated.model.LoginResponse;
import com.labelhub.api.generated.model.LoginUserProfile;
import com.labelhub.api.generated.model.RegisterRequest;
import com.labelhub.api.generated.web.AuthApi;
import com.labelhub.api.module.auth.service.RefreshTokenService;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.service.UserService;
import com.labelhub.api.module.user.service.UserRegistrationCommand;
import com.labelhub.api.module.user.service.UserRegistrationService;
import com.labelhub.api.security.JwtIssuer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController implements AuthApi {

    private final UserService userService;
    private final UserRegistrationService registrationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
        UserService userService,
        UserRegistrationService registrationService,
        PasswordEncoder passwordEncoder,
        JwtIssuer jwtIssuer,
        RefreshTokenService refreshTokenService
    ) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        UserEntity user = userService.findByUsername(loginRequest.getUsername())
            .filter(candidate -> passwordEncoder.matches(loginRequest.getPassword(), candidate.getPasswordHash()))
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        List<String> roles = userService.loadRoles(user.getId());
        return authenticatedResponse(user, roles, HttpStatus.OK);
    }

    @Override
    @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        UserEntity user = registrationService.register(new UserRegistrationCommand(
            registerRequest.getUsername(),
            registerRequest.getDisplayName(),
            registerRequest.getEmail(),
            registerRequest.getPassword()
        ));
        List<String> roles = userService.loadRoles(user.getId());
        return authenticatedResponse(user, roles, HttpStatus.CREATED);
    }

    @Override
    @PostMapping(path = "/refresh", produces = "application/json")
    public ResponseEntity<LoginResponse> refresh(
        @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken
    ) {
        RefreshTokenService.RefreshSession session = refreshTokenService.rotate(refreshToken);
        return accessResponse(session.user(), session.roles(), session.refreshToken());
    }

    @Override
    @PostMapping(path = "/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = RefreshTokenService.COOKIE_NAME, required = false) String refreshToken
    ) {
        refreshTokenService.logout(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
            .build();
    }

    private ResponseEntity<LoginResponse> authenticatedResponse(UserEntity user, List<String> roles, HttpStatus status) {
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueForUser(user.getId());
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString())
            .body(loginResponse(user, roles));
    }

    private ResponseEntity<LoginResponse> accessResponse(
        UserEntity user,
        List<String> roles,
        RefreshTokenService.IssuedRefreshToken refreshToken
    ) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken).toString())
            .body(loginResponse(user, roles));
    }

    private LoginResponse loginResponse(UserEntity user, List<String> roles) {
        JwtIssuer.IssuedToken issued = jwtIssuer.issue(user, roles);

        LoginUserProfile profile = new LoginUserProfile();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setDisplayName(user.getDisplayName());
        profile.setRoles(roles);
        profile.setMustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()));

        LoginResponse response = new LoginResponse();
        response.setAccessToken(issued.accessToken());
        response.setTokenType(LoginResponse.TokenTypeEnum.BEARER);
        response.setExpiresAt(OffsetDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC));
        response.setUser(profile);
        return response;
    }

    private ResponseCookie refreshCookie(RefreshTokenService.IssuedRefreshToken token) {
        return ResponseCookie.from(RefreshTokenService.COOKIE_NAME, token.token())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(RefreshTokenService.COOKIE_PATH)
            .maxAge(Duration.ofSeconds(token.maxAgeSeconds()))
            .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(RefreshTokenService.COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(RefreshTokenService.COOKIE_PATH)
            .maxAge(Duration.ZERO)
            .build();
    }
}
