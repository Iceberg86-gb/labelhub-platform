package com.labelhub.api.module.ai.mapper;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallPromptVersionAdditiveContractTest {

    @Test
    void ai_call_entity_exposes_additive_prompt_version_fields() {
        AiCallEntity entity = new AiCallEntity();

        entity.setPromptVersionId(12L);
        entity.setProviderAdapterVersion("agent-default-v1");
        entity.setAiReviewRuleId(19L);

        assertThat(entity.getPromptVersionId()).isEqualTo(12L);
        assertThat(entity.getProviderAdapterVersion()).isEqualTo("agent-default-v1");
        assertThat(entity.getAiReviewRuleId()).isEqualTo(19L);
    }

    @Test
    void ai_call_insert_writes_prompt_version_fk_and_adapter_after_c3_hard_switch() throws Exception {
        Method insert = AiCallMapper.class.getDeclaredMethod("insert", AiCallEntity.class);
        String insertSql = String.join(" ", insert.getAnnotation(Insert.class).value());

        assertThat(insertSql)
            .contains("INSERT INTO ai_calls")
            .contains("prompt_version_id")
            .contains("ai_review_rule_id")
            .contains("provider_adapter_version")
            .contains("#{promptVersionId}")
            .contains("#{aiReviewRuleId}")
            .contains("#{providerAdapterVersion}");
    }

    @Test
    void ai_call_selects_map_new_columns_for_read_paths() throws Exception {
        Method selectById = AiCallMapper.class.getDeclaredMethod("selectById", Long.class);
        Method selectByIdempotencyKey = AiCallMapper.class.getDeclaredMethod("selectByIdempotencyKey", String.class);
        Method selectBySubmissionId = AiCallMapper.class.getDeclaredMethod("selectBySubmissionId", Long.class);

        assertSelectIncludesPromptVersionColumns(selectById);
        assertSelectIncludesPromptVersionColumns(selectByIdempotencyKey);
        assertSelectIncludesPromptVersionColumns(selectBySubmissionId);
    }

    private void assertSelectIncludesPromptVersionColumns(Method method) {
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql)
            .contains("prompt_version_id")
            .contains("ai_review_rule_id")
            .contains("provider_adapter_version");
    }
}
