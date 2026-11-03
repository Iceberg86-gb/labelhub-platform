package com.labelhub.api.module.ai.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.ai.entity.AiCallEntity;
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
public interface AiCallMapper {

    @Insert("""
        INSERT INTO ai_calls
        (submission_id, field_path, purpose, prompt_version, prompt_version_id, ai_review_rule_id, provider_adapter_version,
         model_provider, model_name,
         input_hash, request_payload, response_payload, scores, verdict, token_input,
         token_output, cost_decimal, prompt_tokens, completion_tokens, total_tokens, cache_hit_tokens,
         latency_ms, status, idempotency_key, created_at, completed_at)
        VALUES
        (#{submissionId}, #{fieldPath}, #{purpose}, #{promptVersion}, #{promptVersionId}, #{aiReviewRuleId}, #{providerAdapterVersion},
         #{modelProvider}, #{modelName},
         #{inputHash}, #{requestPayload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{responsePayload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{scores, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{verdict}, #{tokenInput}, #{tokenOutput}, #{costDecimal}, #{promptTokens}, #{completionTokens},
         #{totalTokens}, #{cacheHitTokens}, #{latencyMs}, #{status},
         #{idempotencyKey}, #{createdAt}, #{completedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiCallEntity entity);

    @Select("""
        SELECT id, submission_id, field_path, purpose, prompt_version, prompt_version_id, ai_review_rule_id, provider_adapter_version,
               model_provider, model_name,
               input_hash, request_payload, response_payload, scores, verdict, token_input,
               token_output, cost_decimal, prompt_tokens, completion_tokens, total_tokens, cache_hit_tokens,
               latency_ms, status, idempotency_key, created_at, completed_at
        FROM ai_calls
        WHERE id = #{id}
        """)
    @Results(id = "aiCallResultMap", value = {
        @Result(column = "submission_id", property = "submissionId"),
        @Result(column = "field_path", property = "fieldPath"),
        @Result(column = "prompt_version", property = "promptVersion"),
        @Result(column = "prompt_version_id", property = "promptVersionId"),
        @Result(column = "ai_review_rule_id", property = "aiReviewRuleId"),
        @Result(column = "provider_adapter_version", property = "providerAdapterVersion"),
        @Result(column = "model_provider", property = "modelProvider"),
        @Result(column = "model_name", property = "modelName"),
        @Result(column = "input_hash", property = "inputHash"),
        @Result(column = "request_payload", property = "requestPayload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "response_payload", property = "responsePayload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "scores", property = "scores", typeHandler = JacksonTypeHandler.class),
        @Result(column = "token_input", property = "tokenInput"),
        @Result(column = "token_output", property = "tokenOutput"),
        @Result(column = "cost_decimal", property = "costDecimal"),
        @Result(column = "prompt_tokens", property = "promptTokens"),
        @Result(column = "completion_tokens", property = "completionTokens"),
        @Result(column = "total_tokens", property = "totalTokens"),
        @Result(column = "cache_hit_tokens", property = "cacheHitTokens"),
        @Result(column = "latency_ms", property = "latencyMs"),
        @Result(column = "idempotency_key", property = "idempotencyKey"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "completed_at", property = "completedAt")
    })
    AiCallEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, submission_id, field_path, purpose, prompt_version, prompt_version_id, ai_review_rule_id, provider_adapter_version,
               model_provider, model_name,
               input_hash, request_payload, response_payload, scores, verdict, token_input,
               token_output, cost_decimal, prompt_tokens, completion_tokens, total_tokens, cache_hit_tokens,
               latency_ms, status, idempotency_key, created_at, completed_at
        FROM ai_calls
        WHERE idempotency_key = #{idempotencyKey}
        """)
    @ResultMap("aiCallResultMap")
    AiCallEntity selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Select("""
        SELECT id, submission_id, field_path, purpose, prompt_version, prompt_version_id, ai_review_rule_id, provider_adapter_version,
               model_provider, model_name,
               input_hash, request_payload, response_payload, scores, verdict, token_input,
               token_output, cost_decimal, prompt_tokens, completion_tokens, total_tokens, cache_hit_tokens,
               latency_ms, status, idempotency_key, created_at, completed_at
        FROM ai_calls
        WHERE submission_id = #{submissionId}
        ORDER BY created_at ASC
        """)
    @ResultMap("aiCallResultMap")
    List<AiCallEntity> selectBySubmissionId(@Param("submissionId") Long submissionId);

    @Select("""
        <script>
        SELECT id, submission_id, field_path, purpose, prompt_version, prompt_version_id, ai_review_rule_id, provider_adapter_version,
               model_provider, model_name,
               input_hash, request_payload, response_payload, scores, verdict, token_input,
               token_output, cost_decimal, prompt_tokens, completion_tokens, total_tokens, cache_hit_tokens,
               latency_ms, status, idempotency_key, created_at, completed_at
        FROM ai_calls
        WHERE submission_id IN
        <foreach collection="submissionIds" item="submissionId" open="(" separator="," close=")">
            #{submissionId}
        </foreach>
        ORDER BY submission_id ASC, id ASC
        </script>
        """)
    @ResultMap("aiCallResultMap")
    List<AiCallEntity> selectBySubmissionIdsOrdered(@Param("submissionIds") List<Long> submissionIds);

    /**
     * ADR-020 shadow stats: AI-pass submissions of a rule version that have a human verdict, and how many the latest
     * human verdict (any review level, latest wins) rejected; grouped by prompt version and model.
     */
    @Select("""
        SELECT ac.prompt_version_id AS promptVersionId,
               ac.model_name AS modelName,
               COUNT(*) AS judged,
               SUM(CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(v.payload, '$.verdict')) = 'reject' THEN 1 ELSE 0 END) AS rejected
        FROM ai_calls ac
        JOIN (
            SELECT ranked.submission_id, ranked.payload
            FROM (
                SELECT qle.submission_id, qle.payload,
                       ROW_NUMBER() OVER (PARTITION BY qle.submission_id ORDER BY qle.created_at DESC, qle.id DESC) AS rn
                FROM quality_ledger_entries qle
                WHERE qle.evidence_type = 'reviewer_overall_verdict'
            ) ranked
            WHERE ranked.rn = 1
        ) v ON v.submission_id = ac.submission_id
        WHERE ac.ai_review_rule_id = #{ruleId}
          AND ac.purpose = 'submission_review'
          AND ac.status = 'completed'
          AND ac.verdict = 'pass'
        GROUP BY ac.prompt_version_id, ac.model_name
        ORDER BY ac.prompt_version_id, ac.model_name
        """)
    List<ShadowStatsRow> selectShadowStatsByRuleId(@Param("ruleId") Long ruleId);

    /** Mutable row so MyBatis auto-maps it by property name, like the other result types in this project. */
    class ShadowStatsRow {
        private Long promptVersionId;
        private String modelName;
        private Long judged;
        private Long rejected;

        public ShadowStatsRow() {
        }

        public ShadowStatsRow(Long promptVersionId, String modelName, Long judged, Long rejected) {
            this.promptVersionId = promptVersionId;
            this.modelName = modelName;
            this.judged = judged;
            this.rejected = rejected;
        }

        public Long getPromptVersionId() { return promptVersionId; }
        public void setPromptVersionId(Long promptVersionId) { this.promptVersionId = promptVersionId; }
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public Long getJudged() { return judged; }
        public void setJudged(Long judged) { this.judged = judged; }
        public Long getRejected() { return rejected; }
        public void setRejected(Long rejected) { this.rejected = rejected; }
    }
}
