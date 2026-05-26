package com.labelhub.api.module.ai.mapper;

import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiCallInFieldMapper {

    @Insert("""
        INSERT INTO ai_calls_in_field
        (submission_id, field_path, ai_call_id, accepted, user_modified_after, ordinal, created_at)
        VALUES
        (#{submissionId}, #{fieldPath}, #{aiCallId}, #{accepted}, #{userModifiedAfter}, #{ordinal}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiCallInFieldEntity entity);

    @Select("""
        SELECT id, submission_id, field_path, ai_call_id, accepted, user_modified_after, ordinal, created_at
        FROM ai_calls_in_field
        WHERE submission_id = #{submissionId}
        ORDER BY field_path ASC, ordinal ASC
        """)
    @Results(id = "aiCallInFieldResultMap", value = {
        @Result(column = "submission_id", property = "submissionId"),
        @Result(column = "field_path", property = "fieldPath"),
        @Result(column = "ai_call_id", property = "aiCallId"),
        @Result(column = "user_modified_after", property = "userModifiedAfter"),
        @Result(column = "created_at", property = "createdAt")
    })
    List<AiCallInFieldEntity> selectBySubmissionId(@Param("submissionId") Long submissionId);

    @Select("""
        SELECT COALESCE(MAX(ordinal), 0)
        FROM ai_calls_in_field
        WHERE submission_id = #{submissionId} AND field_path = #{fieldPath}
        """)
    Integer selectMaxOrdinal(@Param("submissionId") Long submissionId, @Param("fieldPath") String fieldPath);

    @Select("""
        SELECT id, submission_id, field_path, ai_call_id, accepted, user_modified_after, ordinal, created_at
        FROM ai_calls_in_field
        WHERE ai_call_id = #{aiCallId}
        ORDER BY ordinal ASC
        """)
    @ResultMap("aiCallInFieldResultMap")
    List<AiCallInFieldEntity> selectByAiCallId(@Param("aiCallId") Long aiCallId);

    @Select("""
        SELECT id, submission_id, field_path, ai_call_id, accepted, user_modified_after, ordinal, created_at
        FROM ai_calls_in_field
        WHERE submission_id = #{submissionId} AND ai_call_id = #{aiCallId}
        ORDER BY ordinal ASC
        """)
    @ResultMap("aiCallInFieldResultMap")
    List<AiCallInFieldEntity> selectBySubmissionAndAiCall(
        @Param("submissionId") Long submissionId,
        @Param("aiCallId") Long aiCallId
    );

    @Select("""
        <script>
        SELECT id, submission_id, field_path, ai_call_id, accepted, user_modified_after, ordinal, created_at
        FROM ai_calls_in_field
        WHERE submission_id IN
        <foreach collection="submissionIds" item="submissionId" open="(" separator="," close=")">
            #{submissionId}
        </foreach>
        ORDER BY submission_id ASC, field_path ASC, ordinal ASC, id ASC
        </script>
        """)
    @ResultMap("aiCallInFieldResultMap")
    List<AiCallInFieldEntity> selectBySubmissionIdsOrdered(@Param("submissionIds") List<Long> submissionIds);
}
