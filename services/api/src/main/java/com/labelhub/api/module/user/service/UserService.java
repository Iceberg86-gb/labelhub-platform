package com.labelhub.api.module.user.service;

import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import com.labelhub.api.module.task.service.PagedResult;
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

    public PagedResult<UserRoleUpdateResult> listActiveUsers(int page, int size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        long offset = (safePage - 1) * safeSize;
        List<UserRoleUpdateResult> items = userMapper.selectActiveUsersPage(offset, safeSize).stream()
            .map(user -> new UserRoleUpdateResult(user, loadRoles(user.getId())))
            .toList();
        Long total = userMapper.countActiveUsers();
        return new PagedResult<>(items, total == null ? 0 : total, safePage, safeSize);
    }
}
