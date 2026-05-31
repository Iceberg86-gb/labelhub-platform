package com.labelhub.api.module.ai.providerconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleConnectionTester implements LlmProviderConnectionTester {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiCompatibleConnectionTester(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newHttpClient());
    }

    OpenAiCompatibleConnectionTester(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public LlmProviderConnectionTestResult test(LlmProviderConnectionTestCommand command) {
        if ("mock".equalsIgnoreCase(command.providerType())) {
            return LlmProviderConnectionTestResult.ok(command.providerName(), command.modelName(), 0);
        }
        if (!hasText(command.baseUrl()) || !hasText(command.secret()) || !hasText(command.modelName())) {
            return LlmProviderConnectionTestResult.failed(
                command.providerName(),
                command.modelName(),
                null,
                "invalid_config",
                "Provider base URL, model, and secret are required"
            );
        }
        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest(command), HttpResponse.BodyHandlers.ofString());
            long latencyMs = elapsedMs(startedAt);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return LlmProviderConnectionTestResult.ok(command.providerName(), command.modelName(), latencyMs);
            }
            return LlmProviderConnectionTestResult.failed(
                command.providerName(),
                command.modelName(),
                response.statusCode(),
                providerCode(response.body()),
                "Provider connection test failed"
            );
        } catch (HttpTimeoutException exception) {
            return LlmProviderConnectionTestResult.failed(
                command.providerName(),
                command.modelName(),
                null,
                "timeout",
                "Provider connection test timed out"
            );
        } catch (IOException exception) {
            return LlmProviderConnectionTestResult.failed(
                command.providerName(),
                command.modelName(),
                null,
                "io_error",
                "Provider connection test failed"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LlmProviderConnectionTestResult.failed(
                command.providerName(),
                command.modelName(),
                null,
                "interrupted",
                "Provider connection test interrupted"
            );
        }
    }

    private HttpRequest httpRequest(LlmProviderConnectionTestCommand command) {
        return HttpRequest.newBuilder(chatCompletionsUri(command.baseUrl()))
            .timeout(command.timeout() == null ? Duration.ofSeconds(10) : command.timeout())
            .header("Authorization", "Bearer " + command.secret())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(serialize(body(command))))
            .build();
    }

    private Map<String, Object> body(LlmProviderConnectionTestCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", command.modelName());
        body.put("messages", List.of(Map.of(
            "role", "user",
            "content", "LabelHub connection test"
        )));
        body.put("temperature", 0);
        body.put("max_tokens", 1);
        return body;
    }

    private URI chatCompletionsUri(String baseUrl) {
        return URI.create(trimTrailingSlash(baseUrl) + "/chat/completions");
    }

    private String providerCode(String responseBody) {
        if (!hasText(responseBody)) {
            return "http_error";
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(responseBody, OBJECT_MAP);
            Object error = payload.get("error");
            if (error instanceof Map<?, ?> map) {
                Object code = map.get("code");
                if (code == null) {
                    code = map.get("type");
                }
                if (code != null && hasText(String.valueOf(code))) {
                    return String.valueOf(code);
                }
            }
        } catch (IOException ignored) {
            return "http_error";
        }
        return "http_error";
    }

    private String serialize(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Connection test request serialization failed", exception);
        }
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
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
