package com.labelhub.api.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.api.module.auth.entity.RefreshTokenEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {

    @Insert("""
        INSERT INTO refresh_tokens (user_id, token_hash, issued_at, expires_at, revoked_at)
        VALUES (#{userId}, #{tokenHash}, #{issuedAt}, #{expiresAt}, #{revokedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRefreshToken(RefreshTokenEntity token);

    @Select("""
        SELECT id, user_id, token_hash, issued_at, expires_at, revoked_at, created_at
        FROM refresh_tokens
        WHERE token_hash = #{tokenHash}
        LIMIT 1
        """)
    RefreshTokenEntity selectByTokenHash(String tokenHash);

    @Update("""
        UPDATE refresh_tokens
        SET revoked_at = #{revokedAt}
        WHERE id = #{id} AND revoked_at IS NULL
        """)
    int revokeById(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt);

    @Update("""
        UPDATE refresh_tokens
        SET revoked_at = #{revokedAt}
        WHERE token_hash = #{tokenHash} AND revoked_at IS NULL
        """)
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("revokedAt") LocalDateTime revokedAt);

    @Update("""
        UPDATE refresh_tokens
        SET revoked_at = #{revokedAt}
        WHERE user_id = #{userId} AND revoked_at IS NULL
        """)
    int revokeActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
