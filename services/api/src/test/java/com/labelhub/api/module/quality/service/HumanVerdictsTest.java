package com.labelhub.api.module.quality.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** ADR-021 §1: precedent verdicts derive exactly like VerdictService's human branch. */
class HumanVerdictsTest {

    private static Optional<HumanVerdicts.HumanVerdict> derive(String verdict, String level, long open, String resolution) {
        return HumanVerdicts.derive(verdict, level, open, resolution);
    }

    @Test
    void reviewer_reject_is_final() {
        assertThat(derive("reject", "reviewer", 0, null)).contains(new HumanVerdicts.HumanVerdict("rejected", false));
    }

    @Test
    void reviewer_approve_without_cases_is_approved_by_reviewer() {
        assertThat(derive("approve", "reviewer", 0, null)).contains(new HumanVerdicts.HumanVerdict("approved", false));
    }

    @Test
    void open_senior_case_keeps_it_pending() {
        assertThat(derive("approve", "reviewer", 1, null)).isEmpty();
    }

    @Test
    void resolved_senior_case_decides_and_marks_senior() {
        assertThat(derive("approve", "reviewer", 0, SeniorReviewCaseService.RESOLUTION_OVERTURN_TO_REJECT))
            .contains(new HumanVerdicts.HumanVerdict("rejected", true));
        assertThat(derive("approve", "reviewer", 0, SeniorReviewCaseService.RESOLUTION_BOUNDARY_REJECTED))
            .contains(new HumanVerdicts.HumanVerdict("rejected", true));
        assertThat(derive("approve", "reviewer", 0, SeniorReviewCaseService.RESOLUTION_UPHOLD_REVIEWER))
            .contains(new HumanVerdicts.HumanVerdict("approved", true));
        assertThat(derive("approve", "reviewer", 0, SeniorReviewCaseService.RESOLUTION_BOUNDARY_APPROVED))
            .contains(new HumanVerdicts.HumanVerdict("approved", true));
    }

    @Test
    void senior_level_entry_is_senior() {
        assertThat(derive("approve", ReviewLevels.SENIOR_REVIEWER, 0, null)).contains(new HumanVerdicts.HumanVerdict("approved", true));
        assertThat(derive("reject", ReviewLevels.SENIOR_REVIEWER, 0, null)).contains(new HumanVerdicts.HumanVerdict("rejected", true));
    }

    @Test
    void anything_else_is_pending() {
        assertThat(derive(null, null, 0, null)).isEmpty();
        assertThat(derive("needs_changes", "reviewer", 0, null)).isEmpty();
    }
}
