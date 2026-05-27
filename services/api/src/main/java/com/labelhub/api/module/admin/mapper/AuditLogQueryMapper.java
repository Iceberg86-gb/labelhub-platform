package com.labelhub.api.module.admin.mapper;

import com.labelhub.api.module.admin.audit.AuditLogFilterCriteria;
import com.labelhub.api.module.admin.entity.AuditLogRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogQueryMapper {

    String AUDIT_ROW_SELECT = """
        SELECT a.id, a.actor_type, a.actor_id, u.display_name AS actor_display_name, a.action,
               a.resource_type, a.resource_id, a.payload, a.payload_hash, a.created_at
        FROM audit_logs a
        LEFT JOIN users u ON u.id = a.actor_id AND a.actor_type = 'user'
        """;

    String FILTER_WHERE = """
        <where>
          <if test="criteria.actionTypes != null and criteria.actionTypes.size() > 0">
            AND a.action IN
            <foreach collection="criteria.actionTypes" item="actionType" open="(" separator="," close=")">
              #{actionType}
            </foreach>
          </if>
          <if test="criteria.resourceTypes != null and criteria.resourceTypes.size() > 0">
            AND a.resource_type IN
            <foreach collection="criteria.resourceTypes" item="resourceType" open="(" separator="," close=")">
              #{resourceType}
            </foreach>
          </if>
          <if test="criteria.actorUserId != null">
            AND a.actor_type = 'user'
            AND a.actor_id = #{criteria.actorUserId}
          </if>
          <if test="criteria.resourceId != null">
            AND a.resource_id = #{criteria.resourceId}
          </if>
          <if test="criteria.from != null">
            AND a.created_at &gt;= #{criteria.from}
          </if>
          <if test="criteria.to != null">
            AND a.created_at &lt;= #{criteria.to}
          </if>
        </where>
        """;

    @Select({"<script>", AUDIT_ROW_SELECT, FILTER_WHERE, """
        ORDER BY a.created_at DESC, a.id DESC
        LIMIT #{criteria.size} OFFSET #{offset}
        </script>
        """})
    @Results(id = "auditLogRowResultMap", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "actor_type", property = "actorType"),
        @Result(column = "actor_id", property = "actorId"),
        @Result(column = "actor_display_name", property = "actorDisplayName"),
        @Result(column = "action", property = "action"),
        @Result(column = "resource_type", property = "resourceType"),
        @Result(column = "resource_id", property = "resourceId"),
        @Result(column = "payload", property = "payload"),
        @Result(column = "payload_hash", property = "payloadHash"),
        @Result(column = "created_at", property = "createdAt")
    })
    List<AuditLogRow> selectFiltered(@Param("criteria") AuditLogFilterCriteria criteria, @Param("offset") long offset);

    @Select({"<script>", """
        SELECT COUNT(*)
        FROM audit_logs a
        """, FILTER_WHERE, "</script>"})
    Long countFiltered(@Param("criteria") AuditLogFilterCriteria criteria);

    @Select({"<script>", AUDIT_ROW_SELECT, FILTER_WHERE, """
        ORDER BY a.created_at DESC, a.id DESC
        LIMIT #{limit}
        </script>
        """})
    @ResultMap("auditLogRowResultMap")
    List<AuditLogRow> streamFiltered(@Param("criteria") AuditLogFilterCriteria criteria, @Param("limit") int limit);
}
