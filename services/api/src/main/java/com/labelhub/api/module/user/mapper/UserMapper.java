package com.labelhub.api.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.api.module.user.entity.UserEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
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
        SELECT id, username, display_name, email, password_hash, status, created_at, updated_at
        FROM users
        WHERE id = #{userId}
        LIMIT 1
        """)
    UserEntity selectUserById(Long userId);

    @Select("""
        SELECT id, username, display_name, email, password_hash, status, created_at, updated_at
        FROM users
        WHERE email = #{email}
        LIMIT 1
        """)
    UserEntity selectByEmail(String email);

    @Insert("""
        INSERT INTO users (username, display_name, email, password_hash, status)
        VALUES (#{username}, #{displayName}, #{email}, #{passwordHash}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(UserEntity user);

    @Select("""
        SELECT r.code
        FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        WHERE ur.user_id = #{userId}
        ORDER BY r.code ASC
        """)
    List<String> selectRoleCodesByUserId(Long userId);

    @Select("""
        SELECT id
        FROM roles
        WHERE code = #{code}
        LIMIT 1
        """)
    Long selectRoleIdByCode(String code);

    @Insert("""
        INSERT IGNORE INTO user_roles (user_id, role_id)
        VALUES (#{userId}, #{roleId})
        """)
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("""
        DELETE FROM user_roles
        WHERE user_id = #{userId} AND role_id = #{roleId}
        """)
    int deleteUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
