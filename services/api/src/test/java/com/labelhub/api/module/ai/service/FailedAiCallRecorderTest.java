package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEvent;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallStatusCodes;
import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FailedAiCallRecorderTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-25T12:00:00");

    private final AiCallMapper aiCallMapper = mock(AiCallMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final FailedAiCallRecorder recorder = new FailedAiCallRecorder(
        aiCallMapper,
        Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC),
        auditLogService
    );

    @Test
    void recordFailedAttempt_persists_failed_ai_call_with_attempt_suffix_key() {
        String canonicalKey = "submission:300:provider:mock:model:mock-v1:promptVersionId:1:adapter:agent-default-v1";

        recorder.recordFailedAttempt(
            300L,
            canonicalKey,
            2,
            "promptVersion#1",
            1L,
            null,
            "agent-default-v1",
            "mock",
            "mock-v1",
            "input-hash",
            Map.of("answerPayload", Map.of("field-title", "answer")),
            retryable("rate_limit")
        );

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        AiCallEntity row = captor.getValue();
        assertThat(row.getSubmissionId()).isEqualTo(300L);
        assertThat(row.getPurpose()).isEqualTo("submission_review");
        assertThat(row.getPromptVersion()).isEqualTo("promptVersion#1");
        assertThat(row.getPromptVersionId()).isEqualTo(1L);
        assertThat(row.getAiReviewRuleId()).isNull();
        assertThat(row.getProviderAdapterVersion()).isEqualTo("agent-default-v1");
        assertThat(row.getModelProvider()).isEqualTo("mock");
        assertThat(row.getModelName()).isEqualTo("mock-v1");
        assertThat(row.getInputHash()).isEqualTo("input-hash");
        assertThat(row.getIdempotencyKey()).isEqualTo(canonicalKey + "#failed-attempt-2");
        assertThat(row.getStatus()).isEqualTo(AiCallStatusCodes.FAILED);
        assertThat(row.getRequestPayload()).containsKey("answerPayload");
        assertThat(row.getResponsePayload())
            .containsEntry("providerCode", "rate_limit")
            .containsEntry("statusCode", 503)
            .containsEntry("retryable", true);
        assertThat(row.getCostDecimal()).isEqualByComparingTo("0.000000");
        assertThat(row.getTokenInput()).isZero();
        assertThat(row.getTokenOutput()).isZero();
        assertThat(row.getPromptTokens()).isNull();
        assertThat(row.getCompletionTokens()).isNull();
        assertThat(row.getTotalTokens()).isNull();
        assertThat(row.getCacheHitTokens()).isNull();
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
        assertThat(row.getCompletedAt()).isEqualTo(NOW);
        AuditEvent event = capturedAuditEvent();
        assertThat(event.action()).isEqualTo(AuditActions.AI_REVIEW_RECORDED_FAILED_CALL);
        assertThat(event.actorType()).isEqualTo("system");
        assertThat(event.resourceType()).isEqualTo("ai_call");
    }

    @Test
    void recordFailedAttempt_rejects_overlong_key_without_truncating() {
        String canonicalKey = "x".repeat(160);

        assertThatThrownBy(() -> recorder.recordFailedAttempt(
            300L,
            canonicalKey,
            1,
            "promptVersion#1",
            1L,
            null,
            "agent-default-v1",
            "mock",
            "mock-v1",
            "input-hash",
            Map.of(),
            retryable("rate_limit")
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("idempotency_key");

        verify(aiCallMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    private static AiProviderException retryable(String providerCode) {
        return new AiProviderException("temporary", true, providerCode, 503);
    }

    private AuditEvent capturedAuditEvent() {
        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(captor.capture());
        return captor.getValue().build();
    }
}
