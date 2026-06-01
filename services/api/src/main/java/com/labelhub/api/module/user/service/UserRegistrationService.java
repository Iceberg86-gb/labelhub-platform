package com.labelhub.api.module.user.service;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    private static final String DEFAULT_ROLE = "LABELER";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserEntity register(UserRegistrationCommand command) {
        String username = requireText(command.username(), "username", "Username is required");
        String displayName = requireText(command.displayName(), "displayName", "Display name is required");
        String email = blankToNull(command.email());
        String password = command.password();
        if (password == null || password.length() < 8) {
            throw new InvalidUserRegistrationException("password", "Password must be at least 8 characters");
        }
        if (userMapper.selectActiveByUsername(username) != null) {
            throw new DuplicateUserException("username", "Username already exists");
        }
        if (email != null && userMapper.selectByEmail(email) != null) {
            throw new DuplicateUserException("email", "Email already exists");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("active");
        userMapper.insertUser(user);

        Long roleId = userMapper.selectRoleIdByCode(DEFAULT_ROLE);
        if (roleId == null) {
            throw new IllegalStateException("LABELER role is missing");
        }
        userMapper.insertUserRole(user.getId(), roleId);
        return user;
    }

    private static String requireText(String value, String field, String reason) {
        String text = blankToNull(value);
        if (text == null) {
            throw new InvalidUserRegistrationException(field, reason);
        }
        return text;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
