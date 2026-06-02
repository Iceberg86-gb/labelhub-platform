package com.labelhub.api.module.platform.efficiency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformEfficiencyMetricsMapper {

    @Select("""
        SELECT
          COUNT(*) AS call_count,
          COUNT(DISTINCT idempotency_key) AS unique_key_count,
          COUNT(*) - COUNT(DISTINCT idempotency_key) AS duplicate_key_count,
          COALESCE(SUM(cache_hit_tokens), 0) AS cache_hit_tokens
        FROM ai_calls
        """)
    @Results(id = "platformIdempotencyMetricsMap", value = {
        @Result(column = "call_count", property = "callCount"),
        @Result(column = "unique_key_count", property = "uniqueKeyCount"),
        @Result(column = "duplicate_key_count", property = "duplicateKeyCount"),
        @Result(column = "cache_hit_tokens", property = "cacheHitTokens")
    })
    PlatformIdempotencyMetrics selectIdempotencyMetrics();

    @Select("""
        SELECT
          COALESCE(SUM(ac.cost_decimal), 0) AS total_cost,
          COUNT(DISTINCT ac.submission_id) AS distinct_submission_count,
          COUNT(DISTINCT s.dataset_item_id) AS distinct_dataset_item_count
        FROM ai_calls ac
        LEFT JOIN submissions s ON ac.submission_id = s.id
        """)
    @Results(id = "platformUnitCostMetricsMap", value = {
        @Result(column = "total_cost", property = "totalCost"),
        @Result(column = "distinct_submission_count", property = "distinctSubmissionCount"),
        @Result(column = "distinct_dataset_item_count", property = "distinctDatasetItemCount")
    })
    PlatformUnitCostMetrics selectUnitCostMetrics();
}
