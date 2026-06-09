package com.labelhub.api.module.schema.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Select;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionMapperContractTest {

    @Test
    void submission_mapper_has_no_parent_interfaces() {
        assertThat(SubmissionMapper.class.getInterfaces()).isEmpty();
    }

    @Test
    void submission_mapper_exposes_only_insert_and_select_methods() {
        for (Method method : SubmissionMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                    .as("Method " + name + " violates append-only Mapper contract")
                    .doesNotStartWith("update")
                    .doesNotStartWith("delete")
                    .doesNotStartWith("remove")
                    .doesNotStartWith("save");
            assertThat(name.startsWith("insert") || name.startsWith("select"))
                    .as("Method " + name + " must be insert/select only")
                    .isTrue();
        }
    }

    @Test
    void approved_export_selection_follows_current_reviewer_verdict_contract() throws NoSuchMethodException {
        Method method = SubmissionMapper.class.getDeclaredMethod("selectApprovedByTaskOrderedById", Long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("reviewer_overall_verdict");
        assertThat(sql).contains("senior_review_cases");
        assertThat(sql).contains("status IN ('pending_reviewer', 'open')");
        assertThat(sql).doesNotContain("reviewLevel')) = 'senior_reviewer'");
    }
}
