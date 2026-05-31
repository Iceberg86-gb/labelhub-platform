package com.labelhub.api.module.quality.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.quality.entity.ReviewActionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReviewActionMapper {

    @Insert("""
        INSERT INTO review_actions
        (submission_id, task_id, reviewer_id, review_level, action, structured_reason,
         comment_text, round_no, diff_snapshot, created_at)
        VALUES
        (#{submissionId}, #{taskId}, #{reviewerId}, #{reviewLevel}, #{action},
         #{structuredReason, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{commentText}, #{roundNo},
         #{diffSnapshot, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReviewActionEntity entity);

    @Select("SELECT COALESCE(MAX(round_no), 0) + 1 FROM review_actions WHERE submission_id = #{submissionId}")
    Integer selectNextRoundNo(@Param("submissionId") Long submissionId);

    @Select("""
        SELECT id, submission_id, task_id, reviewer_id, review_level, action, structured_reason,
               comment_text, round_no, diff_snapshot, created_at
        FROM review_actions
        WHERE id = #{id}
        """)
    @Results(id = "reviewActionResultMap", value = {
        @Result(column = "submission_id", property = "submissionId"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "reviewer_id", property = "reviewerId"),
        @Result(column = "review_level", property = "reviewLevel"),
        @Result(column = "structured_reason", property = "structuredReason", typeHandler = JacksonTypeHandler.class),
        @Result(column = "comment_text", property = "commentText"),
        @Result(column = "round_no", property = "roundNo"),
        @Result(column = "diff_snapshot", property = "diffSnapshot", typeHandler = JacksonTypeHandler.class),
        @Result(column = "created_at", property = "createdAt")
    })
    ReviewActionEntity selectById(@Param("id") Long id);
}
