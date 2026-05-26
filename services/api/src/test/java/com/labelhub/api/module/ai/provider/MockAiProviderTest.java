package com.labelhub.api.module.ai.provider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private final MockAiProvider provider = new MockAiProvider();

    @Test
    void mock_provider_returns_looks_good_overall_suggestion() {
        AiCallResult result = provider.invoke(requestWithFields(List.of(field("field-title", "标题"))));

        assertThat(result.overallSuggestion()).isEqualTo("looks_good");
        assertThat(result.summary()).contains("字段数 = 1");
        assertThat(result.latencyMs()).isEqualTo(100);
    }

    @Test
    void mock_provider_generates_field_finding_per_top_level_field() {
        AiCallResult result = provider.invoke(requestWithFields(List.of(
            field("field-title", "标题"),
            field("field-body", "正文")
        )));

        assertThat(result.fieldFindings())
            .extracting(FieldFinding::fieldPath)
            .containsExactly("field-title", "field-body");
        assertThat(result.fieldFindings())
            .extracting(FieldFinding::finding)
            .containsExactly("Mock AI 反馈: 标题 看起来合理", "Mock AI 反馈: 正文 看起来合理");
    }

    @Test
    void mock_provider_generates_nested_field_finding_with_dot_path() {
        Map<String, Object> child = field("field-city", "城市");
        Map<String, Object> parent = field("field-address", "地址");
        parent.put("children", List.of(child));

        AiCallResult result = provider.invoke(requestWithFields(List.of(parent)));

        assertThat(result.fieldFindings())
            .extracting(FieldFinding::fieldPath)
            .containsExactly("field-address", "field-address.field-city");
    }

    @Test
    void mock_provider_returns_deterministic_output_for_same_input() {
        AiCallRequest request = requestWithFields(List.of(field("field-title", "标题")));

        AiCallResult first = provider.invoke(request);
        AiCallResult second = provider.invoke(request);

        assertThat(second.output()).isEqualTo(first.output());
        assertThat(second.fieldFindings()).isEqualTo(first.fieldFindings());
    }

    @Test
    void mock_provider_does_not_sleep() {
        long started = System.nanoTime();

        AiCallResult result = provider.invoke(requestWithFields(List.of(field("field-title", "标题"))));

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(result.latencyMs()).isEqualTo(100);
        assertThat(elapsedMs).isLessThan(50);
    }

    private AiCallRequest requestWithFields(List<Map<String, Object>> fields) {
        return new AiCallRequest(
            "m3-prompt-v1",
            Map.of("schemaFields", fields),
            Duration.ofSeconds(30)
        );
    }

    private Map<String, Object> field(String stableId, String label) {
        return new java.util.LinkedHashMap<>(Map.of(
            "stableId", stableId,
            "label", label,
            "type", "text"
        ));
    }
}
