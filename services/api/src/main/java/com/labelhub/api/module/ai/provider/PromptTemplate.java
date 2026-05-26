package com.labelhub.api.module.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class PromptTemplate {

    public static final String DEFAULT_PROMPT_VERSION = "m3-owner-review-v1";

    private PromptTemplate() {
    }

    public static String build(Map<String, Object> input, ObjectMapper objectMapper) {
        try {
            return """
                You are reviewing a data labeling submission. Output JSON only, no prose, no markdown fences.

                Required output schema:
                {
                  "overallSuggestion": "looks_good" | "needs_review" | "issues_found",
                  "confidence": number between 0 and 1,
                  "summary": "1-2 sentence summary",
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
                """.formatted(objectMapper.writeValueAsString(input));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Cannot serialize AI input for prompt", ex);
        }
    }
}
