package com.labelhub.api.module.user.service;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserService userService = new UserService(userMapper);

    @Test
    void find_by_username_returns_empty_when_user_missing() {
        when(userMapper.selectByUsername("missing")).thenReturn(null);

        Optional<UserEntity> result = userService.findByUsername("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void find_by_username_returns_active_user() {
        UserEntity user = new UserEntity();
        user.setId(1001L);
        user.setUsername("owner_demo");
        user.setStatus("active");
        when(userMapper.selectByUsername("owner_demo")).thenReturn(user);

        Optional<UserEntity> result = userService.findByUsername("owner_demo");

        assertThat(result).contains(user);
    }

    @Test
    void load_roles_returns_role_codes_for_user() {
        when(userMapper.selectRoleCodesByUserId(1001L)).thenReturn(List.of("OWNER"));

        List<String> roles = userService.loadRoles(1001L);

        assertThat(roles).containsExactly("OWNER");
    }
}
