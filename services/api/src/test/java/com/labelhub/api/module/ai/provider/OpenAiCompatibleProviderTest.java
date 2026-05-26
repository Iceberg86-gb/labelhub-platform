package com.labelhub.api.module.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.exception.AiProviderException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void invoke_sends_chat_completions_request_with_bearer_auth() throws Exception {
        server = TestHttpServer.responding(200, successResponse(contentJson()));
        OpenAiCompatibleProvider provider = provider(server.baseUrl());

        provider.invoke(request());

        assertThat(server.authorization()).isEqualTo("Bearer test-key");
        assertThat(server.requestPath()).isEqualTo("/v1/chat/completions");
        Map<String, Object> body = objectMapper.readValue(server.requestBody(), Map.class);
        assertThat(body).containsEntry("model", "test-model");
        assertThat(body).containsEntry("temperature", 0);
        assertThat(body.get("messages")).asList().hasSize(1);
    }

    @Test
    void invoke_parses_json_content_from_choices() throws Exception {
        server = TestHttpServer.responding(200, successResponse(contentJson()));
        OpenAiCompatibleProvider provider = provider(server.baseUrl());

        AiCallResult result = provider.invoke(request());

        assertThat(result.overallSuggestion()).isEqualTo("needs_review");
        assertThat(result.summary()).isEqualTo("Check title");
        assertThat(result.fieldFindings()).extracting(FieldFinding::fieldPath).containsExactly("field-title");
        assertThat(result.tokenInput()).isEqualTo(11);
        assertThat(result.tokenOutput()).isEqualTo(7);
        assertThat(result.cost()).isEqualByComparingTo("0.004200");
        assertThat(result.rawResponse()).isEqualTo(contentJson());
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void invoke_throws_when_response_content_is_not_valid_json() throws Exception {
        server = TestHttpServer.responding(200, successResponse("not-json"));
        OpenAiCompatibleProvider provider = provider(server.baseUrl());

        assertThatThrownBy(() -> provider.invoke(request()))
            .isInstanceOf(AiProviderException.class)
            .hasMessageContaining("not valid JSON")
            .extracting("retryable")
            .isEqualTo(false);
    }

    @Test
    void invoke_throws_with_retryable_true_for_5xx() throws Exception {
        server = TestHttpServer.responding(503, errorResponse("overloaded"));
        OpenAiCompatibleProvider provider = provider(server.baseUrl());

        assertThatThrownBy(() -> provider.invoke(request()))
            .isInstanceOf(AiProviderException.class)
            .satisfies(error -> {
                AiProviderException exception = (AiProviderException) error;
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getProviderCode()).isEqualTo("overloaded");
                assertThat(exception.getStatusCode()).isEqualTo(503);
            });
    }

    @Test
    void invoke_throws_with_retryable_false_for_4xx() throws Exception {
        server = TestHttpServer.responding(401, errorResponse("invalid_api_key"));
        OpenAiCompatibleProvider provider = provider(server.baseUrl());

        assertThatThrownBy(() -> provider.invoke(request()))
            .isInstanceOf(AiProviderException.class)
            .satisfies(error -> {
                AiProviderException exception = (AiProviderException) error;
                assertThat(exception.isRetryable()).isFalse();
                assertThat(exception.getProviderCode()).isEqualTo("invalid_api_key");
                assertThat(exception.getStatusCode()).isEqualTo(401);
            });
    }

    @Test
    void constructor_fails_fast_when_apikey_missing() {
        OpenAiCompatibleProperties properties = new OpenAiCompatibleProperties(
            "http://127.0.0.1:1/v1",
            "",
            "test-model",
            "test-provider",
            new BigDecimal("0.001")
        );

        assertThatThrownBy(() -> new OpenAiCompatibleProvider(properties, objectMapper))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("api-key");
    }

    private OpenAiCompatibleProvider provider(String baseUrl) {
        return new OpenAiCompatibleProvider(
            new OpenAiCompatibleProperties(
                baseUrl,
                "test-key",
                "test-model",
                "test-provider",
                new BigDecimal("0.004200")
            ),
            objectMapper
        );
    }

    private AiCallRequest request() {
        return new AiCallRequest(
            "m3-owner-review-v1",
            Map.of(
                "schemaFields", List.of(Map.of("stableId", "field-title", "label", "Title")),
                "answerPayload", Map.of("field-title", "answer")
            ),
            Duration.ofSeconds(2)
        );
    }

    private static String contentJson() {
        return """
            {"overallSuggestion":"needs_review","confidence":0.82,"summary":"Check title","fieldFindings":[{"fieldPath":"field-title","stableId":"field-title","label":"Title","severity":"warning","finding":"Needs review","confidence":0.72}]}\
            """;
    }

    private String successResponse(String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "choices", List.of(Map.of("message", Map.of("content", content))),
            "usage", Map.of("prompt_tokens", 11, "completion_tokens", 7)
        ));
    }

    private String errorResponse(String code) throws Exception {
        return objectMapper.writeValueAsString(Map.of("error", Map.of("code", code, "message", code)));
    }

    private static final class TestHttpServer {
        private final HttpServer server;
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();
        private final AtomicReference<String> requestPath = new AtomicReference<>();

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        static TestHttpServer responding(int status, String responseJson) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            TestHttpServer wrapper = new TestHttpServer(server);
            server.createContext("/v1/chat/completions", exchange -> wrapper.handle(exchange, status, responseJson));
            server.start();
            return wrapper;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        String authorization() {
            return authorization.get();
        }

        String requestBody() {
            return requestBody.get();
        }

        String requestPath() {
            return requestPath.get();
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange, int status, String responseJson) throws IOException {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
