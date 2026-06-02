package com.labelhub.api.module.platform.service;

import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import com.labelhub.api.module.user.service.InvalidUserRoleAssignmentException;
import com.labelhub.api.module.user.service.UserRoleCommand;
import com.labelhub.api.module.user.service.UserRoleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformUserRoleServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final PlatformUserRoleService platformService = new PlatformUserRoleService(userMapper, auditLogService);
    private final UserRoleService businessRoleService = new UserRoleService(userMapper, auditLogService);

    @Test
    void platformAdminCanGrantOwnerWhileBusinessRoleServiceStillRejectsOwner() {
        UserEntity target = activeUser(2001L, "owner_candidate", "Owner Candidate");
        when(userMapper.selectUserById(2001L)).thenReturn(target);
        when(userMapper.selectRoleCodesByUserId(2001L)).thenReturn(List.of("LABELER"), List.of("LABELER", "OWNER"));
        when(userMapper.selectRoleIdByCode("OWNER")).thenReturn(1L);

        platformService.updateRole(9001L, 2001L, new UserRoleCommand("OWNER", true));

        verify(userMapper).insertUserRole(2001L, 1L);
        ArgumentCaptor<AuditEventBuilder> auditCaptor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(auditCaptor.capture());
        var event = auditCaptor.getValue().build();
        assertThat(event.actorType()).isEqualTo("platform_admin");
        assertThat(event.actorId()).isEqualTo(9001L);
        assertThat(event.payload()).containsEntry("toRole", "OWNER");

        assertThatThrownBy(() -> businessRoleService.updateRole(1001L, 2001L, new UserRoleCommand("OWNER", true)))
            .isInstanceOf(InvalidUserRoleAssignmentException.class);
    }

    @Test
    void platformAdminRejectsAiAgentRole() {
        assertThatThrownBy(() -> platformService.updateRole(9001L, 2001L, new UserRoleCommand("AI_AGENT", true)))
            .isInstanceOf(InvalidUserRoleAssignmentException.class)
            .hasMessageContaining("AI_AGENT");

        verify(userMapper, never()).insertUserRole(2001L, 4L);
        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void platformAdminRejectsPlatformAdminRole() {
        assertThatThrownBy(() -> platformService.updateRole(9001L, 2001L, new UserRoleCommand("PLATFORM_ADMIN", true)))
            .isInstanceOf(InvalidUserRoleAssignmentException.class)
            .hasMessageContaining("PLATFORM_ADMIN");

        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void platformAdminRejectsSelfGrant() {
        assertThatThrownBy(() -> platformService.updateRole(9001L, 9001L, new UserRoleCommand("OWNER", true)))
            .isInstanceOf(InvalidUserRoleAssignmentException.class)
            .hasMessageContaining("self");

        verify(userMapper, never()).selectUserById(9001L);
        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    private static UserEntity activeUser(Long id, String username, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus("active");
        return user;
    }
}
