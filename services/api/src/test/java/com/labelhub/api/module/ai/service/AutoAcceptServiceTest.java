package com.labelhub.api.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiReviewAutoPolicyEventEntity;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.FieldFinding;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.service.HumanVerdictRecordedEvent;
import com.labelhub.api.module.quality.service.LedgerService;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ADR-020 §3: who the auto-pass policy may admit, deterministic sampling, and the breaker trigger. */
class AutoAcceptServiceTest {

    private final AutoPolicyService policyService = mock(AutoPolicyService.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final QualityLedgerEntryMapper ledgerMapper = mock(QualityLedgerEntryMapper.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final AutoAcceptService service = new AutoAcceptService(policyService, ledgerService, ledgerMapper, new Canonicalizer(new ObjectMapper()), audit);
    private final AiReviewAutoPolicyEventEntity policy = new AiReviewAutoPolicyEventEntity();

    @BeforeEach
    void setUp() {
        policy.setId(500L);
        policy.setAiReviewRuleId(19L);
        policy.setAction("enable");
        policy.setPromptVersionId(7L);
        policy.setModelName("gpt-6.1-terra");
        policy.setMaxError(new BigDecimal("0.05"));
        policy.setSampleRate(new BigDecimal("0.10"));
        policy.setActorId(1001L);
        when(policyService.activeEnable(19L)).thenReturn(policy);
        when(ledgerService.appendPolicyEvidence(anyLong(), anyLong(), anyLong(), any(), any())).thenAnswer(inv -> {
            QualityLedgerEntryEntity e = new QualityLedgerEntryEntity();
            e.setId(900L);
            return e;
        });
    }

    private static AiCallEntity call(String model) {
        AiCallEntity call = new AiCallEntity();
        call.setId(77L);
        call.setAiReviewRuleId(19L);
        call.setPromptVersionId(7L);
        call.setModelName(model);
        call.setProviderAdapterVersion("agent-default-v1");
        return call;
    }

    private static AiCallResult result(String suggestion, String loopOutcome, String... severities) {
        Map<String, Object> output = loopOutcome == null ? Map.of() : Map.of("agentLoop", Map.of("outcome", loopOutcome));
        List<FieldFinding> findings = java.util.Arrays.stream(severities)
            .map(s -> new FieldFinding("answer", "answer", null, s, "f", null)).toList();
        return new AiCallResult(output, suggestion, new BigDecimal("0.9"), "s", findings, 1, 1, BigDecimal.ZERO, 1, "{}");
    }

    /** First submission id that the deterministic selector does (sampled=true) or does not sample. */
    private long submission(boolean sampled) {
        for (long id = 1; ; id++) {
            if (service.sampled(id, 500L, new BigDecimal("0.10")) == sampled) {
                return id;
            }
        }
    }

    @Test
    void eligible_submission_not_sampled_is_auto_accepted_with_accountability_in_the_payload() {
        long id = submission(false);

        assertThat(service.onAgentResultRecorded(id, 44L, call("gpt-6.1-terra"), result("pass", "valid"))).isEqualTo(AutoAcceptService.Decision.AUTO_ACCEPTED);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(ledgerService).appendPolicyEvidence(eq(id), eq(44L), eq(77L), eq("policy_auto_accept"), payload.capture());
        assertThat(payload.getValue()).containsEntry("policyEventId", 500L).containsEntry("enabledBy", 1001L).containsEntry("modelName", "gpt-6.1-terra");
        verify(audit).record(any());
    }

    @Test
    void sampled_submission_gets_sample_evidence_and_stays_in_human_review() {
        long id = submission(true);
        assertThat(service.onAgentResultRecorded(id, 44L, call("gpt-6.1-terra"), result("pass", "corrected"))).isEqualTo(AutoAcceptService.Decision.SAMPLED);
        verify(ledgerService).appendPolicyEvidence(eq(id), eq(44L), eq(77L), eq("policy_auto_sample"), any());
        verify(audit, never()).record(any());
    }

    @Test
    void anything_outside_the_gated_conditions_stays_in_suggest_mode() {
        long id = submission(false);
        assertThat(service.onAgentResultRecorded(id, 44L, call("other-model"), result("pass", "valid"))).isEqualTo(AutoAcceptService.Decision.NOT_ELIGIBLE);
        assertThat(service.onAgentResultRecorded(id, 44L, call("gpt-6.1-terra"), result("manual_review", "valid"))).isEqualTo(AutoAcceptService.Decision.NOT_ELIGIBLE);
        assertThat(service.onAgentResultRecorded(id, 44L, call("gpt-6.1-terra"), result("pass", "flagged"))).isEqualTo(AutoAcceptService.Decision.NOT_ELIGIBLE);
        assertThat(service.onAgentResultRecorded(id, 44L, call("gpt-6.1-terra"), result("pass", null))).isEqualTo(AutoAcceptService.Decision.NOT_ELIGIBLE);
        assertThat(service.onAgentResultRecorded(id, 44L, call("gpt-6.1-terra"), result("pass", "valid", "info", "error"))).isEqualTo(AutoAcceptService.Decision.NOT_ELIGIBLE);
        // ADR-021 §3: evidence gathered without precedent memory does not cover a call made with it.
        AiCallEntity withPrecedents = call("gpt-6.1-terra");
        withPrecedents.setProviderAdapterVersion("agent-default-v1+h4-precedents-v1");
        assertThat(service.onAgentResultRecorded(id, 44L, withPrecedents, result("pass", "valid"))).isEqualTo(AutoAcceptService.Decision.NOT_ELIGIBLE);
        when(policyService.activeEnable(19L)).thenReturn(null);
        assertThat(service.onAgentResultRecorded(id, 44L, call("gpt-6.1-terra"), result("pass", "valid"))).isEqualTo(AutoAcceptService.Decision.NOT_ELIGIBLE);
        verify(ledgerService, never()).appendPolicyEvidence(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void sampling_is_deterministic_and_close_to_the_configured_rate() {
        int sampled = 0;
        for (long id = 1; id <= 20000; id++) {
            boolean first = service.sampled(id, 500L, new BigDecimal("0.10"));
            assertThat(service.sampled(id, 500L, new BigDecimal("0.10"))).isEqualTo(first);
            sampled += first ? 1 : 0;
        }
        assertThat(sampled / 20000.0).isBetween(0.09, 0.11);
        assertThat(service.sampled(1L, 500L, BigDecimal.ONE)).isTrue();
    }

    @Test
    void a_human_verdict_on_a_sampled_submission_re_evaluates_its_policy_breaker() {
        QualityLedgerEntryEntity sample = new QualityLedgerEntryEntity();
        sample.setEvidenceType("policy_auto_sample");
        sample.setPayload(Map.of("policyEventId", 500, "ruleId", 19));
        when(ledgerMapper.selectBySubmissionIdAndType(300L, "policy_auto_sample")).thenReturn(List.of(sample));

        service.onHumanVerdict(new HumanVerdictRecordedEvent(300L, 44L, 901L, "reject", "reviewer"));

        verify(policyService).evaluateCircuitBreaker(500L);
    }

    @Test
    void a_human_verdict_on_an_unsampled_submission_does_nothing() {
        when(ledgerMapper.selectBySubmissionIdAndType(301L, "policy_auto_sample")).thenReturn(List.of());
        service.onHumanVerdict(new HumanVerdictRecordedEvent(301L, 44L, 902L, "approve", "reviewer"));
        verify(policyService, never()).evaluateCircuitBreaker(anyLong());
    }
}
