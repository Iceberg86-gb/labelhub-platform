package com.labelhub.api.module.ai.prereview;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiPrereviewStatusServiceTest {

    private final AiPrereviewStatusMapper mapper = mock(AiPrereviewStatusMapper.class);
    private final AiPrereviewStatusService service = new AiPrereviewStatusService(
        mapper,
        Clock.fixed(Instant.parse("2026-06-02T12:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void completed_status_wins_over_dead_letter_when_ai_evidence_exists() {
        AiPrereviewSignalsView result = service.derive(new AiPrereviewSignalRow(
            7L,
            "dead_letter",
            null,
            "reason=provider_http_error",
            "completed",
            true
        ));

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.signals().outboxStatus()).isEqualTo("dead_letter");
        assertThat(result.signals().aiCallStatus()).isEqualTo("completed");
        assertThat(result.signals().hasAiOverallRecommendation()).isTrue();
        assertThat(result.signals().lastError()).isEqualTo("reason=provider_http_error");
    }

    @Test
    void dead_letter_without_completed_ai_evidence_is_failed() {
        AiPrereviewSignalsView result = service.derive(new AiPrereviewSignalRow(
            8L,
            "dead_letter",
            null,
            "reason=provider_http_error",
            null,
            false
        ));

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.signals().lastError()).isEqualTo("reason=provider_http_error");
    }

    @Test
    void fresh_processing_outbox_is_processing() {
        AiPrereviewSignalsView result = service.derive(new AiPrereviewSignalRow(
            9L,
            "processing",
            LocalDateTime.parse("2026-06-02T11:59:30"),
            null,
            null,
            false
        ));

        assertThat(result.status()).isEqualTo("processing");
    }

    @Test
    void no_outbox_or_ai_signal_is_pending() {
        AiPrereviewSignalsView result = service.derive(new AiPrereviewSignalRow(
            10L,
            null,
            null,
            null,
            null,
            false
        ));

        assertThat(result.status()).isEqualTo("pending");
    }

    @Test
    void batch_lookup_returns_pending_defaults_for_missing_rows() {
        AiPrereviewSignalsView result = service.defaultView(11L);

        assertThat(result.submissionId()).isEqualTo(11L);
        assertThat(result.status()).isEqualTo("pending");
        assertThat(result.signals().outboxStatus()).isNull();
        assertThat(result.signals().aiCallStatus()).isNull();
        assertThat(result.signals().hasAiOverallRecommendation()).isFalse();
        assertThat(result.signals().lastError()).isNull();
    }

    @Test
    void status_values_do_not_include_reuse_without_durable_signal() {
        List<String> statuses = List.of(
            service.defaultView(1L).status(),
            service.derive(new AiPrereviewSignalRow(2L, "processed", null, null, "completed", true)).status(),
            service.derive(new AiPrereviewSignalRow(3L, "dead_letter", null, "reason=failed", null, false)).status(),
            service.derive(new AiPrereviewSignalRow(4L, "processing", LocalDateTime.parse("2026-06-02T11:59:30"), null, null, false)).status()
        );

        assertThat(statuses).containsExactly("pending", "completed", "failed", "processing");
        assertThat(statuses).doesNotContain("reused");
    }
}
