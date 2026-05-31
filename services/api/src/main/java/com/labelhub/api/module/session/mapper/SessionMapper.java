package com.labelhub.api.module.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.session.entity.SessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {

    @Select("""
        SELECT id, task_id, dataset_item_id, labeler_id, schema_version_id,
               claim_snapshot, status, claimed_at, submitted_at
        FROM sessions
        WHERE labeler_id = #{labelerId}
          AND dataset_item_id = #{datasetItemId}
        """)
    @Results(id = "sessionResultMap", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "dataset_item_id", property = "datasetItemId"),
        @Result(column = "labeler_id", property = "labelerId"),
        @Result(column = "schema_version_id", property = "schemaVersionId"),
        @Result(column = "claim_snapshot", property = "claimSnapshot", typeHandler = JacksonTypeHandler.class),
        @Result(column = "status", property = "status"),
        @Result(column = "claimed_at", property = "claimedAt"),
        @Result(column = "submitted_at", property = "submittedAt")
    })
    SessionEntity selectByLabelerAndItem(@Param("labelerId") Long labelerId, @Param("datasetItemId") Long datasetItemId);

    @Select("""
        SELECT id, task_id, dataset_item_id, labeler_id, schema_version_id,
               claim_snapshot, status, claimed_at, submitted_at
        FROM sessions
        WHERE id = #{id}
        FOR UPDATE
        """)
    @ResultMap("sessionResultMap")
    SessionEntity selectByIdForUpdate(@Param("id") Long id);

    @Select("""
        <script>
        SELECT id, task_id, dataset_item_id, labeler_id, schema_version_id,
               claim_snapshot, status, claimed_at, submitted_at
        FROM sessions
        WHERE labeler_id = #{labelerId}
        <if test="statusFilter != null and statusFilter != ''">
          AND status = #{statusFilter}
        </if>
        ORDER BY claimed_at DESC, id DESC
        </script>
        """)
    @ResultMap("sessionResultMap")
    IPage<SessionEntity> selectByLabeler(
        IPage<SessionEntity> page,
        @Param("labelerId") Long labelerId,
        @Param("statusFilter") String statusFilter
    );

    @Update("UPDATE sessions SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
