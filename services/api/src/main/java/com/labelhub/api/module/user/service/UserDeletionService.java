package com.labelhub.api.module.user.service;

import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.auth.service.RefreshTokenService;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeletionService {

    private static final String ACTIVE = "active";
    private static final String DELETED = "deleted";

    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    @Autowired
    public UserDeletionService(
        UserMapper userMapper,
        AuditLogService auditLogService,
        RefreshTokenService refreshTokenService
    ) {
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.refreshTokenService = refreshTokenService;
    }

    public UserDeletionService(UserMapper userMapper, AuditLogService auditLogService) {
        this(userMapper, auditLogService, null);
    }

    @Transactional
    public void deleteUser(Long actorUserId, Long targetUserId) {
        if (actorUserId != null && actorUserId.equals(targetUserId)) {
            throw new CannotDeleteSelfException(targetUserId);
        }

        UserEntity target = userMapper.selectActiveUserById(targetUserId);
        if (target == null) {
            throw new UserDeletionConflictException(targetUserId);
        }

        List<String> targetRoles = userMapper.selectRoleCodesByUserId(targetUserId);
        if (targetRoles.contains("OWNER")) {
            throw new CannotDeleteOwnerException(targetUserId);
        }

        if (userMapper.softDeleteUserById(targetUserId) != 1) {
            throw new UserDeletionConflictException(targetUserId);
        }
        if (refreshTokenService != null) {
            refreshTokenService.revokeActiveForUser(targetUserId);
        }

        auditLogService.record(AuditEventBuilder.forAction(AuditActions.USER_DELETED)
            .actorType("USER")
            .actorId(actorUserId)
            .resource("USER", targetUserId)
            .payload("fromStatus", ACTIVE)
            .payload("toStatus", DELETED)
            .payload("targetUsername", target.getUsername())
            .payload("targetRoles", targetRoles));
    }
}
