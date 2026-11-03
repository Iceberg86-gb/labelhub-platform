package com.labelhub.api.module.quality.service;

/**
 * Published after a human (reviewer or senior reviewer) overall verdict is appended to the ledger. ADR-020 §3: the
 * auto-pass circuit breaker listens to it, after commit, so it can never block or roll back the human verdict.
 */
public record HumanVerdictRecordedEvent(Long submissionId, Long taskId, Long ledgerEntryId, String verdict, String reviewLevel) {
}
