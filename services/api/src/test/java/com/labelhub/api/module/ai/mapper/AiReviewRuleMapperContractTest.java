package com.labelhub.api.module.ai.mapper;

import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.task.entity.TaskEntity;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewRuleMapperContractTest {

    @Test
    void ai_review_rule_entity_exposes_task_scoped_rule_fields() {
        AiReviewRuleEntity entity = new AiReviewRuleEntity();
        entity.setTaskId(11L);
        entity.setVersionNumber(2);
        entity.setCurrentPromptVersionId(5L);
        entity.setDimensionsJson("[\"accuracy\",\"safety\"]");
        entity.setThreshold(new BigDecimal("0.8000"));
        entity.setStatusCode("draft");
        entity.setCreatedBy(1001L);

        assertThat(entity.getTaskId()).isEqualTo(11L);
        assertThat(entity.getVersionNumber()).isEqualTo(2);
        assertThat(entity.getCurrentPromptVersionId()).isEqualTo(5L);
        assertThat(entity.getDimensionsJson()).contains("accuracy");
        assertThat(entity.getThreshold()).isEqualByComparingTo("0.8000");
        assertThat(entity.getStatusCode()).isEqualTo("draft");
        assertThat(entity.getCreatedBy()).isEqualTo(1001L);
    }

    @Test
    void task_entity_exposes_nullable_current_ai_review_rule_pointer() {
        TaskEntity entity = new TaskEntity();
        entity.setCurrentAiReviewRuleId(99L);

        assertThat(entity.getCurrentAiReviewRuleId()).isEqualTo(99L);
    }

    @Test
    void ai_review_rule_mapper_exposes_append_only_insert_select_and_publish_methods() {
        assertThat(AiReviewRuleMapper.class.getInterfaces()).isEmpty();
        for (Method method : AiReviewRuleMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates append-only AiReviewRuleMapper contract")
                .doesNotStartWith("delete")
                .doesNotStartWith("remove")
                .doesNotStartWith("save");
            assertThat(name.startsWith("insert") || name.startsWith("select") || name.equals("markPublished"))
                .as("Method " + name + " must be insert/select/markPublished only")
                .isTrue();
        }
    }

    @Test
    void ai_review_rule_mapper_has_insert_and_lookup_sql() throws Exception {
        Method insert = AiReviewRuleMapper.class.getDeclaredMethod("insert", AiReviewRuleEntity.class);
        Method selectById = AiReviewRuleMapper.class.getDeclaredMethod("selectById", Long.class);
        Method selectByTaskId = AiReviewRuleMapper.class.getDeclaredMethod("selectByTaskId", Long.class);
        Method selectMaxVersionByTaskId = AiReviewRuleMapper.class.getDeclaredMethod("selectMaxVersionByTaskId", Long.class);
        Method markPublished = AiReviewRuleMapper.class.getDeclaredMethod("markPublished", Long.class);

        String insertSql = String.join(" ", insert.getAnnotation(Insert.class).value());
        String selectByIdSql = String.join(" ", selectById.getAnnotation(Select.class).value());
        String selectByTaskIdSql = String.join(" ", selectByTaskId.getAnnotation(Select.class).value());
        String selectMaxSql = String.join(" ", selectMaxVersionByTaskId.getAnnotation(Select.class).value());
        String markPublishedSql = String.join(" ", markPublished.getAnnotation(Update.class).value());

        assertThat(insertSql)
            .contains("INSERT INTO ai_review_rules")
            .contains("task_id")
            .contains("current_prompt_version_id")
            .contains("dimensions_json")
            .contains("threshold");
        assertThat(selectByIdSql).contains("FROM ai_review_rules").contains("WHERE id = #{id}");
        assertThat(selectByTaskIdSql).contains("FROM ai_review_rules").contains("WHERE task_id = #{taskId}");
        assertThat(selectMaxSql).contains("MAX(version_no)").contains("FROM ai_review_rules").contains("task_id = #{taskId}");
        assertThat(markPublishedSql)
            .contains("UPDATE ai_review_rules")
            .contains("status = 'published'")
            .contains("activated_at = COALESCE(activated_at, NOW(3))")
            .contains("WHERE id = #{id}");
    }
}
