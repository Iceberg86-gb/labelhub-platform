package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
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
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ADR-020 §3 (W3b): decides, when an agent result is recorded, whether the submission is admitted by the auto-pass
 * policy (policy_auto_accept), sampled for blind human review (policy_auto_sample), or left to suggest mode; and
 * re-evaluates the circuit breaker whenever a sampled submission gets a human verdict.
 */
@Service
public class AutoAcceptService {

    /** H2 loop outcomes that leave the output trustworthy enough to admit without a human (ADR-019 v2). */
    static final Set<String> ADMISSIBLE_LOOP_OUTCOMES = Set.of("valid", "corrected");
    private static final BigInteger HASH_SPACE = BigInteger.ONE.shiftLeft(32);

    public enum Decision { NOT_ELIGIBLE, AUTO_ACCEPTED, SAMPLED }

    private final AutoPolicyService autoPolicyService;
    private final LedgerService ledgerService;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;
    private final Canonicalizer canonicalizer;
    private final AuditLogService auditLogService;

    public AutoAcceptService(
        AutoPolicyService autoPolicyService,
        LedgerService ledgerService,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Canonicalizer canonicalizer,
        AuditLogService auditLogService
    ) {
        this.autoPolicyService = autoPolicyService;
        this.ledgerService = ledgerService;
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
        this.canonicalizer = canonicalizer;
        this.auditLogService = auditLogService;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Decision onAgentResultRecorded(Long submissionId, Long taskId, AiCallEntity aiCall, AiCallResult result) {
        AiReviewAutoPolicyEventEntity policy = autoPolicyService.activeEnable(aiCall.getAiReviewRuleId());
        if (policy == null || !eligible(aiCall, result, policy)) {
            return Decision.NOT_ELIGIBLE;
        }
        boolean sampled = sampled(submissionId, policy.getId(), policy.getSampleRate());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyEventId", policy.getId());
        payload.put("ruleId", policy.getAiReviewRuleId());
        payload.put("promptVersionId", policy.getPromptVersionId());
        payload.put("modelName", policy.getModelName());
        payload.put("maxError", policy.getMaxError());
        payload.put("sampleRate", policy.getSampleRate());
        payload.put("enabledBy", policy.getActorId());
        payload.put("finalScore", result.confidence());
        String type = sampled ? LedgerService.POLICY_AUTO_SAMPLE : LedgerService.POLICY_AUTO_ACCEPT;
        QualityLedgerEntryEntity entry = ledgerService.appendPolicyEvidence(submissionId, taskId, aiCall.getId(), type, payload);
        if (!sampled) {
            auditLogService.record(AuditEventBuilder.forAction(AuditActions.AI_AUTO_ACCEPT)
                .actorSystem()
                .resource("submission", submissionId)
                .payload("taskId", taskId)
                .payload("aiCallId", aiCall.getId())
                .payload("ledgerEntryId", entry.getId())
                .payload("policyEventId", policy.getId())
                .payload("enabledBy", policy.getActorId()));
        }
        return sampled ? Decision.SAMPLED : Decision.AUTO_ACCEPTED;
    }

    private boolean eligible(AiCallEntity aiCall, AiCallResult result, AiReviewAutoPolicyEventEntity policy) {
        if (aiCall.getModelProvider() != null && aiCall.getModelProvider().startsWith("mcp:")) {
            return false;  // ADR-022 §3: an external agent's proposal is evidence only, never auto-accepted
        }
        if (!Objects.equals(aiCall.getPromptVersionId(), policy.getPromptVersionId())
            || !Objects.equals(aiCall.getModelName(), policy.getModelName())) {
            return false;  // the gate evidence is only valid for the exact (rule, prompt, model)
        }
        Object evidenceAdapter = policy.getEvidence() == null ? null : policy.getEvidence().get("providerAdapterVersion");
        String expectedAdapter = evidenceAdapter == null
            ? com.labelhub.api.module.ai.precedent.PrecedentProperties.BASE_ADAPTER_VERSION
            : String.valueOf(evidenceAdapter);
        if (!expectedAdapter.equals(aiCall.getProviderAdapterVersion())) {
            return false;  // ADR-021 §3: evidence without precedents does not cover calls with them, and vice versa
        }
        if (!"pass".equals(result.overallSuggestion())) {
            return false;  // API scoring authority routed elsewhere
        }
        Object loop = result.output() == null ? null : result.output().get("agentLoop");
        if (!(loop instanceof Map<?, ?> loopMap) || !ADMISSIBLE_LOOP_OUTCOMES.contains(String.valueOf(loopMap.get("outcome")))) {
            return false;  // no H2 validation, or flagged/downgraded
        }
        if (result.fieldFindings() != null) {
            for (FieldFinding finding : result.fieldFindings()) {
                if ("error".equals(finding.severity())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Deterministic: the same submission and policy event always sample the same way. */
    boolean sampled(Long submissionId, Long policyEventId, BigDecimal sampleRate) {
        String digest = canonicalizer.sha256Hex(submissionId + ":" + policyEventId);
        BigInteger bucket = new BigInteger(digest.substring(0, 8), 16);
        BigInteger threshold = sampleRate.multiply(new BigDecimal(HASH_SPACE)).toBigInteger();
        return bucket.compareTo(threshold) < 0;
    }

    /** After the human verdict commits: re-evaluate the breaker of every policy event this submission was sampled for. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHumanVerdict(HumanVerdictRecordedEvent event) {
        Set<Long> policyEventIds = new LinkedHashSet<>();
        for (QualityLedgerEntryEntity entry : qualityLedgerEntryMapper.selectBySubmissionIdAndType(event.submissionId(), LedgerService.POLICY_AUTO_SAMPLE)) {
            if (entry.getPayload() != null && entry.getPayload().get("policyEventId") != null) {
                policyEventIds.add(Long.valueOf(String.valueOf(entry.getPayload().get("policyEventId"))));
            }
        }
        for (Long policyEventId : policyEventIds) {
            autoPolicyService.evaluateCircuitBreaker(policyEventId);
        }
    }
}
