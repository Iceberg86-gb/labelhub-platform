package com.labelhub.api.module.platform.cost;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCostMetricsMapperContractTest {

    @Test
    void cost_metrics_mapper_exposes_only_select_methods() {
        for (Method method : PlatformCostMetricsMapper.class.getDeclaredMethods()) {
            assertThat(method.getName()).startsWith("select");
            assertThat(method.getAnnotations()).anyMatch(annotation -> annotation instanceof Select);
        }
    }

    @Test
    void cost_metrics_queries_use_stored_cost_and_read_only_joins() {
        String sql = "";
        for (Method method : PlatformCostMetricsMapper.class.getDeclaredMethods()) {
            sql += " " + String.join(" ", method.getAnnotation(Select.class).value());
        }

        assertThat(sql).contains("SUM(ac.cost_decimal)");
        assertThat(sql).contains("JOIN submissions s ON ac.submission_id = s.id");
        assertThat(sql).contains("JOIN tasks t ON s.task_id = t.id");
        assertThat(sql).contains("LEFT JOIN users u ON t.owner_id = u.id");
        assertThat(sql).doesNotContain("INSERT");
        assertThat(sql).doesNotContain("UPDATE");
        assertThat(sql).doesNotContain("DELETE");
        assertThat(sql).doesNotContain("pricing");
        assertThat(sql).doesNotContain("per-1m");
    }
}
