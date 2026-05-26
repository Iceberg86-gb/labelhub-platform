package com.labelhub.api.module.ai.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiIdempotencyMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AiIdempotencyMetrics metrics = new AiIdempotencyMetrics(registry);

    @Test
    void increments_hit_counter_with_provider_tag() {
        metrics.recordHit("deepseek");

        assertThat(registry.counter("labelhub.ai.idempotency.hit", "provider", "deepseek").count())
            .isEqualTo(1.0);
    }

    @Test
    void increments_miss_counter_with_provider_tag() {
        metrics.recordMiss("mock");

        assertThat(registry.counter("labelhub.ai.idempotency.miss", "provider", "mock").count())
            .isEqualTo(1.0);
    }

    @Test
    void increments_mismatch_counter_with_provider_tag() {
        metrics.recordMismatch("deepseek");

        assertThat(registry.counter("labelhub.ai.idempotency.mismatch", "provider", "deepseek").count())
            .isEqualTo(1.0);
    }

    @Test
    void increments_provider_retry_counter_with_provider_tag() {
        metrics.recordRetryAttempt("deepseek");

        assertThat(registry.counter("labelhub.ai.provider.retry", "provider", "deepseek").count())
            .isEqualTo(1.0);
    }
}
