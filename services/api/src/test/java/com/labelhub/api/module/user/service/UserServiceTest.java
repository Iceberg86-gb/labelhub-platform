package com.labelhub.api.module.user.service;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import com.labelhub.api.module.task.service.PagedResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserService userService = new UserService(userMapper);

    @Test
    void find_by_username_returns_empty_when_user_missing() {
        when(userMapper.selectActiveByUsername("missing")).thenReturn(null);

        Optional<UserEntity> result = userService.findByUsername("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void find_by_username_returns_active_user() {
        UserEntity user = new UserEntity();
        user.setId(1001L);
        user.setUsername("owner_demo");
        user.setStatus("active");
        when(userMapper.selectActiveByUsername("owner_demo")).thenReturn(user);

        Optional<UserEntity> result = userService.findByUsername("owner_demo");

        assertThat(result).contains(user);
    }

    @Test
    void find_by_username_uses_active_lookup_when_deleted_rows_share_username() {
        UserEntity activeUser = new UserEntity();
        activeUser.setId(1022L);
        activeUser.setUsername("774670647");
        activeUser.setStatus("active");
        when(userMapper.selectActiveByUsername("774670647")).thenReturn(activeUser);

        Optional<UserEntity> result = userService.findByUsername("774670647");

        assertThat(result).contains(activeUser);
        assertThat(result.get().getId()).isEqualTo(1022L);
        assertThat(result.get().getStatus()).isEqualTo("active");
        verify(userMapper).selectActiveByUsername("774670647");
        verify(userMapper, never()).selectByUsername("774670647");
    }

    @Test
    void load_roles_returns_role_codes_for_user() {
        when(userMapper.selectRoleCodesByUserId(1001L)).thenReturn(List.of("OWNER"));

        List<String> roles = userService.loadRoles(1001L);

        assertThat(roles).containsExactly("OWNER");
    }

    @Test
    void list_active_users_returns_registered_accounts_with_roles() {
        UserEntity registered = new UserEntity();
        registered.setId(2001L);
        registered.setUsername("new_labeler");
        registered.setDisplayName("New Labeler");
        registered.setStatus("active");
        when(userMapper.selectActiveUsersPage(0, 20)).thenReturn(List.of(registered));
        when(userMapper.countActiveUsers()).thenReturn(1L);
        when(userMapper.selectRoleCodesByUserId(2001L)).thenReturn(List.of("LABELER"));

        PagedResult<UserRoleUpdateResult> result = userService.listActiveUsers(1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).user()).isSameAs(registered);
        assertThat(result.items().get(0).roles()).containsExactly("LABELER");
    }
}
