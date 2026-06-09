package com.labelhub.api.module.export.service;

import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportFieldCatalogServiceTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final ExportFactCollector factCollector = mock(ExportFactCollector.class);
    private final ExportArtifactBuilder artifactBuilder = mock(ExportArtifactBuilder.class);
    private final ExportFieldCatalogService service = new ExportFieldCatalogService(taskMapper, factCollector, artifactBuilder);

    @Test
    void build_detects_dpo_recommendation_from_paired_choices_and_preference() {
        List<Map<String, String>> rows = List.of(
            row("解释什么是过拟合", "回答A1", "回答B1", "A", "答案A优于B"),
            row("反转链表", "回答A2", "回答B2", "B", "B 答非所问")
        );

        ExportFieldCatalogView catalog = service.build(rows);

        assertThat(catalog.submissionCount()).isEqualTo(2);
        assertThat(catalog.recommendedFormat()).isEqualTo("trl_dpo_jsonl");
        assertThat(catalog.recommendedBindings()).isNotNull();
        assertThat(catalog.recommendedBindings().promptSource()).isEqualTo("item.prompt");
        assertThat(catalog.recommendedBindings().preferenceSource()).isEqualTo("answer.preferred");
        assertThat(catalog.recommendedBindings().choiceSources())
            .containsEntry("A", "item.response_a")
            .containsEntry("B", "item.response_b");
        assertThat(catalog.sampleRows()).hasSize(2);
        assertThat(catalog.fields()).extracting(ExportFieldCatalogView.Field::source)
            .contains("task_id", "item.prompt", "item.response_a", "item.response_b", "answer.preferred", "answer.summary");
    }

    @Test
    void build_binds_choice_pair_to_response_text_not_model_name_identifiers() {
        List<Map<String, String>> rows = List.of(
            choiceRow("解释过拟合", "doubao-pro", "baseline-7b",
                "过拟合是指模型在训练集上表现很好但泛化差,例如死记硬背考题。", "过拟合就是学得太好了。", "A"),
            choiceRow("反转链表", "gpt-style", "baseline-7b",
                "可以用三个指针迭代地反转每个节点的指向,时间复杂度 O(n)。", "用递归也行。", "B")
        );

        ExportFieldCatalogView catalog = service.build(rows);

        assertThat(catalog.recommendedFormat()).isEqualTo("trl_dpo_jsonl");
        assertThat(catalog.recommendedBindings().choiceSources())
            .containsEntry("A", "item.response_a")
            .containsEntry("B", "item.response_b");
        assertThat(catalog.recommendedBindings().choiceSources().values())
            .doesNotContain("item.model_a", "item.model_b");
    }

    @Test
    void build_marks_preference_field_distinct_values_and_coverage() {
        List<Map<String, String>> rows = List.of(
            row("p1", "a1", "b1", "A", "s1"),
            row("p2", "a2", "b2", "B", "")
        );

        ExportFieldCatalogView catalog = service.build(rows);

        ExportFieldCatalogView.Field preferred = field(catalog, "answer.preferred");
        assertThat(preferred.distinctValues()).containsExactlyInAnyOrder("A", "B");
        assertThat(preferred.nonEmptyRatio()).isEqualTo(1.0);
        assertThat(field(catalog, "answer.summary").nonEmptyRatio()).isEqualTo(0.5);
    }

    @Test
    void build_falls_back_to_sft_when_no_choice_pair() {
        List<Map<String, String>> rows = List.of(
            sftRow("写一首关于春天的诗", "春天来了,万物复苏,这是一首很长的诗。"),
            sftRow("翻译 hello", "你好")
        );

        ExportFieldCatalogView catalog = service.build(rows);

        assertThat(catalog.recommendedFormat()).isEqualTo("openai_chat_sft_jsonl");
        assertThat(catalog.recommendedBindings().promptSource()).isEqualTo("item.prompt");
        assertThat(catalog.recommendedBindings().completionSource()).isEqualTo("answer.text");
    }

    @Test
    void build_completion_skips_structured_array_field_and_picks_text_answer() {
        List<Map<String, String>> rows = List.of(
            sftWithDimensions("写一首关于春天的诗", "[\"relevance\",\"accuracy\"]", "春天来了,万物复苏,这是一段较长的真实回答。"),
            sftWithDimensions("翻译 hello", "[\"fluency\"]", "你好,很高兴见到你。")
        );

        ExportFieldCatalogView catalog = service.build(rows);

        assertThat(catalog.recommendedFormat()).isEqualTo("openai_chat_sft_jsonl");
        assertThat(catalog.recommendedBindings().completionSource()).isEqualTo("answer.summary");
        assertThat(catalog.recommendedBindings().completionSource()).isNotEqualTo("answer.dimensions");
    }

    @Test
    void build_uses_schema_field_titles_as_friendly_labels() {
        List<Map<String, String>> rows = List.of(sftRow("提示", "回答"));

        ExportFieldCatalogView catalog = service.build(rows, Map.of("text", "助手答复", "prompt", "用户问题"));

        assertThat(field(catalog, "answer.text").label()).isEqualTo("助手答复");
        assertThat(field(catalog, "item.prompt").label()).isEqualTo("用户问题");
    }

    @Test
    void build_defaults_to_flat_table_when_no_bindable_business_fields() {
        Map<String, String> systemOnly = new LinkedHashMap<>();
        systemOnly.put("task_id", "1");
        systemOnly.put("submission_id", "10");

        ExportFieldCatalogView catalog = service.build(List.of(systemOnly));

        assertThat(catalog.recommendedFormat()).isEqualTo("flat_table");
        assertThat(catalog.recommendedBindings()).isNull();
        assertThat(catalog.fields()).extracting(ExportFieldCatalogView.Field::group).containsOnly("system");
    }

    @Test
    void buildForOwner_rejects_task_not_owned_by_requester() {
        TaskEntity task = new TaskEntity();
        task.setId(100L);
        task.setOwnerId(999L);
        when(taskMapper.selectById(100L)).thenReturn(task);

        assertThatThrownBy(() -> service.buildForOwner(100L, 1L))
            .isInstanceOf(TaskNotFoundException.class);
    }

    private static ExportFieldCatalogView.Field field(ExportFieldCatalogView catalog, String source) {
        return catalog.fields().stream().filter(f -> f.source().equals(source)).findFirst().orElseThrow();
    }

    private static Map<String, String> row(String prompt, String responseA, String responseB, String preferred, String summary) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("task_id", "1");
        row.put("submission_id", "10");
        row.put("item.prompt", prompt);
        row.put("item.response_a", responseA);
        row.put("item.response_b", responseB);
        row.put("answer.preferred", preferred);
        row.put("answer.summary", summary);
        return row;
    }

    private static Map<String, String> sftRow(String prompt, String text) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("task_id", "1");
        row.put("submission_id", "10");
        row.put("item.prompt", prompt);
        row.put("answer.text", text);
        return row;
    }

    private static Map<String, String> choiceRow(
        String prompt, String modelA, String modelB, String responseA, String responseB, String preferred
    ) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("task_id", "1");
        row.put("submission_id", "10");
        row.put("item.prompt", prompt);
        row.put("item.model_a", modelA);
        row.put("item.model_b", modelB);
        row.put("item.response_a", responseA);
        row.put("item.response_b", responseB);
        row.put("answer.preferred", preferred);
        return row;
    }

    private static Map<String, String> sftWithDimensions(String prompt, String dimensions, String summary) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("task_id", "1");
        row.put("submission_id", "10");
        row.put("item.prompt", prompt);
        row.put("answer.dimensions", dimensions);
        row.put("answer.summary", summary);
        return row;
    }
}
