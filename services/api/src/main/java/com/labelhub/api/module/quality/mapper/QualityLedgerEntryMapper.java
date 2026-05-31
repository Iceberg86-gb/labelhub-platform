package com.labelhub.api.module.quality.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
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
public interface QualityLedgerEntryMapper {

    @Insert("""
        INSERT INTO quality_ledger_entries
        (submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at)
        VALUES
        (#{submissionId}, #{taskId}, #{evidenceType}, #{actorType}, #{actorId}, #{aiCallId},
         #{payload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QualityLedgerEntryEntity entity);

    @Select("""
        SELECT id, submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at
        FROM quality_ledger_entries
        WHERE id = #{id}
        """)
    @Results(id = "qualityLedgerEntryResultMap", value = {
        @Result(column = "submission_id", property = "submissionId"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "evidence_type", property = "evidenceType"),
        @Result(column = "actor_type", property = "actorType"),
        @Result(column = "actor_id", property = "actorId"),
        @Result(column = "ai_call_id", property = "aiCallId"),
        @Result(column = "payload", property = "payload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "created_at", property = "createdAt")
    })
    QualityLedgerEntryEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at
        FROM quality_ledger_entries
        WHERE submission_id = #{submissionId}
        ORDER BY created_at ASC, id ASC
        LIMIT #{size} OFFSET #{offset}
        """)
    @ResultMap("qualityLedgerEntryResultMap")
    List<QualityLedgerEntryEntity> selectBySubmissionId(
        @Param("submissionId") Long submissionId,
        @Param("offset") Long offset,
        @Param("size") Long size
    );

    @Select("SELECT COUNT(*) FROM quality_ledger_entries WHERE submission_id = #{submissionId}")
    Long selectCountBySubmissionId(@Param("submissionId") Long submissionId);

    @Select("""
        SELECT id, submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at
        FROM quality_ledger_entries
        WHERE submission_id = #{submissionId}
          AND evidence_type = 'reviewer_overall_verdict'
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """)
    @ResultMap("qualityLedgerEntryResultMap")
    QualityLedgerEntryEntity selectLatestReviewerOverallVerdict(@Param("submissionId") Long submissionId);

    @Select("""
        SELECT qle.id, qle.submission_id, qle.task_id, qle.evidence_type, qle.actor_type,
               qle.actor_id, qle.ai_call_id, qle.payload, qle.created_at
        FROM quality_ledger_entries qle
        JOIN submissions s ON s.id = qle.submission_id
        WHERE s.session_id = #{sessionId}
          AND qle.evidence_type = 'reviewer_overall_verdict'
          AND JSON_UNQUOTE(JSON_EXTRACT(qle.payload, '$.verdict')) = 'reject'
        ORDER BY qle.created_at DESC, qle.id DESC
        LIMIT 1
        """)
    @ResultMap("qualityLedgerEntryResultMap")
    QualityLedgerEntryEntity selectLatestReviewerRejectBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
        SELECT s.id, s.task_id, t.title AS task_title, s.labeler_id, s.schema_version_id,
               s.status AS status_code, s.created_at AS submitted_at,
               latest.id AS derived_from_entry_id,
               JSON_UNQUOTE(JSON_EXTRACT(latest.payload, '$.verdict')) AS reviewer_verdict,
               JSON_UNQUOTE(JSON_EXTRACT(latest_ai.payload, '$.recommendation')) AS ai_recommendation
        FROM submissions s
        JOIN tasks t ON t.id = s.task_id
        LEFT JOIN (
            SELECT ranked.*
            FROM (
                SELECT qle.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY qle.submission_id
                           ORDER BY qle.created_at DESC, qle.id DESC
                       ) AS rn
                FROM quality_ledger_entries qle
                WHERE qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked
            WHERE ranked.rn = 1
        ) latest ON latest.submission_id = s.id
        LEFT JOIN (
            SELECT ranked_ai.*
            FROM (
                SELECT qle.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY qle.submission_id
                           ORDER BY qle.created_at DESC, qle.id DESC
                       ) AS rn
                FROM quality_ledger_entries qle
                WHERE qle.evidence_type = 'ai_overall_recommendation'
            ) ranked_ai
            WHERE ranked_ai.rn = 1
        ) latest_ai ON latest_ai.submission_id = s.id
        WHERE s.status = #{status}
          AND (
              #{verdict} IS NULL
              OR (#{verdict} = 'pending' AND latest.id IS NULL)
              OR (#{verdict} = 'approved' AND JSON_UNQUOTE(JSON_EXTRACT(latest.payload, '$.verdict')) = 'approve')
              OR (#{verdict} = 'rejected' AND JSON_UNQUOTE(JSON_EXTRACT(latest.payload, '$.verdict')) = 'reject')
          )
        ORDER BY s.created_at DESC, s.id DESC
        LIMIT #{size} OFFSET #{offset}
        """)
    @Results(id = "reviewerSubmissionQueueRowMap", value = {
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "task_title", property = "taskTitle"),
        @Result(column = "labeler_id", property = "labelerId"),
        @Result(column = "schema_version_id", property = "schemaVersionId"),
        @Result(column = "status_code", property = "statusCode"),
        @Result(column = "submitted_at", property = "submittedAt"),
        @Result(column = "derived_from_entry_id", property = "derivedFromEntryId"),
        @Result(column = "reviewer_verdict", property = "reviewerVerdict"),
        @Result(column = "ai_recommendation", property = "aiRecommendation")
    })
    List<ReviewerSubmissionQueueRow> selectReviewerQueuePage(
        @Param("status") String status,
        @Param("verdict") String verdict,
        @Param("offset") Long offset,
        @Param("size") Long size
    );

    @Select("""
        SELECT COUNT(*)
        FROM submissions s
        LEFT JOIN (
            SELECT ranked.*
            FROM (
                SELECT qle.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY qle.submission_id
                           ORDER BY qle.created_at DESC, qle.id DESC
                       ) AS rn
                FROM quality_ledger_entries qle
                WHERE qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked
            WHERE ranked.rn = 1
        ) latest ON latest.submission_id = s.id
        WHERE s.status = #{status}
          AND (
              #{verdict} IS NULL
              OR (#{verdict} = 'pending' AND latest.id IS NULL)
              OR (#{verdict} = 'approved' AND JSON_UNQUOTE(JSON_EXTRACT(latest.payload, '$.verdict')) = 'approve')
              OR (#{verdict} = 'rejected' AND JSON_UNQUOTE(JSON_EXTRACT(latest.payload, '$.verdict')) = 'reject')
          )
        """)
    Long selectReviewerQueueCount(
        @Param("status") String status,
        @Param("verdict") String verdict
    );

    @Select("""
        <script>
        SELECT id, submission_id, task_id, evidence_type, actor_type, actor_id, ai_call_id, payload, created_at
        FROM quality_ledger_entries
        WHERE submission_id IN
        <foreach collection="submissionIds" item="submissionId" open="(" separator="," close=")">
            #{submissionId}
        </foreach>
        ORDER BY submission_id ASC, created_at ASC, id ASC
        </script>
        """)
    @ResultMap("qualityLedgerEntryResultMap")
    List<QualityLedgerEntryEntity> selectBySubmissionIdsOrdered(@Param("submissionIds") List<Long> submissionIds);
}
