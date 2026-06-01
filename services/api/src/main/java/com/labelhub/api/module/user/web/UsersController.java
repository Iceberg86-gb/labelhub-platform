package com.labelhub.api.module.user.web;

import com.labelhub.api.generated.model.GrantRoleRequest;
import com.labelhub.api.generated.model.LoginUserProfile;
import com.labelhub.api.generated.model.PagedUsers;
import com.labelhub.api.generated.model.UserAccountSummary;
import com.labelhub.api.generated.web.UsersApi;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.module.user.service.UserRoleCommand;
import com.labelhub.api.module.user.service.UserRoleService;
import com.labelhub.api.module.user.service.UserRoleUpdateResult;
import com.labelhub.api.module.user.service.UserService;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UsersController implements UsersApi {

    private final UserService userService;
    private final UserRoleService userRoleService;

    public UsersController(UserService userService, UserRoleService userRoleService) {
        this.userService = userService;
        this.userRoleService = userRoleService;
    }

    @Override
    @PreAuthorize("hasAnyRole('OWNER','SENIOR_REVIEWER')")
    @GetMapping(produces = "application/json")
    public ResponseEntity<PagedUsers> listUsers(
        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        PagedResult<UserRoleUpdateResult> result = userService.listActiveUsers(page, size);
        PagedUsers response = new PagedUsers();
        response.setItems(result.items().stream().map(this::toAccountSummary).toList());
        response.setTotal(result.total());
        response.setPage((int) result.page());
        response.setSize((int) result.size());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasAnyRole('OWNER','SENIOR_REVIEWER')")
    @PostMapping(path = "/{userId}/roles", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LoginUserProfile> grantUserRole(
        @PathVariable("userId") Long userId,
        @Valid @RequestBody GrantRoleRequest request
    ) {
        return ResponseEntity.ok(toProfile(userRoleService.updateRole(
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
        return profile;
    }

    private UserAccountSummary toAccountSummary(UserRoleUpdateResult result) {
        UserAccountSummary summary = new UserAccountSummary();
        summary.setId(result.user().getId());
        summary.setUsername(result.user().getUsername());
        summary.setDisplayName(result.user().getDisplayName());
        summary.setEmail(result.user().getEmail());
        summary.setStatus(result.user().getStatus());
        summary.setRoles(result.roles());
        summary.setCreatedAt(result.user().getCreatedAt() == null ? null : result.user().getCreatedAt().atOffset(ZoneOffset.UTC));
        return summary;
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }
}
