package com.labelhub.api.module.auth.web;

import com.labelhub.api.generated.model.LoginRequest;
import com.labelhub.api.generated.model.LoginResponse;
import com.labelhub.api.generated.model.LoginUserProfile;
import com.labelhub.api.generated.web.AuthApi;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.service.UserService;
import com.labelhub.api.security.JwtIssuer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController implements AuthApi {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        UserEntity user = userService.findByUsername(loginRequest.getUsername())
            .filter(candidate -> passwordEncoder.matches(loginRequest.getPassword(), candidate.getPasswordHash()))
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        List<String> roles = userService.loadRoles(user.getId());
        JwtIssuer.IssuedToken issued = jwtIssuer.issue(user, roles);

        LoginUserProfile profile = new LoginUserProfile();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setDisplayName(user.getDisplayName());
        profile.setRoles(roles);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(issued.accessToken());
        response.setTokenType(LoginResponse.TokenTypeEnum.BEARER);
        response.setExpiresAt(OffsetDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC));
        response.setUser(profile);
        return ResponseEntity.ok(response);
    }
}
