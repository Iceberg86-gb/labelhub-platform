package com.labelhub.agent.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.labelhub.agent.llm.runtime.RuntimeProviderCallException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Classifies worker failures that are deterministic — retrying them cannot succeed, so they should
 * be dead-lettered immediately instead of burning the retry budget. Assumes the agent's only HTTP
 * hop is the internal API: there, a 4xx is deterministic (404 not-found, 409 ai-input-hash-mismatch),
 * while 408/429 are the only transient 4xx and are not expected on that hop (excluded defensively).
 */
final class OutboxNonRetryable {

    private OutboxNonRetryable() {
    }

    static boolean isNonRetryable(Throwable throwable) {
        if (throwable instanceof JsonProcessingException) {
            return true; // a malformed outbox payload will never parse
        }
        if (throwable instanceof RuntimeProviderCallException providerError) {
            return !providerError.isRetryable(); // provider config errors are marked non-retryable at the source
        }
        if (throwable instanceof WebClientResponseException response) {
            HttpStatusCode status = response.getStatusCode();
            return status.is4xxClientError() && status.value() != 408 && status.value() != 429;
        }
        return false;
    }
}
