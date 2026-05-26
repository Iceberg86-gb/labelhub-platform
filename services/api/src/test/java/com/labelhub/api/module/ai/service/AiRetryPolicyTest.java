package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.OpenAiCompatibleProperties;
import com.labelhub.api.module.ai.provider.ProviderInvocationResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRetryPolicyTest {

    @Test
    void retryable_exception_retries_until_success_with_deterministic_backoff() {
        List<Long> delays = new ArrayList<>();
        List<Integer> failedAttempts = new ArrayList<>();
        AiRetryPolicy policy = new AiRetryPolicy(properties(3), delays::add);
        AtomicInteger attempts = new AtomicInteger();

        ProviderInvocationResult result = policy.invokeWithRetry(
            () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw retryable("temporary-" + attempt);
                }
                return invocationResult();
            },
            (attempt, exception, willRetry) -> {
                failedAttempts.add(attempt);
                assertThat(willRetry).isTrue();
            }
        );

        assertThat(result.result().overallSuggestion()).isEqualTo("looks_good");
        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(1000L, 2000L);
        assertThat(failedAttempts).containsExactly(1, 2);
    }

    @Test
    void non_retryable_exception_does_not_retry() {
        List<Long> delays = new ArrayList<>();
        List<Boolean> willRetryFlags = new ArrayList<>();
        AiRetryPolicy policy = new AiRetryPolicy(properties(3), delays::add);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.invokeWithRetry(
            () -> {
                attempts.incrementAndGet();
                throw nonRetryable();
            },
            (attempt, exception, willRetry) -> willRetryFlags.add(willRetry)
        )).isInstanceOf(AiProviderException.class);

        assertThat(attempts).hasValue(1);
        assertThat(delays).isEmpty();
        assertThat(willRetryFlags).containsExactly(false);
    }

    @Test
    void retryable_exception_stops_at_max_attempts() {
        List<Long> delays = new ArrayList<>();
        List<Integer> failedAttempts = new ArrayList<>();
        List<Boolean> willRetryFlags = new ArrayList<>();
        AiRetryPolicy policy = new AiRetryPolicy(properties(3), delays::add);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.invokeWithRetry(
            () -> {
                attempts.incrementAndGet();
                throw retryable("temporary");
            },
            (attempt, exception, willRetry) -> {
                failedAttempts.add(attempt);
                willRetryFlags.add(willRetry);
            }
        )).isInstanceOf(AiProviderException.class);

        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(1000L, 2000L);
        assertThat(failedAttempts).containsExactly(1, 2, 3);
        assertThat(willRetryFlags).containsExactly(true, true, false);
    }

    private static OpenAiCompatibleProperties properties(int maxAttempts) {
        return new OpenAiCompatibleProperties(
            "",
            "",
            "mock-v1",
            "mock",
            new BigDecimal("0.001"),
            Duration.ofSeconds(30),
            maxAttempts,
            Duration.ofMillis(1000)
        );
    }

    private static AiProviderException retryable(String providerCode) {
        return new AiProviderException("temporary", true, providerCode, 503);
    }

    private static AiProviderException nonRetryable() {
        return new AiProviderException("bad request", false, "bad_request", 400);
    }

    private static ProviderInvocationResult invocationResult() {
        AiCallResult result = new AiCallResult(
            Map.of("overallSuggestion", "looks_good", "fieldFindings", List.of()),
            "looks_good",
            new BigDecimal("0.90"),
            "summary",
            List.of(),
            10,
            20,
            new BigDecimal("0.000100"),
            100,
            null
        );
        return new ProviderInvocationResult(result, null);
    }
}
