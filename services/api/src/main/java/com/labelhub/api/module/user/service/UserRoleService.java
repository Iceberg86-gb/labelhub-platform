package com.labelhub.api.module.user.service;

import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRoleService {

    private static final Set<String> GRANTABLE_ROLES = Set.of("LABELER", "REVIEWER", "SENIOR_REVIEWER");

    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    public UserRoleService(UserMapper userMapper, AuditLogService auditLogService) {
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserRoleUpdateResult updateRole(Long actorUserId, Long targetUserId, UserRoleCommand command) {
        String role = normalizeRole(command.role());
        boolean enabled = command.enabled() == null || command.enabled();
        UserEntity targetUser = userMapper.selectUserById(targetUserId);
        if (targetUser == null || !"active".equals(targetUser.getStatus())) {
            throw new UserNotFoundException(targetUserId);
        }

        List<String> fromRoles = userMapper.selectRoleCodesByUserId(targetUserId);
        Long roleId = userMapper.selectRoleIdByCode(role);
        if (roleId == null) {
            throw new InvalidUserRoleAssignmentException("Role is not grantable: " + role);
        }

        boolean hadRole = fromRoles.contains(role);
        if (enabled && !hadRole) {
            userMapper.insertUserRole(targetUserId, roleId);
            return audited(actorUserId, targetUser, fromRoles, role, AuditActions.ROLE_GRANTED);
        }
        if (!enabled && hadRole) {
            userMapper.deleteUserRole(targetUserId, roleId);
            return audited(actorUserId, targetUser, fromRoles, role, AuditActions.ROLE_REVOKED);
        }

        return new UserRoleUpdateResult(targetUser, fromRoles);
    }

    private UserRoleUpdateResult audited(
        Long actorUserId,
        UserEntity targetUser,
        List<String> fromRoles,
        String role,
        String action
    ) {
        List<String> toRoles = userMapper.selectRoleCodesByUserId(targetUser.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromRoles", fromRoles);
        payload.put("toRole", role);
        payload.put("toRoles", toRoles);
        auditLogService.record(AuditEventBuilder.forAction(action)
            .actorType("USER")
            .actorId(actorUserId)
            .resource("USER", targetUser.getId())
            .payload(payload));
        return new UserRoleUpdateResult(targetUser, toRoles);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new InvalidUserRoleAssignmentException("Role is required");
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!GRANTABLE_ROLES.contains(normalized)) {
            throw new InvalidUserRoleAssignmentException("Role is not grantable: " + normalized);
        }
        return normalized;
    }
}
