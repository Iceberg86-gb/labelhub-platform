package com.labelhub.api.module.ai.prereview;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiPrereviewStatusMapper {

    @Select("""
        <script>
        SELECT
          s.id AS submission_id,
          latest_outbox.status AS outbox_status,
          latest_outbox.locked_at AS outbox_locked_at,
          latest_outbox.last_error AS outbox_last_error,
          latest_ai.status AS ai_call_status,
          EXISTS(
            SELECT 1
            FROM quality_ledger_entries qle
            WHERE qle.submission_id = s.id
              AND qle.evidence_type = 'ai_overall_recommendation'
          ) AS has_ai_overall_recommendation
        FROM submissions s
        LEFT JOIN (
          SELECT o.aggregate_id, o.status, o.locked_at, o.last_error
          FROM outbox o
          JOIN (
            SELECT aggregate_id, MAX(id) AS id
            FROM outbox
            WHERE aggregate_type = 'submission'
              AND event_type = 'ai_review'
            GROUP BY aggregate_id
          ) latest_o ON latest_o.id = o.id
        ) latest_outbox ON latest_outbox.aggregate_id = s.id
        LEFT JOIN (
          SELECT ac.submission_id, ac.status
          FROM ai_calls ac
          JOIN (
            SELECT submission_id, MAX(id) AS id
            FROM ai_calls
            WHERE submission_id IS NOT NULL
            GROUP BY submission_id
          ) latest_ac ON latest_ac.id = ac.id
        ) latest_ai ON latest_ai.submission_id = s.id
        WHERE s.id IN
        <foreach collection="submissionIds" item="submissionId" open="(" separator="," close=")">
          #{submissionId}
        </foreach>
        </script>
        """)
    @Results(id = "aiPrereviewSignalRowMap", value = {
        @Result(column = "submission_id", property = "submissionId"),
        @Result(column = "outbox_status", property = "outboxStatus"),
        @Result(column = "outbox_locked_at", property = "outboxLockedAt"),
        @Result(column = "outbox_last_error", property = "outboxLastError"),
        @Result(column = "ai_call_status", property = "aiCallStatus"),
        @Result(column = "has_ai_overall_recommendation", property = "hasAiOverallRecommendation")
    })
    List<AiPrereviewSignalRow> selectSignalsBySubmissionIds(@Param("submissionIds") List<Long> submissionIds);
}
