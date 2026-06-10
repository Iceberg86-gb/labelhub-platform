package com.labelhub.api.module.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.service.view.LabelerSessionWorkStatusCount;
import java.util.List;
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
        @Result(column = "labeler_work_status", property = "workStatus"),
        @Result(column = "final_verdict", property = "finalVerdict"),
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
        SELECT id, task_id, dataset_item_id, labeler_id, schema_version_id,
               claim_snapshot, status, claimed_at, submitted_at
        FROM sessions
        WHERE task_id = #{taskId}
          AND labeler_id = #{labelerId}
          AND status IN ('claimed', 'returned_for_revision')
        ORDER BY dataset_item_id ASC, id ASC
        FOR UPDATE
        """)
    @ResultMap("sessionResultMap")
    List<SessionEntity> selectEditableByTaskAndLabelerForUpdate(
        @Param("taskId") Long taskId,
        @Param("labelerId") Long labelerId
    );

    @Select("""
        <script>
        SELECT *
        FROM (
          SELECT s.id, s.task_id, s.dataset_item_id, s.labeler_id, s.schema_version_id,
                 s.claim_snapshot, s.status, s.claimed_at, s.submitted_at,
                 CASE
                   WHEN s.status = 'claimed' THEN 'in_progress'
                   WHEN s.status = 'returned_for_revision' THEN 'returned_for_revision'
                   WHEN s.status = 'abandoned' THEN 'abandoned'
                   WHEN s.status = 'submitted'
                        AND JSON_UNQUOTE(JSON_EXTRACT(latest_verdict.payload, '$.verdict')) = 'approve'
                     THEN 'approved'
                   WHEN s.status = 'submitted'
                        AND JSON_UNQUOTE(JSON_EXTRACT(latest_verdict.payload, '$.verdict')) = 'reject'
                     THEN 'rejected'
                   WHEN s.status = 'submitted' THEN 'submitted'
                   ELSE s.status
                 END AS labeler_work_status,
                 CASE
                   WHEN JSON_UNQUOTE(JSON_EXTRACT(latest_verdict.payload, '$.verdict')) = 'approve' THEN 'approved'
                   WHEN JSON_UNQUOTE(JSON_EXTRACT(latest_verdict.payload, '$.verdict')) = 'reject' THEN 'rejected'
                   WHEN active_submission.id IS NOT NULL THEN 'pending'
                   ELSE NULL
                 END AS final_verdict
          FROM sessions s
          LEFT JOIN (
            SELECT ranked.*
            FROM (
              SELECT sub.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY sub.session_id
                       ORDER BY sub.created_at DESC, sub.id DESC
                     ) AS rn
              FROM submissions sub
              WHERE sub.superseded_by_id IS NULL
            ) ranked
            WHERE ranked.rn = 1
          ) active_submission ON active_submission.session_id = s.id
          LEFT JOIN (
            SELECT ranked_verdict.*
            FROM (
              SELECT qle.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY qle.submission_id
                       ORDER BY qle.created_at DESC, qle.id DESC
                     ) AS rn
              FROM quality_ledger_entries qle
              WHERE qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked_verdict
            WHERE ranked_verdict.rn = 1
          ) latest_verdict ON latest_verdict.submission_id = active_submission.id
          WHERE s.labeler_id = #{labelerId}
          <if test="statusFilter != null and statusFilter != ''">
            AND s.status = #{statusFilter}
          </if>
        ) labeler_sessions
        WHERE 1 = 1
        <if test="workStatusFilter != null and workStatusFilter != ''">
          AND labeler_work_status = #{workStatusFilter}
        </if>
        ORDER BY claimed_at DESC, id DESC
        </script>
        """)
    @ResultMap("sessionResultMap")
    IPage<SessionEntity> selectByLabeler(
        IPage<SessionEntity> page,
        @Param("labelerId") Long labelerId,
        @Param("statusFilter") String statusFilter,
        @Param("workStatusFilter") String workStatusFilter
    );

    @Select("""
        SELECT labeler_work_status AS work_status, COUNT(*) AS count
        FROM (
          SELECT CASE
                   WHEN s.status = 'claimed' THEN 'in_progress'
                   WHEN s.status = 'returned_for_revision' THEN 'returned_for_revision'
                   WHEN s.status = 'abandoned' THEN 'abandoned'
                   WHEN s.status = 'submitted'
                        AND JSON_UNQUOTE(JSON_EXTRACT(latest_verdict.payload, '$.verdict')) = 'approve'
                     THEN 'approved'
                   WHEN s.status = 'submitted'
                        AND JSON_UNQUOTE(JSON_EXTRACT(latest_verdict.payload, '$.verdict')) = 'reject'
                     THEN 'rejected'
                   WHEN s.status = 'submitted' THEN 'submitted'
                   ELSE s.status
                 END AS labeler_work_status
          FROM sessions s
          LEFT JOIN (
            SELECT ranked.*
            FROM (
              SELECT sub.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY sub.session_id
                       ORDER BY sub.created_at DESC, sub.id DESC
                     ) AS rn
              FROM submissions sub
              WHERE sub.superseded_by_id IS NULL
            ) ranked
            WHERE ranked.rn = 1
          ) active_submission ON active_submission.session_id = s.id
          LEFT JOIN (
            SELECT ranked_verdict.*
            FROM (
              SELECT qle.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY qle.submission_id
                       ORDER BY qle.created_at DESC, qle.id DESC
                     ) AS rn
              FROM quality_ledger_entries qle
              WHERE qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked_verdict
            WHERE ranked_verdict.rn = 1
          ) latest_verdict ON latest_verdict.submission_id = active_submission.id
          WHERE s.labeler_id = #{labelerId}
        ) labeler_sessions
        GROUP BY labeler_work_status
        """)
    @Results(id = "labelerSessionWorkStatusCountMap", value = {
        @Result(column = "work_status", property = "workStatus"),
        @Result(column = "count", property = "count")
    })
    List<LabelerSessionWorkStatusCount> selectLabelerWorkStatusCounts(@Param("labelerId") Long labelerId);

    @Update("UPDATE sessions SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
