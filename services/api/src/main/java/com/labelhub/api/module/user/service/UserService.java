package com.labelhub.api.module.user.service;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Optional<UserEntity> findByUsername(String username) {
        UserEntity user = userMapper.selectByUsername(username);
        if (user == null || !"active".equals(user.getStatus())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public List<String> loadRoles(Long userId) {
        return userMapper.selectRoleCodesByUserId(userId);
    }
}
