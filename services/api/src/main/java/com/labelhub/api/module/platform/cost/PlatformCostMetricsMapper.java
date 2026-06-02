package com.labelhub.api.module.platform.cost;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlatformCostMetricsMapper {

    @Select("""
        <script>
        SELECT
          COUNT(*) AS call_count,
          COALESCE(SUM(ac.token_input + ac.token_output), 0) AS total_tokens,
          COALESCE(SUM(ac.cost_decimal), 0) AS total_cost,
          COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND t.id IS NOT NULL THEN 1 ELSE 0 END), 0) AS attributed_call_count,
          COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND t.id IS NOT NULL THEN ac.token_input + ac.token_output ELSE 0 END), 0) AS attributed_tokens,
          COALESCE(SUM(CASE WHEN s.id IS NOT NULL AND t.id IS NOT NULL THEN ac.cost_decimal ELSE 0 END), 0) AS attributed_cost
        FROM ai_calls ac
        LEFT JOIN submissions s ON ac.submission_id = s.id
        LEFT JOIN tasks t ON s.task_id = t.id
        <where>
          <if test="from != null">AND ac.created_at &gt;= #{from}</if>
          <if test="to != null">AND ac.created_at &lt;= #{to}</if>
        </where>
        </script>
        """)
    @Results(id = "platformCostAggregateRowMap", value = {
        @Result(column = "bucket_date", property = "bucketDate"),
        @Result(column = "model_provider", property = "modelProvider"),
        @Result(column = "model_name", property = "modelName"),
        @Result(column = "group_id", property = "groupId"),
        @Result(column = "group_name", property = "groupName"),
        @Result(column = "call_count", property = "callCount"),
        @Result(column = "total_tokens", property = "totalTokens"),
        @Result(column = "total_cost", property = "totalCost"),
        @Result(column = "attributed_call_count", property = "attributedCallCount"),
        @Result(column = "attributed_tokens", property = "attributedTokens"),
        @Result(column = "attributed_cost", property = "attributedCost")
    })
    PlatformCostAggregateRow selectOverview(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
        <script>
        SELECT
          DATE(ac.created_at) AS bucket_date,
          COUNT(*) AS call_count,
          COALESCE(SUM(ac.token_input + ac.token_output), 0) AS total_tokens,
          COALESCE(SUM(ac.cost_decimal), 0) AS total_cost
        FROM ai_calls ac
        <where>
          <if test="from != null">AND ac.created_at &gt;= #{from}</if>
          <if test="to != null">AND ac.created_at &lt;= #{to}</if>
        </where>
        GROUP BY DATE(ac.created_at)
        ORDER BY bucket_date ASC
        </script>
        """)
    @ResultMap("platformCostAggregateRowMap")
    List<PlatformCostAggregateRow> selectDailyTrend(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
        <script>
        SELECT
          ac.model_provider AS model_provider,
          ac.model_name AS model_name,
          COUNT(*) AS call_count,
          COALESCE(SUM(ac.token_input + ac.token_output), 0) AS total_tokens,
          COALESCE(SUM(ac.cost_decimal), 0) AS total_cost
        FROM ai_calls ac
        <where>
          <if test="from != null">AND ac.created_at &gt;= #{from}</if>
          <if test="to != null">AND ac.created_at &lt;= #{to}</if>
        </where>
        GROUP BY ac.model_provider, ac.model_name
        ORDER BY total_cost DESC, total_tokens DESC, call_count DESC
        </script>
        """)
    @ResultMap("platformCostAggregateRowMap")
    List<PlatformCostAggregateRow> selectModelBreakdown(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
        <script>
        SELECT
          t.id AS group_id,
          t.title AS group_name,
          COUNT(*) AS call_count,
          COALESCE(SUM(ac.token_input + ac.token_output), 0) AS total_tokens,
          COALESCE(SUM(ac.cost_decimal), 0) AS total_cost
        FROM ai_calls ac
        JOIN submissions s ON ac.submission_id = s.id
        JOIN tasks t ON s.task_id = t.id
        <where>
          <if test="from != null">AND ac.created_at &gt;= #{from}</if>
          <if test="to != null">AND ac.created_at &lt;= #{to}</if>
        </where>
        GROUP BY t.id, t.title
        ORDER BY total_cost DESC, total_tokens DESC, call_count DESC
        </script>
        """)
    @ResultMap("platformCostAggregateRowMap")
    List<PlatformCostAggregateRow> selectTaskBreakdown(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
        <script>
        SELECT
          t.owner_id AS group_id,
          COALESCE(u.display_name, u.username, CONCAT('USER#', t.owner_id)) AS group_name,
          COUNT(*) AS call_count,
          COALESCE(SUM(ac.token_input + ac.token_output), 0) AS total_tokens,
          COALESCE(SUM(ac.cost_decimal), 0) AS total_cost
        FROM ai_calls ac
        JOIN submissions s ON ac.submission_id = s.id
        JOIN tasks t ON s.task_id = t.id
        LEFT JOIN users u ON t.owner_id = u.id
        <where>
          <if test="from != null">AND ac.created_at &gt;= #{from}</if>
          <if test="to != null">AND ac.created_at &lt;= #{to}</if>
        </where>
        GROUP BY t.owner_id, u.display_name, u.username
        ORDER BY total_cost DESC, total_tokens DESC, call_count DESC
        </script>
        """)
    @ResultMap("platformCostAggregateRowMap")
    List<PlatformCostAggregateRow> selectOwnerBreakdown(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
