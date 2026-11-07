package com.labelhub.api.module.quality.service;

import java.util.Optional;

/**
 * The human part of {@link VerdictService}'s derivation, computed from pre-fetched facts so a batch of
 * submissions can be judged in one query (ADR-021 §1: precedents are human verdicts only). It must agree with
 * {@code VerdictService.statusFromLatestEntry}; HumanVerdictsTest pins the cases. Policy acceptance
 * (ADR-020) is not a human verdict and is never produced here.
 */
public final class HumanVerdicts {

    public record HumanVerdict(String status, boolean decidedBySenior) {
    }

    private HumanVerdicts() {
    }

    /**
     * @param verdict          payload.verdict of the latest reviewer_overall_verdict
     * @param reviewLevel      payload.reviewLevel of that entry
     * @param openCases        senior cases still pending_reviewer/open
     * @param latestResolution resolution of the latest resolved senior case, or null
     * @return approved / rejected, or empty while the verdict is still pending
     */
    public static Optional<HumanVerdict> derive(String verdict, String reviewLevel, long openCases, String latestResolution) {
        boolean seniorEntry = ReviewLevels.SENIOR_REVIEWER.equals(reviewLevel);
        if ("reject".equals(verdict)) {
            return Optional.of(new HumanVerdict("rejected", seniorEntry));
        }
        if (!"approve".equals(verdict)) {
            return Optional.empty();
        }
        if (seniorEntry) {
            return Optional.of(new HumanVerdict("approved", true));
        }
        if (openCases > 0) {
            return Optional.empty();
        }
        if (latestResolution != null) {
            boolean rejects = SeniorReviewCaseService.RESOLUTION_OVERTURN_TO_REJECT.equals(latestResolution)
                || SeniorReviewCaseService.RESOLUTION_BOUNDARY_REJECTED.equals(latestResolution);
            return Optional.of(new HumanVerdict(rejects ? "rejected" : "approved", true));
        }
        return Optional.of(new HumanVerdict("approved", false));
    }
}
