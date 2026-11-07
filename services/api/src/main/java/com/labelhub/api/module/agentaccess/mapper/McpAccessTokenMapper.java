package com.labelhub.api.module.agentaccess.mapper;

import com.labelhub.api.module.agentaccess.entity.McpAccessTokenEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** ADR-022 §2. Current-state table: the only update is revocation (revoked_at is set once). */
@Mapper
public interface McpAccessTokenMapper {

    String COLUMNS = "id, task_id, owner_id, name, token_hash, token_prefix, expires_at, created_at, revoked_at, revoked_by";

    @Insert("""
        INSERT INTO mcp_access_tokens(task_id, owner_id, name, token_hash, token_prefix, expires_at, created_at)
        VALUES (#{taskId}, #{ownerId}, #{name}, #{tokenHash}, #{tokenPrefix}, #{expiresAt}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(McpAccessTokenEntity token);

    @Select("SELECT " + COLUMNS + " FROM mcp_access_tokens WHERE id = #{id}")
    McpAccessTokenEntity selectById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM mcp_access_tokens WHERE token_hash = #{tokenHash}")
    McpAccessTokenEntity selectByHash(@Param("tokenHash") String tokenHash);

    @Select("SELECT " + COLUMNS + " FROM mcp_access_tokens WHERE task_id = #{taskId} ORDER BY created_at DESC, id DESC")
    List<McpAccessTokenEntity> selectByTaskId(@Param("taskId") Long taskId);

    @Update("""
        UPDATE mcp_access_tokens SET revoked_at = #{revokedAt}, revoked_by = #{revokedBy}
        WHERE id = #{id} AND revoked_at IS NULL
        """)
    int revoke(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt, @Param("revokedBy") Long revokedBy);
}
