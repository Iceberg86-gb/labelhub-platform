package com.labelhub.api.module.user.service;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationServiceTest {

    private static final String PASSWORD = "demo1234";
    private static final String PASSWORD_HASH = "$2a$10$encoded";

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserRegistrationService service = new UserRegistrationService(userMapper, passwordEncoder);

    @Test
    void registerCreatesActiveUserWithEncodedPasswordAndLabelerRoleOnly() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(userMapper.selectRoleIdByCode("LABELER")).thenReturn(2L);
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(2001L);
            return 1;
        });

        UserEntity registered = service.register(new UserRegistrationCommand(
            "new_labeler",
            "New Labeler",
            "new-labeler@example.test",
            PASSWORD
        ));

        assertThat(registered.getId()).isEqualTo(2001L);
        assertThat(registered.getStatus()).isEqualTo("active");
        assertThat(registered.getPasswordHash()).isEqualTo(PASSWORD_HASH);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(userCaptor.getValue().getPasswordHash()).doesNotContain(PASSWORD);

        verify(userMapper).insertUserRole(2001L, 2L);
    }
}
