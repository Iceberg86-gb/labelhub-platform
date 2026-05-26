package com.labelhub.api.module.ai.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.TextOutputFormat;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorPrometheusEndpointExposureTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class,
            PrometheusMetricsExportAutoConfiguration.class,
            EndpointAutoConfiguration.class,
            WebEndpointAutoConfiguration.class
        ))
        .withPropertyValues(
            "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
            "management.endpoint.prometheus.enabled=true"
        );

    @Test
    void actuator_prometheus_endpoint_exposes_idempotency_counters() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PrometheusScrapeEndpoint.class);

            MeterRegistry registry = context.getBean(MeterRegistry.class);
            registry.counter("labelhub.ai.idempotency.hit", "provider", "deepseek").increment();

            String scrape = context.getBean(PrometheusScrapeEndpoint.class)
                .scrape(TextOutputFormat.CONTENT_TYPE_004, Set.of())
                .getBody();

            assertThat(scrape).contains("labelhub_ai_idempotency_hit_total");
        });
    }
}
