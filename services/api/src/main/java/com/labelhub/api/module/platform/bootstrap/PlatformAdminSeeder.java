package com.labelhub.api.module.platform.bootstrap;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdminSeeder implements ApplicationRunner {

    private static final String USERNAME = "platform_admin";
    private static final String DISPLAY_NAME = "Platform Administrator";
    private static final String ROLE = "PLATFORM_ADMIN";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String initialPassword;

    public PlatformAdminSeeder(
        UserMapper userMapper,
        PasswordEncoder passwordEncoder,
        @Value("${LABELHUB_PA_INITIAL_PASSWORD:${labelhub.platform-admin.initial-password:}}") String initialPassword
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.initialPassword = initialPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException("LABELHUB_PA_INITIAL_PASSWORD must be provided for the platform admin account");
        }
        if (initialPassword.length() < 12) {
            throw new IllegalStateException("LABELHUB_PA_INITIAL_PASSWORD must be at least 12 characters");
        }

        UserEntity user = userMapper.selectActiveByUsername(USERNAME);
        if (user == null) {
            user = new UserEntity();
            user.setUsername(USERNAME);
            user.setDisplayName(DISPLAY_NAME);
            user.setEmail(null);
            user.setPasswordHash(passwordEncoder.encode(initialPassword));
            user.setStatus("active");
            user.setMustChangePassword(Boolean.TRUE);
            userMapper.insertPlatformAdminUser(user);
        }

        Long roleId = userMapper.selectRoleIdByCode(ROLE);
        if (roleId == null) {
            throw new IllegalStateException("PLATFORM_ADMIN role is missing");
        }
        userMapper.insertUserRole(user.getId(), roleId);
    }
}
