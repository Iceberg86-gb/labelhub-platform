package com.labelhub.api.module.quality.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ADR-020 blind sampling: only pure initial reviewers of sampled submissions are blinded. */
class BlindReviewPolicyTest {

    private final QualityLedgerEntryMapper mapper = mock(QualityLedgerEntryMapper.class);

    private static QualityLedgerEntryEntity entry(String type) {
        QualityLedgerEntryEntity e = new QualityLedgerEntryEntity();
        e.setEvidenceType(type);
        return e;
    }

    @Test
    void initial_reviewer_of_a_sampled_submission_is_blind_everyone_else_is_not() {
        when(mapper.selectBySubmissionIdAndType(1L, "policy_auto_sample")).thenReturn(List.of(entry("policy_auto_sample")));
        assertThat(BlindReviewPolicy.isBlind(mapper, 1L, Set.of("ROLE_REVIEWER"), false, false)).isTrue();
        assertThat(BlindReviewPolicy.isBlind(mapper, 1L, Set.of("REVIEWER", "SENIOR_REVIEWER"), false, false)).isFalse();
        assertThat(BlindReviewPolicy.isBlind(mapper, 1L, Set.of("ROLE_REVIEWER"), true, false)).isFalse();
        assertThat(BlindReviewPolicy.isBlind(mapper, 1L, Set.of("ROLE_REVIEWER"), false, true)).isFalse();
        assertThat(BlindReviewPolicy.isBlind(mapper, 1L, Set.of("OWNER"), false, false)).isFalse();
    }

    @Test
    void unsampled_submissions_are_never_blind() {
        when(mapper.selectBySubmissionIdAndType(2L, "policy_auto_sample")).thenReturn(List.of());
        assertThat(BlindReviewPolicy.isBlind(mapper, 2L, Set.of("ROLE_REVIEWER"), false, false)).isFalse();
    }

    @Test
    void visible_hides_the_overall_judgement_and_policy_markers_but_keeps_findings_and_verdicts() {
        List<QualityLedgerEntryEntity> all = List.of(entry("ai_field_finding"), entry("ai_overall_recommendation"),
            entry("policy_auto_sample"), entry("reviewer_overall_verdict"));
        assertThat(BlindReviewPolicy.visible(all)).extracting(QualityLedgerEntryEntity::getEvidenceType)
            .containsExactly("ai_field_finding", "reviewer_overall_verdict");
    }
}
