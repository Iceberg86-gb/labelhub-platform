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

        assertThat(entity.getPromptVersionId()).isEqualTo(12L);
        assertThat(entity.getProviderAdapterVersion()).isEqualTo("agent-default-v1");
    }

    @Test
    void ai_call_insert_omits_new_columns_so_legacy_service_path_uses_db_defaults() throws Exception {
        Method insert = AiCallMapper.class.getDeclaredMethod("insert", AiCallEntity.class);
        String insertSql = String.join(" ", insert.getAnnotation(Insert.class).value());

        assertThat(insertSql)
            .contains("INSERT INTO ai_calls")
            .doesNotContain("prompt_version_id")
            .doesNotContain("provider_adapter_version");
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
            .contains("provider_adapter_version");
    }
}
