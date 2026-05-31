package com.labelhub.agent.llm.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;
import java.math.BigDecimal;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleAiReviewRuntimeClientTest {

    private static final String SECRET = "sk-live-secret-1234";

    @Test
    void sends_key_only_as_authorization_header_and_returns_safe_result_payload() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
            {
              "choices": [
                {
                  "message": {
                    "tool_calls": [
                      {
                        "function": {
                          "name": "record_ai_review",
                          "arguments": "{\\"overallSuggestion\\":\\"manual_review\\",\\"confidence\\":0.67,\\"summary\\":\\"Needs human check\\",\\"dimensionScores\\":[{\\"dimension\\":\\"quality\\",\\"score\\":0.67,\\"reason\\":\\"Borderline\\"}],\\"fieldFindings\\":[]}"
                        }
                      }
                    ]
                  }
                }
              ],
              "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18}
            }
            """);
        RuntimeProviderSource source = RuntimeProviderSource.db(
            9L,
            "openai-compatible",
            "owner-doubao",
            "https://provider.example.test/v1",
            "doubao-lite",
            SECRET
        );
        OpenAiCompatibleAiReviewRuntimeClient client = new OpenAiCompatibleAiReviewRuntimeClient(new ObjectMapper(), httpClient);

        AiReviewResultPayload result = client.review(context(), source);

        assertThat(httpClient.authorizationHeader()).isEqualTo("Bearer " + SECRET);
        assertThat(httpClient.requestBody()).contains("\"model\":\"doubao-lite\"");
        assertThat(httpClient.requestBody()).doesNotContain(SECRET);
        assertThat(result.overallSuggestion()).isEqualTo("manual_review");
        assertThat(result.modelProvider()).isEqualTo("owner-doubao");
        assertThat(result.modelName()).isEqualTo("doubao-lite");
        assertThat(result.responsePayload().toString()).doesNotContain(SECRET);
        assertThat(result.rawResponse()).doesNotContain(SECRET);
    }

    @Test
    void auth_failure_is_non_retryable_and_does_not_echo_key() {
        RecordingHttpClient httpClient = new RecordingHttpClient(401, """
            {"error":{"code":"invalid_api_key"}}
            """);
        OpenAiCompatibleAiReviewRuntimeClient client = new OpenAiCompatibleAiReviewRuntimeClient(new ObjectMapper(), httpClient);

        assertThatThrownBy(() -> client.review(context(), source()))
            .isInstanceOfSatisfying(RuntimeProviderCallException.class, exception -> {
                assertThat(exception.isRetryable()).isFalse();
                assertThat(exception.getProviderCode()).isEqualTo("invalid_api_key");
                assertThat(exception.getStatusCode()).isEqualTo(401);
                assertThat(exception.getMessage()).doesNotContain(SECRET);
            });
    }

    @Test
    void server_failure_is_retryable_and_does_not_echo_key() {
        RecordingHttpClient httpClient = new RecordingHttpClient(500, """
            {"error":{"code":"server_error"}}
            """);
        OpenAiCompatibleAiReviewRuntimeClient client = new OpenAiCompatibleAiReviewRuntimeClient(new ObjectMapper(), httpClient);

        assertThatThrownBy(() -> client.review(context(), source()))
            .isInstanceOfSatisfying(RuntimeProviderCallException.class, exception -> {
                assertThat(exception.isRetryable()).isTrue();
                assertThat(exception.getProviderCode()).isEqualTo("server_error");
                assertThat(exception.getStatusCode()).isEqualTo(500);
                assertThat(exception.getMessage()).doesNotContain(SECRET);
            });
    }

    private static RuntimeProviderSource source() {
        return RuntimeProviderSource.db(
            9L,
            "openai-compatible",
            "owner-doubao",
            "https://provider.example.test/v1",
            "doubao-lite",
            SECRET
        );
    }

    private static AiReviewContext context() {
        return new AiReviewContext(
            300L,
            "submission:300:ai_review:promptVersionId:7:adapter:agent-default-v1",
            "promptVersion#7",
            7L,
            19L,
            "agent-default-v1",
            Map.of("answerPayload", Map.of("field-title", "answer")),
            "a".repeat(64),
            List.of("quality"),
            new BigDecimal("0.80"),
            new BigDecimal("0.20"),
            new BigDecimal("0.80"),
            new BigDecimal("0.20"),
            "equal-weight-three-zone-v2",
            "business prompt",
            "rendered prompt"
        );
    }

    private static final class RecordingHttpClient extends HttpClient {

        private final String responseBody;
        private final int status;
        private String authorizationHeader;
        private String requestBody;

        private RecordingHttpClient(int status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        private String authorizationHeader() {
            return authorizationHeader;
        }

        private String requestBody() {
            return requestBody;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            authorizationHeader = request.headers().firstValue("Authorization").orElse(null);
            requestBody = request.bodyPublisher()
                .map(RecordingHttpClient::readBody)
                .orElse("");
            @SuppressWarnings("unchecked")
            T body = (T) responseBody;
            return new StubHttpResponse<>(request, status, body);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }

        private static String readBody(HttpRequest.BodyPublisher publisher) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CountDownLatch latch = new CountDownLatch(1);
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] chunk = new byte[item.remaining()];
                    item.get(chunk);
                    bytes.writeBytes(chunk);
                }

                @Override
                public void onError(Throwable throwable) {
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });
            try {
                if (!latch.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("request body was not published");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("request body read interrupted", exception);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }

    private record StubHttpResponse<T>(HttpRequest request, int status, T body) implements HttpResponse<T> {
        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (left, right) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
