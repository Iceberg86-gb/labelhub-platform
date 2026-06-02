package com.labelhub.api.module.platform.web;

import com.labelhub.api.generated.model.GrantRoleRequest;
import com.labelhub.api.generated.model.LoginUserProfile;
import com.labelhub.api.generated.web.PlatformApi;
import com.labelhub.api.module.platform.service.PlatformUserRoleService;
import com.labelhub.api.module.user.service.UserRoleCommand;
import com.labelhub.api.module.user.service.UserRoleUpdateResult;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/users")
public class PlatformUsersController implements PlatformApi {

    private final PlatformUserRoleService platformUserRoleService;

    public PlatformUsersController(PlatformUserRoleService platformUserRoleService) {
        this.platformUserRoleService = platformUserRoleService;
    }

    @Override
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping(path = "/{userId}/roles", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LoginUserProfile> grantPlatformUserRole(
        @PathVariable("userId") Long userId,
        @Valid @RequestBody GrantRoleRequest request
    ) {
        return ResponseEntity.ok(toProfile(platformUserRoleService.updateRole(
            currentUserId(),
            userId,
            new UserRoleCommand(request.getRole(), request.getEnabled())
        )));
    }

    private LoginUserProfile toProfile(UserRoleUpdateResult result) {
        LoginUserProfile profile = new LoginUserProfile();
        profile.setId(result.user().getId());
        profile.setUsername(result.user().getUsername());
        profile.setDisplayName(result.user().getDisplayName());
        profile.setRoles(result.roles());
        profile.setMustChangePassword(Boolean.TRUE.equals(result.user().getMustChangePassword()));
        return profile;
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }
}
