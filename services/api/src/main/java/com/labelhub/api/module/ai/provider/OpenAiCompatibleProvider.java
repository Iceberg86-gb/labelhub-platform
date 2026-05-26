package com.labelhub.api.module.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.exception.AiProviderException;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("openAiCompatibleAiProvider")
@ConditionalOnProperty(prefix = "labelhub.ai", name = "active-provider", havingValue = "openai-compatible")
public class OpenAiCompatibleProvider implements AiProvider {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final OpenAiCompatibleProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiCompatibleProvider(OpenAiCompatibleProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, HttpClient.newHttpClient());
    }

    OpenAiCompatibleProvider(
        OpenAiCompatibleProperties props,
        ObjectMapper objectMapper,
        HttpClient httpClient
    ) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        requireText(props.baseUrl(), "base-url");
        requireText(props.apiKey(), "api-key");
        requireText(props.modelName(), "model-name");
    }

    @Override
    public String providerName() {
        return props.resolvedProviderName();
    }

    @Override
    public String modelName() {
        return props.modelName();
    }

    @Override
    public Duration timeout() {
        return props.resolvedTimeout();
    }

    @Override
    public AiCallResult invoke(AiCallRequest request) {
        return invokeWithUsage(request).result();
    }

    @Override
    public ProviderInvocationResult invokeWithUsage(AiCallRequest request) {
        long start = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest(request), HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMs(start);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerHttpError(response);
            }
            return invocationResultFromResponse(response.body(), latencyMs);
        } catch (HttpTimeoutException ex) {
            throw new AiProviderException("AI provider request timed out", ex, true, "timeout", null);
        } catch (IOException ex) {
            throw new AiProviderException("AI provider request failed", ex, true, "io_error", null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI provider request interrupted", ex, true, "interrupted", null);
        }
    }

    private HttpRequest httpRequest(AiCallRequest request) {
        String body = serialize(chatCompletionBody(request));
        Duration timeout = request.timeout() == null ? props.resolvedTimeout() : request.timeout();
        return HttpRequest.newBuilder(chatCompletionsUri())
            .timeout(timeout)
            .header("Authorization", "Bearer " + props.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    }

    private Map<String, Object> chatCompletionBody(AiCallRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.modelName());
        body.put("messages", List.of(Map.of(
            "role", "user",
            "content", PromptTemplate.build(request.input(), objectMapper)
        )));
        body.put("temperature", 0);
        return body;
    }

    private URI chatCompletionsUri() {
        return URI.create(trimTrailingSlash(props.baseUrl()) + "/chat/completions");
    }

    private AiCallResult resultFromResponse(String responseBody, long latencyMs) {
        return invocationResultFromResponse(responseBody, latencyMs).result();
    }

    private ProviderInvocationResult invocationResultFromResponse(String responseBody, long latencyMs) {
        Map<String, Object> response = parseJsonObject(responseBody, "AI provider response is not valid JSON");
        String content = extractContent(response);
        Map<String, Object> output = parseJsonObject(content, "AI response is not valid JSON");
        AiCallResult result = new AiCallResult(
            output,
            stringOrDefault(output.get("overallSuggestion"), "needs_review"),
            decimalValue(output.get("confidence")),
            stringValue(output.get("summary")),
            fieldFindings(output.get("fieldFindings")),
            intPath(response, "usage", "prompt_tokens"),
            intPath(response, "usage", "completion_tokens"),
            props.resolvedEstimatedCostPerCall(),
            latencyMs,
            content
        );
        return new ProviderInvocationResult(result, usageFromResponse(response));
    }

    private String extractContent(Map<String, Object> response) {
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new AiProviderException("AI provider response missing choices", false, "missing_choices", null);
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new AiProviderException("AI provider response choice is invalid", false, "invalid_choice", null);
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new AiProviderException("AI provider response missing message", false, "missing_message", null);
        }
        Object content = message.get("content");
        if (content == null || String.valueOf(content).isBlank()) {
            throw new AiProviderException("AI provider response missing content", false, "missing_content", null);
        }
        return String.valueOf(content);
    }

    private List<FieldFinding> fieldFindings(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        List<FieldFinding> findings = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            String fieldPath = stringValue(map.get("fieldPath"));
            if (fieldPath == null || fieldPath.isBlank()) {
                continue;
            }
            findings.add(new FieldFinding(
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

    private AiProviderException providerHttpError(HttpResponse<String> response) {
        String code = errorCode(response.body(), response.statusCode());
        return new AiProviderException(
            "AI provider returned HTTP " + response.statusCode(),
            response.statusCode() >= 500,
            code,
            response.statusCode()
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
                if (code != null) {
                    return String.valueOf(code);
                }
            }
        } catch (JsonProcessingException ignored) {
            // Fall through to stable status-derived provider code.
        }
        return "http_" + statusCode;
    }

    private Map<String, Object> parseJsonObject(String json, String errorMessage) {
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException ex) {
            throw new AiProviderException(errorMessage, ex, false, "invalid_json", null);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("AI request serialization failed", ex, false, "request_serialization_failed", null);
        }
    }

    private int intPath(Map<String, Object> map, String parentKey, String key) {
        Object parent = map.get(parentKey);
        if (!(parent instanceof Map<?, ?> parentMap)) {
            return 0;
        }
        Object value = parentMap.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private AiCallUsage usageFromResponse(Map<String, Object> response) {
        Object usage = response.get("usage");
        if (!(usage instanceof Map<?, ?> usageMap)) {
            return null;
        }
        return new AiCallUsage(
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
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String stringOrDefault(Object value, String defaultValue) {
        String string = stringValue(value);
        return string == null || string.isBlank() ? defaultValue : string;
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("labelhub.ai.openai-compatible." + name + " must be configured");
        }
    }
}
