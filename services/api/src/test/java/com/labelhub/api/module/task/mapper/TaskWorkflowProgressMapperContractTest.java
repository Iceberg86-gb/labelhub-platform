package com.labelhub.api.module.task.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class TaskWorkflowProgressMapperContractTest {

    @Test
    void progress_counts_senior_queue_from_cases_not_missing_senior_ledger() throws NoSuchMethodException {
        Method method = TaskWorkflowProgressMapper.class.getMethod("selectByTaskId", Long.class);
        String sql = String.join("\n", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("senior_review_cases");
        assertThat(sql).contains("status = 'open'");
        assertThat(sql).doesNotContain("latest_senior.id IS NULL THEN 1");
    }
}
