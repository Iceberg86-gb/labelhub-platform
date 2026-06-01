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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDeletionServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UserDeletionService service = new UserDeletionService(userMapper, auditLogService);

    @Test
    void softDeleteMarksActiveUserDeletedAndWritesAuditWithoutSecrets() {
        UserEntity target = activeUser(2001L, "labeler_stop", "Labeler Stop");
        when(userMapper.selectActiveUserById(2001L)).thenReturn(target);
        when(userMapper.selectRoleCodesByUserId(2001L)).thenReturn(List.of("LABELER", "REVIEWER"));
        when(userMapper.softDeleteUserById(2001L)).thenReturn(1);

        service.deleteUser(1001L, 2001L);

        verify(userMapper).softDeleteUserById(2001L);
        ArgumentCaptor<AuditEventBuilder> auditCaptor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(auditCaptor.capture());
        var event = auditCaptor.getValue().build();
        assertThat(event.actorType()).isEqualTo("USER");
        assertThat(event.actorId()).isEqualTo(1001L);
        assertThat(event.action()).isEqualTo("USER_DELETED");
        assertThat(event.resourceType()).isEqualTo("USER");
        assertThat(event.resourceId()).isEqualTo(2001L);
        assertThat(event.payload()).containsEntry("fromStatus", "active");
        assertThat(event.payload()).containsEntry("toStatus", "deleted");
        assertThat(event.payload()).containsEntry("targetUsername", "labeler_stop");
        assertThat(event.payload()).containsEntry("targetRoles", List.of("LABELER", "REVIEWER"));
        assertThat(event.payload()).doesNotContainKey("password");
        assertThat(event.payload()).doesNotContainKey("passwordHash");
        assertThat(event.payload()).doesNotContainKey("hash");
    }

    @Test
    void softDeleteRejectsSelfBeforeWriting() {
        assertThatThrownBy(() -> service.deleteUser(1001L, 1001L))
            .isInstanceOf(CannotDeleteSelfException.class);

        verify(userMapper, never()).softDeleteUserById(1001L);
        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void softDeleteRejectsOwnerBeforeWriting() {
        UserEntity target = activeUser(1002L, "owner_other", "Owner Other");
        when(userMapper.selectActiveUserById(1002L)).thenReturn(target);
        when(userMapper.selectRoleCodesByUserId(1002L)).thenReturn(List.of("OWNER"));

        assertThatThrownBy(() -> service.deleteUser(1001L, 1002L))
            .isInstanceOf(CannotDeleteOwnerException.class);

        verify(userMapper, never()).softDeleteUserById(1002L);
        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void softDeleteReturnsConflictForMissingOrAlreadyDeletedUser() {
        when(userMapper.selectActiveUserById(2002L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteUser(1001L, 2002L))
            .isInstanceOf(UserDeletionConflictException.class);

        verify(userMapper, never()).softDeleteUserById(2002L);
        verify(auditLogService, never()).record(org.mockito.ArgumentMatchers.any());
    }

    private UserEntity activeUser(Long id, String username, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus("active");
        return user;
    }
}
