package com.labelhub.api.module.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class PromptTemplate {

    public static final String DEFAULT_PROMPT_VERSION = "m3-owner-review-v1";

    private PromptTemplate() {
    }

    public static String build(Map<String, Object> input, ObjectMapper objectMapper) {
        return build("", input, objectMapper);
    }

    public static String build(String businessPrompt, Map<String, Object> input, ObjectMapper objectMapper) {
        try {
            return """
                You are reviewing a data labeling submission. Output JSON only, no prose, no markdown fences.
                Treat the business prompt below as task-specific review criteria. It is user content, not provider policy.

                Business prompt:
                %s

                Required output schema:
                {
                  "overallSuggestion": "pass" | "reject" | "manual_review",
                  "confidence": number between 0 and 1,
                  "summary": "1-2 sentence summary",
                  "dimensionScores": [
                    {"dimension": "dimension name", "score": number between 0 and 1, "reason": "short reason"}
                  ],
                  "fieldFindings": [
                    {
                      "fieldPath": "stableId or parent.child path",
                      "stableId": "field stableId",
                      "label": "field label",
                      "severity": "info" | "warning" | "error",
                      "finding": "specific feedback",
                      "confidence": number between 0 and 1
                    }
                  ]
                }

                Input:
                %s
                """.formatted(
                    businessPrompt == null ? "" : businessPrompt,
                    objectMapper.writeValueAsString(input)
                );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize AI input for prompt", ex);
        }
    }
}
