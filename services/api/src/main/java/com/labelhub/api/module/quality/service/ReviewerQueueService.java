package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.mapper.ReviewerSubmissionQueueRow;
import com.labelhub.api.module.task.service.PagedResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewerQueueService {

    private static final String DEFAULT_STATUS = "submitted";

    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;

    public ReviewerQueueService(QualityLedgerEntryMapper qualityLedgerEntryMapper) {
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
    }

    public PagedResult<ReviewerSubmissionQueueRow> listQueue(
        long page,
        long size,
        String statusFilter,
        String verdictFilter,
        String reviewLevelFilter
    ) {
        String effectiveStatus = statusFilter == null || statusFilter.isBlank() ? DEFAULT_STATUS : statusFilter;
        String effectiveVerdict = verdictFilter == null || verdictFilter.isBlank() ? null : verdictFilter;
        String effectiveReviewLevel = ReviewLevels.normalize(reviewLevelFilter);
        long offset = (page - 1) * size;
        List<ReviewerSubmissionQueueRow> items =
            qualityLedgerEntryMapper.selectReviewerQueuePage(effectiveStatus, effectiveVerdict, effectiveReviewLevel, offset, size);
        Long total = qualityLedgerEntryMapper.selectReviewerQueueCount(effectiveStatus, effectiveVerdict, effectiveReviewLevel);
        return new PagedResult<>(items, total == null ? 0 : total, page, size);
    }
}
