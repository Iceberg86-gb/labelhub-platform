package com.labelhub.api.module.task.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskWorkflowProgressMapper {

    @Select("""
        SELECT
          t.id AS task_id,
          COALESCE(item_stats.total_count, t.quota_total, 0) AS quota_total,
          COALESCE(item_stats.claimed_count, t.quota_claimed, 0) AS quota_claimed,
          COALESCE(item_stats.available_count, 0) AS unclaimed_count,
          COALESCE(labeling.labeling_count, 0) AS labeling_count,
          COALESCE(work.submitted_count, 0) AS submitted_count,
          COALESCE(work.ai_prereview_completed_count, 0) AS ai_prereview_completed_count,
          COALESCE(work.pending_review_count, 0) AS pending_review_count,
          COALESCE(work.pending_senior_review_count, 0) AS pending_senior_review_count,
          COALESCE(work.approved_count, 0) AS approved_count,
          COALESCE(work.rejected_count, 0) AS rejected_count
        FROM tasks t
        LEFT JOIN (
          SELECT
            task_id,
            dataset_id,
            COUNT(*) AS total_count,
            SUM(CASE WHEN status = 'available' THEN 1 ELSE 0 END) AS available_count,
            SUM(CASE WHEN status <> 'available' THEN 1 ELSE 0 END) AS claimed_count
          FROM dataset_items
          GROUP BY task_id, dataset_id
        ) item_stats ON item_stats.task_id = t.id AND item_stats.dataset_id = t.current_dataset_id
        LEFT JOIN (
          SELECT task_id, COUNT(*) AS labeling_count
          FROM sessions
          WHERE status IN ('claimed', 'returned_for_revision')
          GROUP BY task_id
        ) labeling ON labeling.task_id = t.id
        LEFT JOIN (
          SELECT
            s.task_id,
            COUNT(*) AS submitted_count,
            SUM(CASE
              WHEN latest_ai_recommendation.id IS NOT NULL OR latest_ai_call.status = 'completed' THEN 1
              ELSE 0
            END) AS ai_prereview_completed_count,
            SUM(CASE
              WHEN s.status = 'submitted' AND latest_reviewer.id IS NULL THEN 1
              ELSE 0
            END) AS pending_review_count,
            SUM(CASE
              WHEN s.status = 'submitted'
                AND JSON_UNQUOTE(JSON_EXTRACT(latest_reviewer.payload, '$.verdict')) = 'approve'
                AND latest_senior.id IS NULL THEN 1
              ELSE 0
            END) AS pending_senior_review_count,
            SUM(CASE
              WHEN JSON_UNQUOTE(JSON_EXTRACT(latest_senior.payload, '$.verdict')) = 'approve' THEN 1
              ELSE 0
            END) AS approved_count,
            SUM(CASE
              WHEN JSON_UNQUOTE(JSON_EXTRACT(latest_overall.payload, '$.verdict')) = 'reject' THEN 1
              ELSE 0
            END) AS rejected_count
          FROM submissions s
          LEFT JOIN (
            SELECT ranked_reviewer.*
            FROM (
              SELECT qle.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY qle.submission_id
                       ORDER BY qle.created_at DESC, qle.id DESC
                     ) AS rn
              FROM quality_ledger_entries qle
              WHERE qle.evidence_type = 'reviewer_overall_verdict'
                AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(qle.payload, '$.reviewLevel')), 'reviewer') = 'reviewer'
            ) ranked_reviewer
            WHERE ranked_reviewer.rn = 1
          ) latest_reviewer ON latest_reviewer.submission_id = s.id
          LEFT JOIN (
            SELECT ranked_senior.*
            FROM (
              SELECT qle.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY qle.submission_id
                       ORDER BY qle.created_at DESC, qle.id DESC
                     ) AS rn
              FROM quality_ledger_entries qle
              WHERE qle.evidence_type = 'reviewer_overall_verdict'
                AND JSON_UNQUOTE(JSON_EXTRACT(qle.payload, '$.reviewLevel')) = 'senior_reviewer'
            ) ranked_senior
            WHERE ranked_senior.rn = 1
          ) latest_senior ON latest_senior.submission_id = s.id
          LEFT JOIN (
            SELECT ranked_overall.*
            FROM (
              SELECT qle.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY qle.submission_id
                       ORDER BY qle.created_at DESC, qle.id DESC
                     ) AS rn
              FROM quality_ledger_entries qle
              WHERE qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked_overall
            WHERE ranked_overall.rn = 1
          ) latest_overall ON latest_overall.submission_id = s.id
          LEFT JOIN (
            SELECT ranked_ai_recommendation.*
            FROM (
              SELECT qle.*,
                     ROW_NUMBER() OVER (
                       PARTITION BY qle.submission_id
                       ORDER BY qle.created_at DESC, qle.id DESC
                     ) AS rn
              FROM quality_ledger_entries qle
              WHERE qle.evidence_type = 'ai_overall_recommendation'
            ) ranked_ai_recommendation
            WHERE ranked_ai_recommendation.rn = 1
          ) latest_ai_recommendation ON latest_ai_recommendation.submission_id = s.id
          LEFT JOIN (
            SELECT ac.submission_id, ac.status
            FROM ai_calls ac
            JOIN (
              SELECT submission_id, MAX(id) AS id
              FROM ai_calls
              WHERE submission_id IS NOT NULL
              GROUP BY submission_id
            ) latest_ac ON latest_ac.id = ac.id
          ) latest_ai_call ON latest_ai_call.submission_id = s.id
          WHERE s.superseded_by_id IS NULL
          GROUP BY s.task_id
        ) work ON work.task_id = t.id
        WHERE t.id = #{taskId}
        """)
    @Results(id = "taskWorkflowProgressRowMap", value = {
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "quota_total", property = "quotaTotal"),
        @Result(column = "quota_claimed", property = "quotaClaimed"),
        @Result(column = "unclaimed_count", property = "unclaimedCount"),
        @Result(column = "labeling_count", property = "labelingCount"),
        @Result(column = "submitted_count", property = "submittedCount"),
        @Result(column = "ai_prereview_completed_count", property = "aiPrereviewCompletedCount"),
        @Result(column = "pending_review_count", property = "pendingReviewCount"),
        @Result(column = "pending_senior_review_count", property = "pendingSeniorReviewCount"),
        @Result(column = "approved_count", property = "approvedCount"),
        @Result(column = "rejected_count", property = "rejectedCount")
    })
    TaskWorkflowProgressRow selectByTaskId(@Param("taskId") Long taskId);
}
