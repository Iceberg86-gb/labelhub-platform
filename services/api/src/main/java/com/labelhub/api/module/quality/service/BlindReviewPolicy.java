package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import java.util.List;
import java.util.Set;

/**
 * ADR-020 §3 (owner decision): blind sampling. A submission sampled by the auto-pass policy is reviewed blind by
 * initial reviewers: they do not see the AI's overall recommendation/score, nor that it is a sample, so the sampled
 * error rate that drives the circuit breaker is not anchored on the AI. Field-level findings stay visible.
 * Senior reviewers, owners and the labeler are not blinded.
 */
public final class BlindReviewPolicy {

    /** Ledger evidence an initial reviewer must not see on a sampled submission. */
    public static final Set<String> HIDDEN_ENTRY_TYPES = Set.of(
        "ai_overall_recommendation", LedgerService.POLICY_AUTO_SAMPLE, LedgerService.POLICY_AUTO_ACCEPT);

    private BlindReviewPolicy() {
    }

    public static boolean isBlind(QualityLedgerEntryMapper mapper, Long submissionId, Set<String> roles, boolean isOwner, boolean isLabeler) {
        return isBlind(mapper, submissionId, roles, () -> isOwner, isLabeler);
    }

    /** Lazily checks ownership: only resolved when the requester is a pure initial reviewer of a sampled submission. */
    public static boolean isBlind(QualityLedgerEntryMapper mapper, Long submissionId, Set<String> roles,
                                  java.util.function.BooleanSupplier isOwner, boolean isLabeler) {
        if (isLabeler || !has(roles, "REVIEWER") || has(roles, "SENIOR_REVIEWER")) {
            return false;
        }
        if (mapper.selectBySubmissionIdAndType(submissionId, LedgerService.POLICY_AUTO_SAMPLE).isEmpty()) {
            return false;
        }
        return !isOwner.getAsBoolean();
    }

    public static List<QualityLedgerEntryEntity> visible(List<QualityLedgerEntryEntity> entries) {
        return entries.stream().filter(e -> !HIDDEN_ENTRY_TYPES.contains(e.getEvidenceType())).toList();
    }

    private static boolean has(Set<String> roles, String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }
}
