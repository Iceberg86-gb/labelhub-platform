package com.labelhub.api.module.platform.labor;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformLaborMetricsMapper {

    @Select("""
        SELECT
          s.labeler_id AS user_id,
          u.display_name AS display_name,
          u.username AS username,
          COUNT(*) AS count
        FROM submissions s
        LEFT JOIN users u ON s.labeler_id = u.id
        GROUP BY s.labeler_id, u.display_name, u.username
        ORDER BY count DESC, s.labeler_id ASC
        """)
    @Results(id = "platformLaborMetricRowMap", value = {
        @Result(column = "user_id", property = "userId"),
        @Result(column = "display_name", property = "displayName"),
        @Result(column = "username", property = "username"),
        @Result(column = "count", property = "count"),
        @Result(column = "initial_review_count", property = "initialReviewCount"),
        @Result(column = "senior_review_count", property = "seniorReviewCount"),
        @Result(column = "approve_action_count", property = "approveActionCount"),
        @Result(column = "return_action_count", property = "returnActionCount"),
        @Result(column = "reject_action_count", property = "rejectActionCount")
    })
    List<PlatformLaborMetricRow> selectSubmissionMetrics();

    @Select("""
        SELECT
          ra.reviewer_id AS user_id,
          u.display_name AS display_name,
          u.username AS username,
          COUNT(*) AS count,
          COALESCE(SUM(CASE WHEN ra.review_level = 'reviewer' THEN 1 ELSE 0 END), 0) AS initial_review_count,
          COALESCE(SUM(CASE WHEN ra.review_level = 'senior_reviewer' THEN 1 ELSE 0 END), 0) AS senior_review_count,
          COALESCE(SUM(CASE WHEN ra.action = 'approve' THEN 1 ELSE 0 END), 0) AS approve_action_count,
          COALESCE(SUM(CASE WHEN ra.action = 'return_for_revision' THEN 1 ELSE 0 END), 0) AS return_action_count,
          COALESCE(SUM(CASE WHEN ra.action = 'reject' THEN 1 ELSE 0 END), 0) AS reject_action_count
        FROM review_actions ra
        LEFT JOIN users u ON ra.reviewer_id = u.id
        GROUP BY ra.reviewer_id, u.display_name, u.username
        ORDER BY count DESC, ra.reviewer_id ASC
        """)
    @ResultMap("platformLaborMetricRowMap")
    List<PlatformLaborMetricRow> selectReviewMetrics();

    @Select("""
        SELECT
          (SELECT COUNT(*) FROM submissions WHERE superseded_by_id IS NOT NULL) AS superseded_submission_count,
          (SELECT COUNT(*) FROM review_actions WHERE round_no > 1) AS multi_round_review_action_count,
          (SELECT COUNT(*) FROM submissions WHERE status = 'returned_for_revision') AS returned_for_revision_submission_count
        """)
    @Results(id = "platformReworkMetricsMap", value = {
        @Result(column = "superseded_submission_count", property = "supersededSubmissionCount"),
        @Result(column = "multi_round_review_action_count", property = "multiRoundReviewActionCount"),
        @Result(column = "returned_for_revision_submission_count", property = "returnedForRevisionSubmissionCount")
    })
    PlatformReworkMetrics selectReworkMetrics();
}
