package com.labelhub.api.module.quality.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.quality.entity.SeniorReviewCaseEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeniorReviewCaseMapper {

    @Insert("""
        INSERT INTO senior_review_cases
        (submission_id, task_id, case_key, case_type, source_signal, status, priority,
         reviewer_verdict_entry_id, ai_overall_entry_id, reviewer_id, senior_reviewer_id,
         resolution, reason, payload, accountability, created_at, updated_at, resolved_at)
        VALUES
        (#{submissionId}, #{taskId}, #{caseKey}, #{caseType}, #{sourceSignal}, #{status}, #{priority},
         #{reviewerVerdictEntryId}, #{aiOverallEntryId}, #{reviewerId}, #{seniorReviewerId},
         #{resolution}, #{reason},
         #{payload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{accountability, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{createdAt}, #{updatedAt}, #{resolvedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SeniorReviewCaseEntity entity);

    @Select("""
        SELECT src.id, src.submission_id, src.task_id, t.title AS task_title,
               ls.name AS schema_name, sv.version_no AS schema_version_number,
               src.case_key, src.case_type, src.source_signal, src.status, src.priority,
               src.reviewer_verdict_entry_id, src.ai_overall_entry_id, src.reviewer_id, src.senior_reviewer_id,
               src.resolution, src.reason, src.payload, src.accountability, src.created_at, src.updated_at, src.resolved_at
        FROM senior_review_cases src
        JOIN submissions s ON s.id = src.submission_id
        JOIN tasks t ON t.id = src.task_id
        JOIN schema_versions sv ON sv.id = s.schema_version_id
        JOIN label_schemas ls ON ls.id = sv.schema_id
        WHERE src.id = #{id}
        """)
    @Results(id = "seniorReviewCaseResultMap", value = {
        @Result(column = "submission_id", property = "submissionId"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "task_title", property = "taskTitle"),
        @Result(column = "schema_name", property = "schemaName"),
        @Result(column = "schema_version_number", property = "schemaVersionNumber"),
        @Result(column = "case_key", property = "caseKey"),
        @Result(column = "case_type", property = "caseType"),
        @Result(column = "source_signal", property = "sourceSignal"),
        @Result(column = "reviewer_verdict_entry_id", property = "reviewerVerdictEntryId"),
        @Result(column = "ai_overall_entry_id", property = "aiOverallEntryId"),
        @Result(column = "reviewer_id", property = "reviewerId"),
        @Result(column = "senior_reviewer_id", property = "seniorReviewerId"),
        @Result(column = "payload", property = "payload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "accountability", property = "accountability", typeHandler = JacksonTypeHandler.class),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "updated_at", property = "updatedAt"),
        @Result(column = "resolved_at", property = "resolvedAt")
    })
    SeniorReviewCaseEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, submission_id, task_id, case_key, case_type, source_signal, status, priority,
               reviewer_verdict_entry_id, ai_overall_entry_id, reviewer_id, senior_reviewer_id,
               resolution, reason, payload, accountability, created_at, updated_at, resolved_at
        FROM senior_review_cases
        WHERE case_key = #{caseKey}
        LIMIT 1
        """)
    @ResultMap("seniorReviewCaseResultMap")
    SeniorReviewCaseEntity selectByCaseKey(@Param("caseKey") String caseKey);

    @Select("""
        SELECT COUNT(*)
        FROM senior_review_cases
        WHERE submission_id = #{submissionId}
          AND status IN ('pending_reviewer', 'open')
        """)
    Long selectOpenCountBySubmissionId(@Param("submissionId") Long submissionId);

    @Select("""
        SELECT id, submission_id, task_id, case_key, case_type, source_signal, status, priority,
               reviewer_verdict_entry_id, ai_overall_entry_id, reviewer_id, senior_reviewer_id,
               resolution, reason, payload, accountability, created_at, updated_at, resolved_at
        FROM senior_review_cases
        WHERE submission_id = #{submissionId}
          AND status = 'resolved'
        ORDER BY resolved_at DESC, id DESC
        LIMIT 1
        """)
    @ResultMap("seniorReviewCaseResultMap")
    SeniorReviewCaseEntity selectLatestResolvedBySubmissionId(@Param("submissionId") Long submissionId);

    @Update("""
        UPDATE senior_review_cases
        SET status = 'open',
            reviewer_verdict_entry_id = #{reviewerVerdictEntryId},
            reviewer_id = #{reviewerId},
            updated_at = #{updatedAt}
        WHERE submission_id = #{submissionId}
          AND status = 'pending_reviewer'
        """)
    int openPendingCasesForReviewerApprove(
        @Param("submissionId") Long submissionId,
        @Param("reviewerVerdictEntryId") Long reviewerVerdictEntryId,
        @Param("reviewerId") Long reviewerId,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    @Update("""
        UPDATE senior_review_cases
        SET status = 'canceled',
            reviewer_verdict_entry_id = #{reviewerVerdictEntryId},
            reviewer_id = #{reviewerId},
            updated_at = #{updatedAt}
        WHERE submission_id = #{submissionId}
          AND status = 'pending_reviewer'
        """)
    int cancelPendingCasesForReviewerReject(
        @Param("submissionId") Long submissionId,
        @Param("reviewerVerdictEntryId") Long reviewerVerdictEntryId,
        @Param("reviewerId") Long reviewerId,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    @Update("""
        UPDATE senior_review_cases
        SET status = 'resolved',
            senior_reviewer_id = #{seniorReviewerId},
            resolution = #{resolution},
            reason = #{reason},
            accountability = #{accountability, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
            updated_at = #{resolvedAt},
            resolved_at = #{resolvedAt}
        WHERE id = #{id}
          AND status = 'open'
        """)
    int resolveCase(
        @Param("id") Long id,
        @Param("seniorReviewerId") Long seniorReviewerId,
        @Param("resolution") String resolution,
        @Param("reason") String reason,
        @Param("accountability") Map<String, Object> accountability,
        @Param("resolvedAt") LocalDateTime resolvedAt
    );

    @Select("""
        SELECT src.id, src.submission_id, src.task_id, t.title AS task_title,
               ls.name AS schema_name, sv.version_no AS schema_version_number,
               src.case_key, src.case_type, src.source_signal, src.status, src.priority,
               src.reviewer_verdict_entry_id, src.ai_overall_entry_id, src.reviewer_id, src.senior_reviewer_id,
               src.resolution, src.reason, src.payload, src.accountability, src.created_at, src.updated_at, src.resolved_at
        FROM senior_review_cases src
        JOIN submissions s ON s.id = src.submission_id
        JOIN tasks t ON t.id = src.task_id
        JOIN schema_versions sv ON sv.id = s.schema_version_id
        JOIN label_schemas ls ON ls.id = sv.schema_id
        WHERE src.status = #{status}
        ORDER BY src.created_at ASC, src.id ASC
        LIMIT #{size} OFFSET #{offset}
        """)
    @ResultMap("seniorReviewCaseResultMap")
    List<SeniorReviewCaseEntity> selectQueuePage(
        @Param("status") String status,
        @Param("offset") Long offset,
        @Param("size") Long size
    );

    @Select("SELECT COUNT(*) FROM senior_review_cases WHERE status = #{status}")
    Long selectQueueCount(@Param("status") String status);
}
