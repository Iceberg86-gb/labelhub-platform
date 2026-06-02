package com.labelhub.api.module.platform.bootstrap;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAdminSeederTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Test
    void seedFailsFastWhenInitialPasswordMissing() {
        PlatformAdminSeeder seeder = new PlatformAdminSeeder(userMapper, passwordEncoder, " ");

        assertThatThrownBy(() -> seeder.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LABELHUB_PA_INITIAL_PASSWORD")
            .hasMessageNotContaining("password=");

        verify(userMapper, never()).insertPlatformAdminUser(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void seedCreatesActivePlatformAdminWithEncodedPasswordAndMustChangePassword() throws Exception {
        when(userMapper.selectActiveByUsername("platform_admin")).thenReturn(null);
        when(userMapper.selectRoleIdByCode("PLATFORM_ADMIN")).thenReturn(6L);
        when(userMapper.insertPlatformAdminUser(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(9001L);
            return 1;
        });
        PlatformAdminSeeder seeder = new PlatformAdminSeeder(userMapper, passwordEncoder, "StrongerPaPass123!");

        seeder.run(null);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userMapper).insertPlatformAdminUser(userCaptor.capture());
        UserEntity seeded = userCaptor.getValue();
        assertThat(seeded.getUsername()).isEqualTo("platform_admin");
        assertThat(seeded.getDisplayName()).isEqualTo("Platform Administrator");
        assertThat(seeded.getStatus()).isEqualTo("active");
        assertThat(seeded.getMustChangePassword()).isTrue();
        assertThat(seeded.getPasswordHash()).startsWith("$2a$10$");
        assertThat(seeded.getPasswordHash()).doesNotContain("StrongerPaPass123!");
        assertThat(passwordEncoder.matches("StrongerPaPass123!", seeded.getPasswordHash())).isTrue();
        verify(userMapper).insertUserRole(9001L, 6L);
    }
}
