package com.labelhub.api.module.ai.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AiIdempotencyMetrics {

    private static final String NAMESPACE = "labelhub.ai.idempotency";
    private static final String TAG_PROVIDER = "provider";

    private final MeterRegistry registry;

    public AiIdempotencyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordHit(String providerName) {
        registry.counter(NAMESPACE + ".hit", TAG_PROVIDER, normalize(providerName)).increment();
    }

    public void recordMiss(String providerName) {
        registry.counter(NAMESPACE + ".miss", TAG_PROVIDER, normalize(providerName)).increment();
    }

    public void recordMismatch(String providerName) {
        registry.counter(NAMESPACE + ".mismatch", TAG_PROVIDER, normalize(providerName)).increment();
    }

    public void recordRetryAttempt(String providerName) {
        registry.counter("labelhub.ai.provider.retry", TAG_PROVIDER, normalize(providerName)).increment();
    }

    private String normalize(String providerName) {
        return providerName == null || providerName.isBlank() ? "unknown" : providerName;
    }
}
