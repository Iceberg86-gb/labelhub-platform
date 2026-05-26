package com.labelhub.api.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.api.module.user.entity.UserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("""
        SELECT id, username, display_name, email, password_hash, status, created_at, updated_at
        FROM users
        WHERE username = #{username}
        LIMIT 1
        """)
    UserEntity selectByUsername(String username);

    @Select("""
        SELECT r.code
        FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        WHERE ur.user_id = #{userId}
        ORDER BY r.code ASC
        """)
    List<String> selectRoleCodesByUserId(Long userId);
}
