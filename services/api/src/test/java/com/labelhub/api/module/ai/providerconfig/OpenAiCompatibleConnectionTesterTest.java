package com.labelhub.api.module.ai.providerconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleConnectionTesterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void test_sendsLightweightChatCompletionWithoutAiReviewToolPayload() throws Exception {
        server = TestHttpServer.responding(200, """
            {"choices":[{"message":{"content":"ok"}}]}\
            """);
        OpenAiCompatibleConnectionTester tester = new OpenAiCompatibleConnectionTester(objectMapper);

        LlmProviderConnectionTestResult result = tester.test(new LlmProviderConnectionTestCommand(
            "openai-compatible",
            "deepseek",
            server.baseUrl(),
            "deepseek-v4-flash",
            "sk-test-secret-1234567890",
            Duration.ofSeconds(2)
        ));

        assertThat(result.ok()).isTrue();
        assertThat(server.authorization()).isEqualTo("Bearer sk-test-secret-1234567890");
        assertThat(server.requestPath()).isEqualTo("/v1/chat/completions");
        assertThat(server.requestBody()).contains("LabelHub connection test");
        assertThat(server.requestBody()).doesNotContain("record_ai_review");
        assertThat(server.requestBody()).doesNotContain("schemaFields");
    }

    @Test
    void test_returnsControlledFailureWithoutSecretEcho() {
        server = TestHttpServer.responding(401, """
            {"error":{"code":"invalid_api_key","message":"bad key"}}\
            """);
        OpenAiCompatibleConnectionTester tester = new OpenAiCompatibleConnectionTester(objectMapper);

        LlmProviderConnectionTestResult result = tester.test(new LlmProviderConnectionTestCommand(
            "openai-compatible",
            "deepseek",
            server.baseUrl(),
            "deepseek-v4-flash",
            "sk-test-secret-1234567890",
            Duration.ofSeconds(2)
        ));

        assertThat(result.ok()).isFalse();
        assertThat(result.providerStatus()).isEqualTo(401);
        assertThat(result.providerCode()).isEqualTo("invalid_api_key");
        assertThat(result.message()).doesNotContain("sk-test-secret");
    }

    private static class TestHttpServer {
        private final HttpServer server;
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> requestPath = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        static TestHttpServer responding(int statusCode, String responseBody) {
            try {
                return new TestHttpServer(statusCode, responseBody);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private TestHttpServer(int statusCode, String responseBody) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> handle(exchange, statusCode, responseBody));
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        String authorization() { return authorization.get(); }
        String requestPath() { return requestPath.get(); }
        String requestBody() { return requestBody.get(); }
        void stop() { server.stop(0); }

        private void handle(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
