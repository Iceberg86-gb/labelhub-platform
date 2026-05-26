package com.labelhub.api.module.ai.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("mockAiProvider")
@ConditionalOnProperty(prefix = "labelhub.ai", name = "active-provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    private static final BigDecimal CONFIDENCE = new BigDecimal("0.90");
    private static final BigDecimal COST = new BigDecimal("0.000100");
    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-v1";
    }

    @Override
    public AiCallResult invoke(AiCallRequest request) {
        callCount.incrementAndGet();
        List<FieldFinding> findings = generateFindings(schemaFieldsOf(request.input()), "");
        String summary = "Mock 模式生成的字段级反馈,字段数 = " + findings.size();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("overallSuggestion", "looks_good");
        output.put("confidence", CONFIDENCE);
        output.put("summary", summary);
        output.put("fieldFindings", findings.stream().map(this::toMap).toList());
        return new AiCallResult(
            output,
            "looks_good",
            CONFIDENCE,
            summary,
            findings,
            Math.max(1, request.input().toString().length() / 4),
            findings.size() * 20,
            COST,
            100,
            null
        );
    }

    public int getCallCount() {
        return callCount.get();
    }

    public void resetCallCount() {
        callCount.set(0);
    }

    private List<FieldFinding> generateFindings(List<Map<String, Object>> fields, String pathPrefix) {
        List<FieldFinding> findings = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            String stableId = stringValue(field.get("stableId"));
            if (stableId == null || stableId.isBlank()) {
                stableId = stringValue(field.get("id"));
            }
            if (stableId == null || stableId.isBlank()) {
                continue;
            }
            String fieldPath = pathPrefix.isBlank() ? stableId : pathPrefix + "." + stableId;
            String label = stringValue(field.get("label"));
            if (label == null || label.isBlank()) {
                label = stableId;
            }
            findings.add(new FieldFinding(
                fieldPath,
                stableId,
                label,
                "info",
                "Mock AI 反馈: " + label + " 看起来合理",
                CONFIDENCE
            ));
            findings.addAll(generateFindings(childrenOf(field), fieldPath));
        }
        return findings;
    }

    private List<Map<String, Object>> schemaFieldsOf(Map<String, Object> input) {
        return listOfMaps(input.get("schemaFields"));
    }

    private List<Map<String, Object>> childrenOf(Map<String, Object> field) {
        return listOfMaps(field.get("children"));
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                map.forEach((key, mapValue) -> typed.put(String.valueOf(key), mapValue));
                maps.add(typed);
            }
        }
        return maps;
    }

    private Map<String, Object> toMap(FieldFinding finding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldPath", finding.fieldPath());
        map.put("stableId", finding.stableId());
        map.put("label", finding.label());
        map.put("severity", finding.severity());
        map.put("finding", finding.finding());
        map.put("confidence", finding.confidence());
        return map;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
