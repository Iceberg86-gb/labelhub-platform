package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.provider.OpenAiCompatibleProperties;
import com.labelhub.api.module.ai.provider.ProviderInvocationResult;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AiRetryPolicy {

    private final OpenAiCompatibleProperties properties;
    private final LongConsumer sleeper;

    public AiRetryPolicy(OpenAiCompatibleProperties properties) {
        this(properties, AiRetryPolicy::sleepUnchecked);
    }

    AiRetryPolicy(OpenAiCompatibleProperties properties, LongConsumer sleeper) {
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public ProviderInvocationResult invokeWithRetry(
        Supplier<ProviderInvocationResult> providerCall,
        FailedAttemptHandler failedAttemptHandler
    ) {
        int maxAttempts = properties.resolvedMaxAttempts();
        AiProviderException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return providerCall.get();
            } catch (AiProviderException exception) {
                lastException = exception;
                boolean willRetry = exception.isRetryable() && attempt < maxAttempts;
                failedAttemptHandler.onFailedAttempt(attempt, exception, willRetry);
                if (!willRetry) {
                    throw exception;
                }
                sleeper.accept(delayMillis(attempt));
            }
        }
        throw lastException;
    }

    private long delayMillis(int failedAttemptNumber) {
        long baseDelayMillis = Math.max(0, properties.resolvedBaseDelay().toMillis());
        return baseDelayMillis * (1L << (failedAttemptNumber - 1));
    }

    private static void sleepUnchecked(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI provider retry interrupted", exception, true, "interrupted", null);
        }
    }

    @FunctionalInterface
    public interface FailedAttemptHandler {
        void onFailedAttempt(int attemptNumber, AiProviderException exception, boolean willRetry);
    }
}
