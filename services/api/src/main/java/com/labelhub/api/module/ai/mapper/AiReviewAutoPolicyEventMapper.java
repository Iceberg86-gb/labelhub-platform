package com.labelhub.api.module.ai.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.ai.entity.AiReviewAutoPolicyEventEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** Append-only (conventions §4, ADR-020): insert and select only. */
@Mapper
public interface AiReviewAutoPolicyEventMapper {

    @Insert("""
        INSERT INTO ai_review_auto_policy_events
        (ai_review_rule_id, task_id, action, prompt_version_id, model_name, max_error, min_samples, sample_rate,
         evidence, actor_type, actor_id, reason, created_at)
        VALUES
        (#{aiReviewRuleId}, #{taskId}, #{action}, #{promptVersionId}, #{modelName}, #{maxError}, #{minSamples}, #{sampleRate},
         #{evidence, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{actorType}, #{actorId}, #{reason}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiReviewAutoPolicyEventEntity entity);

    @Select("""
        SELECT id, ai_review_rule_id, task_id, action, prompt_version_id, model_name, max_error, min_samples, sample_rate,
               evidence, actor_type, actor_id, reason, created_at
        FROM ai_review_auto_policy_events
        WHERE ai_review_rule_id = #{ruleId}
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """)
    @Results(id = "autoPolicyEventResultMap", value = {
        @Result(column = "ai_review_rule_id", property = "aiReviewRuleId"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "prompt_version_id", property = "promptVersionId"),
        @Result(column = "model_name", property = "modelName"),
        @Result(column = "max_error", property = "maxError"),
        @Result(column = "min_samples", property = "minSamples"),
        @Result(column = "sample_rate", property = "sampleRate"),
        @Result(column = "evidence", property = "evidence", typeHandler = JacksonTypeHandler.class),
        @Result(column = "actor_type", property = "actorType"),
        @Result(column = "actor_id", property = "actorId"),
        @Result(column = "created_at", property = "createdAt")
    })
    AiReviewAutoPolicyEventEntity selectLatestByRuleId(@Param("ruleId") Long ruleId);

    @Select("""
        SELECT id, ai_review_rule_id, task_id, action, prompt_version_id, model_name, max_error, min_samples, sample_rate,
               evidence, actor_type, actor_id, reason, created_at
        FROM ai_review_auto_policy_events
        WHERE ai_review_rule_id = #{ruleId}
        ORDER BY created_at ASC, id ASC
        """)
    @ResultMap("autoPolicyEventResultMap")
    List<AiReviewAutoPolicyEventEntity> selectByRuleIdOrdered(@Param("ruleId") Long ruleId);

    @Select("""
        SELECT id, ai_review_rule_id, task_id, action, prompt_version_id, model_name, max_error, min_samples, sample_rate,
               evidence, actor_type, actor_id, reason, created_at
        FROM ai_review_auto_policy_events
        WHERE id = #{id}
        """)
    @ResultMap("autoPolicyEventResultMap")
    AiReviewAutoPolicyEventEntity selectById(@Param("id") Long id);

    /**
     * Sampled submissions of one enable event (policy_auto_sample ledger evidence) and, among those with a human
     * verdict, how many the latest human verdict rejected. Feeds the circuit breaker.
     */
    @Select("""
        SELECT COUNT(*) AS sampled,
               SUM(CASE WHEN v.payload IS NOT NULL THEN 1 ELSE 0 END) AS judged,
               SUM(CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(v.payload, '$.verdict')) = 'reject' THEN 1 ELSE 0 END) AS rejected
        FROM quality_ledger_entries sample
        LEFT JOIN (
            SELECT ranked.submission_id, ranked.payload
            FROM (
                SELECT qle.submission_id, qle.payload,
                       ROW_NUMBER() OVER (PARTITION BY qle.submission_id ORDER BY qle.created_at DESC, qle.id DESC) AS rn
                FROM quality_ledger_entries qle
                WHERE qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked
            WHERE ranked.rn = 1
        ) v ON v.submission_id = sample.submission_id
        WHERE sample.evidence_type = 'policy_auto_sample'
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(sample.payload, '$.policyEventId')) AS UNSIGNED) = #{policyEventId}
        """)
    SampleStatsRow selectSampleStats(@Param("policyEventId") Long policyEventId);

    /** Auto-accepted (not sampled) submissions of one enable event. */
    @Select("""
        SELECT COUNT(*)
        FROM quality_ledger_entries
        WHERE evidence_type = 'policy_auto_accept'
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.policyEventId')) AS UNSIGNED) = #{policyEventId}
        """)
    Long selectAutoAcceptedCount(@Param("policyEventId") Long policyEventId);

    class SampleStatsRow {
        private Long sampled;
        private Long judged;
        private Long rejected;

        public SampleStatsRow() {
        }

        public SampleStatsRow(Long sampled, Long judged, Long rejected) {
            this.sampled = sampled;
            this.judged = judged;
            this.rejected = rejected;
        }

        public long sampledOrZero() { return sampled == null ? 0 : sampled; }
        public long judgedOrZero() { return judged == null ? 0 : judged; }
        public long rejectedOrZero() { return rejected == null ? 0 : rejected; }
        public Long getSampled() { return sampled; }
        public void setSampled(Long sampled) { this.sampled = sampled; }
        public Long getJudged() { return judged; }
        public void setJudged(Long judged) { this.judged = judged; }
        public Long getRejected() { return rejected; }
        public void setRejected(Long rejected) { this.rejected = rejected; }
    }
}
