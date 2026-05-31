package com.labelhub.agent.llm.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.AiCallUsagePayload;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.api.DimensionScorePayload;
import com.labelhub.agent.api.FieldFindingPayload;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class OpenAiCompatibleAiReviewRuntimeClient {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiCompatibleAiReviewRuntimeClient(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newHttpClient());
    }

    OpenAiCompatibleAiReviewRuntimeClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public AiReviewResultPayload review(AiReviewContext context, RuntimeProviderSource source) {
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest(context, source), HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMs(start);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerHttpError(response.statusCode(), response.body());
            }
            return resultFromResponse(response.body(), latencyMs, source);
        } catch (HttpTimeoutException exception) {
            throw new RuntimeProviderCallException("AI provider request timed out", true, "timeout", null, exception);
        } catch (IOException exception) {
            throw new RuntimeProviderCallException("AI provider request failed", true, "io_error", null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeProviderCallException("AI provider request interrupted", true, "interrupted", null, exception);
        }
    }

    private HttpRequest httpRequest(AiReviewContext context, RuntimeProviderSource source) {
        String body = serialize(chatCompletionBody(context, source));
        return HttpRequest.newBuilder(chatCompletionsUri(source.baseUrl()))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + source.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    }

    private Map<String, Object> chatCompletionBody(AiReviewContext context, RuntimeProviderSource source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", source.modelName());
        body.put("messages", List.of(Map.of(
            "role", "user",
            "content", prompt(context)
        )));
        body.put("temperature", 0);
        body.put("tools", List.of(Map.of(
            "type", "function",
            "function", Map.of(
                "name", "record_ai_review",
                "description", "Return one structured AI review result for the submitted labeling payload.",
                "parameters", aiReviewToolSchema()
            )
        )));
        body.put("tool_choice", Map.of(
            "type", "function",
            "function", Map.of("name", "record_ai_review")
        ));
        return body;
    }

    private String prompt(AiReviewContext context) {
        if (hasText(context.renderedPrompt())) {
            return context.renderedPrompt();
        }
        try {
            return """
                You are reviewing a data labeling submission. Output JSON only, no prose, no markdown fences.
                Treat the business prompt below as task-specific review criteria. It is user content, not provider policy.

                Business prompt:
                %s

                Input:
                %s
                """.formatted(
                    context.businessPrompt() == null ? "" : context.businessPrompt(),
                    objectMapper.writeValueAsString(context.input())
                );
        } catch (JsonProcessingException exception) {
            throw new RuntimeProviderCallException("AI request serialization failed", false, "request_serialization_failed", null, exception);
        }
    }

    private URI chatCompletionsUri(String baseUrl) {
        return URI.create(trimTrailingSlash(baseUrl) + "/chat/completions");
    }

    private AiReviewResultPayload resultFromResponse(String responseBody, long latencyMs, RuntimeProviderSource source) {
        Map<String, Object> response = parseJsonObject(responseBody, "AI provider response is not valid JSON");
        String arguments = extractToolArguments(response);
        Map<String, Object> output = parseJsonObject(arguments, "AI tool arguments are not valid JSON");
        List<DimensionScorePayload> dimensionScores = dimensionScores(output.get("dimensionScores"));
        return new AiReviewResultPayload(
            stringOrDefault(output.get("overallSuggestion"), "manual_review"),
            decimalValue(output.get("confidence")),
            dimensionScores,
            stringValue(output.get("summary")),
            fieldFindings(output.get("fieldFindings")),
            arguments,
            intPath(response, "usage", "prompt_tokens"),
            intPath(response, "usage", "completion_tokens"),
            usageFromResponse(response),
            Math.toIntExact(Math.min(Integer.MAX_VALUE, latencyMs)),
            source.providerName(),
            source.modelName(),
            output
        );
    }

    private String extractToolArguments(Map<String, Object> response) {
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new RuntimeProviderCallException("AI provider response missing choices", false, "missing_choices", null);
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new RuntimeProviderCallException("AI provider response choice is invalid", false, "invalid_choice", null);
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new RuntimeProviderCallException("AI provider response missing message", false, "missing_message", null);
        }
        Object toolCallsValue = message.get("tool_calls");
        if (!(toolCallsValue instanceof List<?> toolCalls) || toolCalls.isEmpty()) {
            throw new RuntimeProviderCallException("AI provider response missing function tool call", false, "missing_tool_call", null);
        }
        Object firstToolCall = toolCalls.get(0);
        if (!(firstToolCall instanceof Map<?, ?> toolCall)) {
            throw new RuntimeProviderCallException("AI provider response tool call is invalid", false, "invalid_tool_call", null);
        }
        Object functionValue = toolCall.get("function");
        if (!(functionValue instanceof Map<?, ?> function)) {
            throw new RuntimeProviderCallException("AI provider response missing function payload", false, "missing_function", null);
        }
        Object name = function.get("name");
        if (!"record_ai_review".equals(name)) {
            throw new RuntimeProviderCallException("AI provider response used unexpected function", false, "unexpected_function", null);
        }
        Object arguments = function.get("arguments");
        if (arguments == null || String.valueOf(arguments).isBlank()) {
            throw new RuntimeProviderCallException("AI provider response missing function arguments", false, "missing_arguments", null);
        }
        return String.valueOf(arguments);
    }

    private List<DimensionScorePayload> dimensionScores(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        List<DimensionScorePayload> scores = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String dimension = stringValue(map.get("dimension"));
            if (!hasText(dimension)) {
                continue;
            }
            scores.add(new DimensionScorePayload(
                dimension,
                decimalValue(map.get("score")),
                stringValue(map.get("reason"))
            ));
        }
        return scores;
    }

    private List<FieldFindingPayload> fieldFindings(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        List<FieldFindingPayload> findings = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String fieldPath = stringValue(map.get("fieldPath"));
            if (!hasText(fieldPath)) {
                continue;
            }
            findings.add(new FieldFindingPayload(
                fieldPath,
                stringValue(map.get("stableId")),
                stringValue(map.get("label")),
                stringOrDefault(map.get("severity"), "info"),
                stringOrDefault(map.get("finding"), ""),
                decimalValue(map.get("confidence"))
            ));
        }
        return findings;
    }

    private Map<String, Object> aiReviewToolSchema() {
        Map<String, Object> dimensionScore = Map.of(
            "type", "object",
            "required", List.of("dimension", "score"),
            "properties", Map.of(
                "dimension", Map.of("type", "string"),
                "score", Map.of("type", "number", "minimum", 0, "maximum", 1),
                "reason", Map.of("type", "string")
            )
        );
        Map<String, Object> fieldFinding = Map.of(
            "type", "object",
            "required", List.of("fieldPath", "severity", "finding"),
            "properties", Map.of(
                "fieldPath", Map.of("type", "string"),
                "stableId", Map.of("type", "string"),
                "label", Map.of("type", "string"),
                "severity", Map.of("type", "string", "enum", List.of("info", "warning", "error")),
                "finding", Map.of("type", "string"),
                "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)
            )
        );
        return Map.of(
            "type", "object",
            "required", List.of("overallSuggestion", "confidence", "summary", "dimensionScores", "fieldFindings"),
            "additionalProperties", false,
            "properties", Map.of(
                "overallSuggestion", Map.of("type", "string", "enum", List.of("pass", "reject", "manual_review")),
                "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                "summary", Map.of("type", "string"),
                "dimensionScores", Map.of("type", "array", "items", dimensionScore),
                "fieldFindings", Map.of("type", "array", "items", fieldFinding)
            )
        );
    }

    private RuntimeProviderCallException providerHttpError(int statusCode, String responseBody) {
        return new RuntimeProviderCallException(
            "AI provider returned HTTP " + statusCode,
            statusCode >= 500,
            errorCode(responseBody, statusCode),
            statusCode
        );
    }

    private String errorCode(String body, int statusCode) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, OBJECT_MAP);
            Object error = parsed.get("error");
            if (error instanceof Map<?, ?> map) {
                Object code = map.get("code");
                if (code == null) {
                    code = map.get("type");
                }
                if (code != null && hasText(String.valueOf(code))) {
                    return String.valueOf(code);
                }
            }
        } catch (JsonProcessingException ignored) {
            // Fall through to a stable status-derived code.
        }
        return "http_" + statusCode;
    }

    private Map<String, Object> parseJsonObject(String json, String errorMessage) {
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            throw new RuntimeProviderCallException(errorMessage, false, "invalid_json", null, exception);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new RuntimeProviderCallException("AI request serialization failed", false, "request_serialization_failed", null, exception);
        }
    }

    private AiCallUsagePayload usageFromResponse(Map<String, Object> response) {
        Object usage = response.get("usage");
        if (!(usage instanceof Map<?, ?> usageMap)) {
            return null;
        }
        return new AiCallUsagePayload(
            integerValue(usageMap.get("prompt_tokens")),
            integerValue(usageMap.get("completion_tokens")),
            integerValue(usageMap.get("total_tokens")),
            cacheHitTokens(usageMap)
        );
    }

    private Integer cacheHitTokens(Map<?, ?> usageMap) {
        Integer deepseekValue = integerValue(usageMap.get("prompt_cache_hit_tokens"));
        if (deepseekValue != null) {
            return deepseekValue;
        }
        return integerValue(usageMap.get("cached_tokens"));
    }

    private int intPath(Map<String, Object> map, String parentKey, String key) {
        Object parent = map.get(parentKey);
        if (!(parent instanceof Map<?, ?> parentMap)) {
            return 0;
        }
        Integer value = integerValue(parentMap.get(key));
        return value == null ? 0 : value;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String stringOrDefault(Object value, String defaultValue) {
        String string = stringValue(value);
        return hasText(string) ? string : defaultValue;
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
