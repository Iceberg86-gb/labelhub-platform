package com.labelhub.api.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.task.entity.TaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {

    @Select("""
        SELECT id, title, description, instruction_rich_text, tags, reward_rule,
               deadline_at, quota_total, quota_claimed, status, owner_id,
               current_schema_version_id, current_dataset_id, current_ai_review_rule_id,
               created_at, updated_at
        FROM tasks
        WHERE status = 'published'
          AND current_dataset_id IS NOT NULL
          AND current_schema_version_id IS NOT NULL
          AND quota_claimed < quota_total
          AND deadline_at > NOW(3)
          AND EXISTS (
            SELECT 1
            FROM dataset_items di
            WHERE di.dataset_id = tasks.current_dataset_id
              AND di.task_id = tasks.id
              AND di.status = 'available'
          )
        ORDER BY created_at DESC
        """)
    @Results(id = "taskMarketplaceResultMap", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "title", property = "title"),
        @Result(column = "description", property = "description"),
        @Result(column = "instruction_rich_text", property = "instructionRichText"),
        @Result(column = "tags", property = "tags", typeHandler = JacksonTypeHandler.class),
        @Result(column = "reward_rule", property = "rewardRule", typeHandler = JacksonTypeHandler.class),
        @Result(column = "deadline_at", property = "deadlineAt"),
        @Result(column = "quota_total", property = "quotaTotal"),
        @Result(column = "quota_claimed", property = "quotaClaimed"),
        @Result(column = "status", property = "statusCode"),
        @Result(column = "owner_id", property = "ownerId"),
        @Result(column = "current_schema_version_id", property = "currentSchemaVersionId"),
        @Result(column = "current_dataset_id", property = "currentDatasetId"),
        @Result(column = "current_ai_review_rule_id", property = "currentAiReviewRuleId"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "updated_at", property = "updatedAt")
    })
    IPage<TaskEntity> selectMarketplace(IPage<TaskEntity> page);

    @Select("""
        SELECT id, title, description, instruction_rich_text, tags, reward_rule,
               deadline_at, quota_total, quota_claimed, status, owner_id,
               current_schema_version_id, current_dataset_id, current_ai_review_rule_id,
               created_at, updated_at
        FROM tasks
        WHERE id = #{taskId}
        FOR UPDATE
        """)
    @ResultMap("taskMarketplaceResultMap")
    TaskEntity selectByIdForUpdate(@Param("taskId") Long taskId);

    @Update("""
        UPDATE tasks
        SET quota_claimed = quota_claimed + 1,
            updated_at = NOW(3)
        WHERE id = #{taskId}
          AND status = 'published'
          AND quota_claimed < quota_total
          AND deadline_at > NOW(3)
          AND current_schema_version_id IS NOT NULL
          AND current_dataset_id IS NOT NULL
        """)
    int incrementQuotaClaimedIfAvailable(@Param("taskId") Long taskId);

}
