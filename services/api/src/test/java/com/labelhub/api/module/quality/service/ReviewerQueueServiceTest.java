package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.mapper.ReviewerSubmissionQueueRow;
import com.labelhub.api.module.task.service.PagedResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewerQueueServiceTest {

    private final QualityLedgerEntryMapper qualityLedgerEntryMapper = mock(QualityLedgerEntryMapper.class);
    private ReviewerQueueService reviewerQueueService;

    @BeforeEach
    void setUp() {
        reviewerQueueService = new ReviewerQueueService(qualityLedgerEntryMapper);
    }

    @Test
    void listQueue_filters_by_default_submitted_status_when_status_not_provided() {
        ReviewerSubmissionQueueRow row = row(300L, "submitted", null, null);
        when(qualityLedgerEntryMapper.selectReviewerQueuePage("submitted", null, "reviewer", 0L, 20L)).thenReturn(List.of(row));
        when(qualityLedgerEntryMapper.selectReviewerQueueCount("submitted", null, "reviewer")).thenReturn(1L);

        PagedResult<ReviewerSubmissionQueueRow> result = reviewerQueueService.listQueue(1, 20, null, null, null);

        assertThat(result.items()).extracting(ReviewerSubmissionQueueRow::getId).containsExactly(300L);
        assertThat(result.total()).isEqualTo(1L);
    }

    @Test
    void listQueue_filters_by_explicit_status_filter() {
        when(qualityLedgerEntryMapper.selectReviewerQueuePage("completed", null, "reviewer", 20L, 10L)).thenReturn(List.of());
        when(qualityLedgerEntryMapper.selectReviewerQueueCount("completed", null, "reviewer")).thenReturn(0L);

        PagedResult<ReviewerSubmissionQueueRow> result = reviewerQueueService.listQueue(3, 10, "completed", null, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(3L);
        assertThat(result.size()).isEqualTo(10L);
    }

    @Test
    void listQueue_filters_by_verdict_filter() {
        ReviewerSubmissionQueueRow row = row(301L, "submitted", 501L, "approve");
        when(qualityLedgerEntryMapper.selectReviewerQueuePage("submitted", "approved", "senior_reviewer", 0L, 20L)).thenReturn(List.of(row));
        when(qualityLedgerEntryMapper.selectReviewerQueueCount("submitted", "approved", "senior_reviewer")).thenReturn(1L);

        PagedResult<ReviewerSubmissionQueueRow> result = reviewerQueueService.listQueue(1, 20, "submitted", "approved", "senior_reviewer");

        assertThat(result.items()).extracting(ReviewerSubmissionQueueRow::getDerivedFromEntryId).containsExactly(501L);
    }

    @Test
    void listQueue_defaults_invalid_review_level_to_reviewer_queue() {
        when(qualityLedgerEntryMapper.selectReviewerQueuePage("submitted", null, "reviewer", 0L, 20L)).thenReturn(List.of());
        when(qualityLedgerEntryMapper.selectReviewerQueueCount("submitted", null, "reviewer")).thenReturn(0L);

        PagedResult<ReviewerSubmissionQueueRow> result = reviewerQueueService.listQueue(1, 20, "submitted", null, "owner");

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void listQueue_returns_paged_result_with_total() {
        when(qualityLedgerEntryMapper.selectReviewerQueuePage("submitted", "pending", "reviewer", 40L, 20L)).thenReturn(List.of());
        when(qualityLedgerEntryMapper.selectReviewerQueueCount("submitted", "pending", "reviewer")).thenReturn(42L);

        PagedResult<ReviewerSubmissionQueueRow> result = reviewerQueueService.listQueue(3, 20, "", "pending", "");

        assertThat(result.total()).isEqualTo(42L);
        assertThat(result.page()).isEqualTo(3L);
        assertThat(result.size()).isEqualTo(20L);
    }

    private static ReviewerSubmissionQueueRow row(Long id, String status, Long derivedFromEntryId, String reviewerVerdict) {
        ReviewerSubmissionQueueRow row = new ReviewerSubmissionQueueRow();
        row.setId(id);
        row.setTaskId(20L);
        row.setTaskTitle("Image QA");
        row.setLabelerId(2002L);
        row.setSchemaVersionId(80L);
        row.setStatusCode(status);
        row.setSubmittedAt(LocalDateTime.parse("2026-05-25T09:30:00"));
        row.setDerivedFromEntryId(derivedFromEntryId);
        row.setReviewerVerdict(reviewerVerdict);
        return row;
    }
}
