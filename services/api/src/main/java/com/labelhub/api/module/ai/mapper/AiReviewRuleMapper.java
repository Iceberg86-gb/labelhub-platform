package com.labelhub.api.module.ai.mapper;

import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiReviewRuleMapper {

    @Insert("""
        INSERT INTO ai_review_rules
        (task_id, version_no, current_prompt_version_id, dimensions_json, threshold, status, created_by, created_at, activated_at)
        VALUES
        (#{taskId}, #{versionNumber}, #{currentPromptVersionId}, #{dimensionsJson}, #{threshold}, #{statusCode}, #{createdBy}, NOW(3), #{activatedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiReviewRuleEntity entity);

    @Select("""
        SELECT id, task_id, version_no, current_prompt_version_id, dimensions_json,
               threshold, status, created_by, created_at, activated_at
        FROM ai_review_rules
        WHERE id = #{id}
        """)
    @Results(id = "aiReviewRuleResultMap", value = {
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "version_no", property = "versionNumber"),
        @Result(column = "current_prompt_version_id", property = "currentPromptVersionId"),
        @Result(column = "dimensions_json", property = "dimensionsJson"),
        @Result(column = "status", property = "statusCode"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "activated_at", property = "activatedAt")
    })
    AiReviewRuleEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, task_id, version_no, current_prompt_version_id, dimensions_json,
               threshold, status, created_by, created_at, activated_at
        FROM ai_review_rules
        WHERE task_id = #{taskId}
        ORDER BY version_no DESC
        """)
    @Results(id = "aiReviewRulesByTaskResultMap", value = {
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "version_no", property = "versionNumber"),
        @Result(column = "current_prompt_version_id", property = "currentPromptVersionId"),
        @Result(column = "dimensions_json", property = "dimensionsJson"),
        @Result(column = "status", property = "statusCode"),
        @Result(column = "created_by", property = "createdBy"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "activated_at", property = "activatedAt")
    })
    List<AiReviewRuleEntity> selectByTaskId(@Param("taskId") Long taskId);

    @Select("""
        SELECT COALESCE(MAX(version_no), 0)
        FROM ai_review_rules
        WHERE task_id = #{taskId}
        """)
    Integer selectMaxVersionByTaskId(@Param("taskId") Long taskId);

    @Update("""
        UPDATE ai_review_rules
        SET status = 'published',
            activated_at = COALESCE(activated_at, NOW(3))
        WHERE id = #{id}
        """)
    int markPublished(@Param("id") Long id);
}
