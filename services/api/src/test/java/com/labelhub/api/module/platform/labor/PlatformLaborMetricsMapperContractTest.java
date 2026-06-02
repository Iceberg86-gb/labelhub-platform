package com.labelhub.api.module.platform.labor;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformLaborMetricsMapperContractTest {

    @Test
    void laborMetricsMapperUsesOnlyReadOnlySelects() {
        for (Method method : PlatformLaborMetricsMapper.class.getDeclaredMethods()) {
            assertThat(method.getName()).startsWith("select");
            assertThat(method.getAnnotations()).anyMatch(annotation -> annotation instanceof Select);
        }
    }

    @Test
    void laborQueriesDoNotReadBusinessContentFields() {
        String sql = "";
        for (Method method : PlatformLaborMetricsMapper.class.getDeclaredMethods()) {
            sql += " " + String.join(" ", method.getAnnotation(Select.class).value());
        }

        assertThat(sql).contains("COUNT(*)");
        assertThat(sql).contains("superseded_by_id");
        assertThat(sql).contains("round_no");
        assertThat(sql).contains("returned_for_revision");
        assertThat(sql).doesNotContain("INSERT");
        assertThat(sql).doesNotContain("UPDATE");
        assertThat(sql).doesNotContain("DELETE");
        assertThat(sql).doesNotContain("answer_payload");
        assertThat(sql).doesNotContain("comment_text");
        assertThat(sql).doesNotContain("structured_reason");
        assertThat(sql).doesNotContain("diff_snapshot");
    }
}
