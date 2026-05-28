package com.labelhub.api.module.ai.mapper;

import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptVersionMapperContractTest {

    @Test
    void prompt_version_entity_exposes_immutable_prompt_version_fields() {
        PromptVersionEntity entity = new PromptVersionEntity();

        entity.setVersionNumber(1);
        entity.setContent("Judge the submitted labels against the task rubric.");
        entity.setContentHash("a".repeat(64));
        entity.setStatusCode("published");
        entity.setOwnerId(1001L);

        assertThat(entity.getVersionNumber()).isEqualTo(1);
        assertThat(entity.getContent()).contains("task rubric");
        assertThat(entity.getContentHash()).hasSize(64);
        assertThat(entity.getStatusCode()).isEqualTo("published");
        assertThat(entity.getOwnerId()).isEqualTo(1001L);
    }

    @Test
    void prompt_version_mapper_exposes_only_insert_and_select_methods() {
        assertThat(PromptVersionMapper.class.getInterfaces()).isEmpty();
        for (Method method : PromptVersionMapper.class.getDeclaredMethods()) {
            String name = method.getName();
            assertThat(name)
                .as("Method " + name + " violates append-only PromptVersionMapper contract")
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
    void prompt_version_mapper_has_insert_and_lookup_sql() throws Exception {
        Method insert = PromptVersionMapper.class.getDeclaredMethod("insert", PromptVersionEntity.class);
        Method selectById = PromptVersionMapper.class.getDeclaredMethod("selectById", Long.class);
        Method selectByContentHash = PromptVersionMapper.class.getDeclaredMethod("selectByContentHash", String.class);
        Method selectMaxVersionNumber = PromptVersionMapper.class.getDeclaredMethod("selectMaxVersionNumber");
        Method selectLatestPublished = PromptVersionMapper.class.getDeclaredMethod("selectLatestPublished");

        String insertSql = String.join(" ", insert.getAnnotation(Insert.class).value());
        String selectByIdSql = String.join(" ", selectById.getAnnotation(Select.class).value());
        String selectByHashSql = String.join(" ", selectByContentHash.getAnnotation(Select.class).value());
        String selectMaxSql = String.join(" ", selectMaxVersionNumber.getAnnotation(Select.class).value());
        String selectLatestPublishedSql = String.join(" ", selectLatestPublished.getAnnotation(Select.class).value());

        assertThat(insertSql)
            .contains("INSERT INTO prompt_versions")
            .contains("version_no")
            .contains("content_hash")
            .contains("content");
        assertThat(selectByIdSql).contains("FROM prompt_versions").contains("WHERE id = #{id}");
        assertThat(selectByHashSql).contains("FROM prompt_versions").contains("WHERE content_hash = #{contentHash}");
        assertThat(selectMaxSql).contains("MAX(version_no)").contains("FROM prompt_versions");
        assertThat(selectLatestPublishedSql)
            .contains("FROM prompt_versions")
            .contains("status = 'published'")
            .contains("ORDER BY version_no DESC")
            .contains("LIMIT 1");
    }
}
