package com.labelhub.api.module.user.service;

import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRoleServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UserRoleService service = new UserRoleService(userMapper, auditLogService);

    @Test
    void grantRejectsOwnerAndAiAgentRolesBeforeWriting() {
        assertThatThrownBy(() -> service.updateRole(1001L, 2001L, new UserRoleCommand("OWNER", true)))
            .isInstanceOf(InvalidUserRoleAssignmentException.class);
        assertThatThrownBy(() -> service.updateRole(1001L, 2001L, new UserRoleCommand("AI_AGENT", true)))
            .isInstanceOf(InvalidUserRoleAssignmentException.class);
    }

    @Test
    void grantAllowedRoleWritesAuditWithFromRolesAndToRole() {
        UserEntity target = new UserEntity();
        target.setId(2001L);
        target.setUsername("reviewer_candidate");
        target.setDisplayName("Reviewer Candidate");
        target.setStatus("active");
        when(userMapper.selectUserById(2001L)).thenReturn(target);
        when(userMapper.selectRoleCodesByUserId(2001L)).thenReturn(List.of("LABELER"), List.of("LABELER", "REVIEWER"));
        when(userMapper.selectRoleIdByCode("REVIEWER")).thenReturn(3L);

        service.updateRole(1001L, 2001L, new UserRoleCommand("REVIEWER", true));

        verify(userMapper).insertUserRole(2001L, 3L);
        ArgumentCaptor<AuditEventBuilder> auditCaptor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(auditCaptor.capture());
        var event = auditCaptor.getValue().build();
        assertThat(event.actorType()).isEqualTo("USER");
        assertThat(event.actorId()).isEqualTo(1001L);
        assertThat(event.action()).isEqualTo("ROLE_GRANTED");
        assertThat(event.resourceType()).isEqualTo("USER");
        assertThat(event.resourceId()).isEqualTo(2001L);
        assertThat(event.payload()).containsEntry("fromRoles", List.of("LABELER"));
        assertThat(event.payload()).containsEntry("toRole", "REVIEWER");
        assertThat(event.payload().get("toRoles")).isEqualTo(List.of("LABELER", "REVIEWER"));
    }

    @Test
    void revokeAllowedRoleWritesAuditWithFromRolesAndToRole() {
        UserEntity target = new UserEntity();
        target.setId(2001L);
        target.setUsername("reviewer_candidate");
        target.setDisplayName("Reviewer Candidate");
        target.setStatus("active");
        when(userMapper.selectUserById(2001L)).thenReturn(target);
        when(userMapper.selectRoleCodesByUserId(2001L)).thenReturn(List.of("LABELER", "REVIEWER"), List.of("LABELER"));
        when(userMapper.selectRoleIdByCode("REVIEWER")).thenReturn(3L);

        service.updateRole(1001L, 2001L, new UserRoleCommand("REVIEWER", false));

        verify(userMapper).deleteUserRole(2001L, 3L);
        ArgumentCaptor<AuditEventBuilder> auditCaptor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(auditCaptor.capture());
        var event = auditCaptor.getValue().build();
        assertThat(event.action()).isEqualTo("ROLE_REVOKED");
        assertThat(event.payload()).containsEntry("fromRoles", List.of("LABELER", "REVIEWER"));
        assertThat(event.payload()).containsEntry("toRole", "REVIEWER");
        assertThat(event.payload().get("toRoles")).isEqualTo(List.of("LABELER"));
        assertThat(event.payload()).doesNotContainKey("password");
        assertThat(event.payload()).doesNotContainKey("passwordHash");
    }
}
