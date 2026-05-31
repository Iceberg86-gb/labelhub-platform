package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.exception.SelfReviewNotAllowedException;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewerBatchServiceTest {

    private final LedgerService ledgerService = mock(LedgerService.class);
    private final ReviewerBatchService service = new ReviewerBatchService(ledgerService);

    @Test
    void reviewSubmissions_continues_per_item_when_one_submission_cannot_be_reviewed() {
        QualityLedgerEntryEntity created = entry(900L, 300L);
        when(ledgerService.createEntry(300L, 1001L, "reviewer_overall_verdict", Map.of("verdict", "approve")))
            .thenReturn(created);
        when(ledgerService.createEntry(301L, 1001L, "reviewer_overall_verdict", Map.of("verdict", "approve")))
            .thenThrow(new SelfReviewNotAllowedException(301L));
        when(ledgerService.createEntry(302L, 1001L, "reviewer_overall_verdict", Map.of("verdict", "approve")))
            .thenThrow(new SubmissionNotFoundException(302L));

        ReviewerBatchResult result = service.reviewSubmissions(
            List.of(300L, 301L, 302L),
            1001L,
            "approve",
            null
        );

        assertThat(result.items()).extracting(ReviewerBatchItemResult::submissionId)
            .containsExactly(300L, 301L, 302L);
        assertThat(result.items()).extracting(ReviewerBatchItemResult::status)
            .containsExactly("created", "self_review_not_allowed", "not_found");
        assertThat(result.items().get(0).ledgerEntryId()).isEqualTo(900L);
        verify(ledgerService).createEntry(300L, 1001L, "reviewer_overall_verdict", Map.of("verdict", "approve"));
        verify(ledgerService).createEntry(301L, 1001L, "reviewer_overall_verdict", Map.of("verdict", "approve"));
        verify(ledgerService).createEntry(302L, 1001L, "reviewer_overall_verdict", Map.of("verdict", "approve"));
    }

    @Test
    void reviewSubmissions_attaches_reason_to_batch_reject_payload() {
        QualityLedgerEntryEntity created = entry(901L, 300L);
        when(ledgerService.createEntry(
            300L,
            1001L,
            "reviewer_overall_verdict",
            Map.of("verdict", "reject", "reason", "same reason")
        )).thenReturn(created);

        ReviewerBatchResult result = service.reviewSubmissions(
            List.of(300L),
            1001L,
            "reject",
            "same reason"
        );

        assertThat(result.items()).singleElement()
            .extracting(ReviewerBatchItemResult::status)
            .isEqualTo("created");
        verify(ledgerService).createEntry(
            300L,
            1001L,
            "reviewer_overall_verdict",
            Map.of("verdict", "reject", "reason", "same reason")
        );
    }

    private static QualityLedgerEntryEntity entry(Long id, Long submissionId) {
        QualityLedgerEntryEntity entry = new QualityLedgerEntryEntity();
        entry.setId(id);
        entry.setSubmissionId(submissionId);
        return entry;
    }
}
