package com.labelhub.api.module.admin.mapper;

import com.labelhub.api.module.admin.entity.AuditLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper {

    @Insert("""
        INSERT INTO audit_logs (actor_type, actor_id, action, resource_type, resource_id, payload, payload_hash)
        VALUES (#{actorType}, #{actorId}, #{action}, #{resourceType}, #{resourceId}, #{payload}, #{payloadHash})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLogEntity auditLog);

    @Select("""
        SELECT id, actor_type, actor_id, action, resource_type, resource_id, payload, payload_hash, created_at
        FROM audit_logs
        WHERE resource_type = #{resourceType} AND resource_id = #{resourceId}
        ORDER BY created_at ASC, id ASC
        """)
    List<AuditLogEntity> selectByResource(@Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);
}
