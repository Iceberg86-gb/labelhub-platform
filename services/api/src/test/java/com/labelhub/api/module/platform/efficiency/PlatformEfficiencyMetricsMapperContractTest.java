package com.labelhub.api.module.platform.efficiency;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformEfficiencyMetricsMapperContractTest {

    @Test
    void efficiencyMetricsMapperUsesOnlyReadOnlySelects() {
        for (Method method : PlatformEfficiencyMetricsMapper.class.getDeclaredMethods()) {
            assertThat(method.getName()).startsWith("select");
            assertThat(method.getAnnotations()).anyMatch(annotation -> annotation instanceof Select);
        }
    }

    @Test
    void efficiencyQueriesUseStoredCostAndAvoidBusinessContentFields() {
        String sql = "";
        for (Method method : PlatformEfficiencyMetricsMapper.class.getDeclaredMethods()) {
            sql += " " + String.join(" ", method.getAnnotation(Select.class).value());
        }

        assertThat(sql).contains("SUM(ac.cost_decimal)");
        assertThat(sql).contains("COUNT(DISTINCT ac.submission_id)");
        assertThat(sql).contains("COUNT(DISTINCT s.dataset_item_id)");
        assertThat(sql).contains("cache_hit_tokens");
        assertThat(sql).doesNotContain("INSERT");
        assertThat(sql).doesNotContain("UPDATE");
        assertThat(sql).doesNotContain("DELETE");
        assertThat(sql).doesNotContain("request_payload");
        assertThat(sql).doesNotContain("response_payload");
        assertThat(sql).doesNotContain("scores");
        assertThat(sql).doesNotContain("pricing");
        assertThat(sql).doesNotContain("per-1m");
    }
}
