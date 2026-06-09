package com.labelhub.api.module.schema.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
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
public interface SubmissionMapper {

    @Insert("""
        INSERT INTO submissions
        (session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
         answer_payload, provenance, content_hash, status, created_at)
        VALUES
        (#{sessionId}, #{taskId}, #{datasetItemId}, #{labelerId}, #{schemaVersionId},
         #{answerPayload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{provenance, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{contentHash}, #{statusCode}, NOW(3))
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SubmissionEntity entity);

    @Select("""
        SELECT id, session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
               answer_payload, provenance, content_hash, status AS status_code,
               created_at, superseded_by_id
        FROM submissions
        WHERE id = #{id}
        """)
    @Results(id = "submissionResultMap", value = {
        @Result(column = "answer_payload", property = "answerPayload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "provenance", property = "provenance", typeHandler = JacksonTypeHandler.class)
    })
    SubmissionEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
               answer_payload, provenance, content_hash, status AS status_code,
               created_at, superseded_by_id
        FROM submissions
        WHERE task_id = #{taskId}
        ORDER BY created_at DESC, id DESC
        LIMIT #{size} OFFSET #{offset}
        """)
    @ResultMap("submissionResultMap")
    List<SubmissionEntity> selectPageByTaskId(
        @Param("taskId") Long taskId,
        @Param("offset") Long offset,
        @Param("size") Long size
    );

    @Select("SELECT COUNT(*) FROM submissions WHERE task_id = #{taskId}")
    Long selectCountByTaskId(@Param("taskId") Long taskId);

    @Select("""
        SELECT id, session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
               answer_payload, provenance, content_hash, status AS status_code,
               created_at, superseded_by_id
        FROM submissions
        WHERE task_id = #{taskId}
          AND status = 'submitted'
        ORDER BY id ASC
        """)
    @ResultMap("submissionResultMap")
    List<SubmissionEntity> selectSubmittedByTaskOrderedById(@Param("taskId") Long taskId);

    @Select("""
        SELECT id, session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
               answer_payload, provenance, content_hash, status AS status_code,
               created_at, superseded_by_id
        FROM submissions
        WHERE task_id = #{taskId}
          AND status IN ('submitted', 'under_ai_review')
        ORDER BY id ASC
        """)
    @ResultMap("submissionResultMap")
    List<SubmissionEntity> selectAiPrereviewCandidatesByTaskOrderedById(@Param("taskId") Long taskId);

    @Select("""
        SELECT s.id, s.session_id, s.task_id, s.dataset_item_id, s.labeler_id, s.schema_version_id,
               s.answer_payload, s.provenance, s.content_hash, s.status AS status_code,
               s.created_at, s.superseded_by_id
        FROM submissions s
        JOIN (
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
            SELECT submission_id, COUNT(*) AS open_count
            FROM senior_review_cases
            WHERE status IN ('pending_reviewer', 'open')
            GROUP BY submission_id
        ) open_cases ON open_cases.submission_id = s.id
        LEFT JOIN (
            SELECT ranked.*
            FROM (
                SELECT src.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY src.submission_id
                           ORDER BY src.resolved_at DESC, src.id DESC
                       ) AS rn
                FROM senior_review_cases src
                WHERE src.status = 'resolved'
            ) ranked
            WHERE ranked.rn = 1
        ) latest_resolved ON latest_resolved.submission_id = s.id
        WHERE s.task_id = #{taskId}
          AND s.status = 'submitted'
          AND JSON_UNQUOTE(JSON_EXTRACT(latest.payload, '$.verdict')) = 'approve'
          AND COALESCE(open_cases.open_count, 0) = 0
          AND (
              latest_resolved.id IS NULL
              OR latest_resolved.resolution NOT IN ('overturn_to_reject', 'boundary_rejected')
          )
        ORDER BY s.id ASC
        """)
    @ResultMap("submissionResultMap")
    List<SubmissionEntity> selectApprovedByTaskOrderedById(@Param("taskId") Long taskId);

    @Select("""
        SELECT id, session_id, task_id, dataset_item_id, labeler_id, schema_version_id,
               answer_payload, provenance, content_hash, status AS status_code,
               created_at, superseded_by_id
        FROM submissions
        WHERE session_id = #{sessionId}
          AND status = 'returned_for_revision'
          AND superseded_by_id IS NULL
        ORDER BY id DESC
        LIMIT 1
        FOR UPDATE
        """)
    @ResultMap("submissionResultMap")
    SubmissionEntity selectLatestBySessionIdForUpdate(@Param("sessionId") Long sessionId);

}
