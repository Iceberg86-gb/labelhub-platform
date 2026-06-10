package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.exception.SelfReviewNotAllowedException;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewerBatchService {

    private final LedgerService ledgerService;

    public ReviewerBatchService(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Transactional
    public ReviewerBatchResult reviewSubmissions(
        List<Long> submissionIds,
        Long reviewerUserId,
        String verdict,
        String reason,
        String reviewLevel,
        Set<String> reviewerRoles
    ) {
        List<ReviewerBatchItemResult> items = new ArrayList<>();
        String effectiveReviewLevel = ReviewLevels.normalize(reviewLevel);
        for (Long submissionId : submissionIds == null ? List.<Long>of() : submissionIds) {
            items.add(reviewOne(submissionId, reviewerUserId, verdict, reason, effectiveReviewLevel, reviewerRoles));
        }
        return new ReviewerBatchResult(items);
    }

    private ReviewerBatchItemResult reviewOne(
        Long submissionId,
        Long reviewerUserId,
        String verdict,
        String reason,
        String reviewLevel,
        Set<String> reviewerRoles
    ) {
        try {
            QualityLedgerEntryEntity entity = ledgerService.createEntryInNewTransaction(
                submissionId,
                reviewerUserId,
                "reviewer_overall_verdict",
                payload(verdict, reason, reviewLevel),
                reviewerRoles
            );
            return new ReviewerBatchItemResult(submissionId, "created", entity.getId(), null);
        } catch (SelfReviewNotAllowedException exception) {
            return new ReviewerBatchItemResult(submissionId, "self_review_not_allowed", null, exception.getMessage());
        } catch (SubmissionNotFoundException exception) {
            return new ReviewerBatchItemResult(submissionId, "not_found", null, exception.getMessage());
        }
    }

    private Map<String, Object> payload(String verdict, String reason, String reviewLevel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("verdict", verdict);
        payload.put("reviewLevel", reviewLevel);
        if (reason != null) {
            payload.put("reason", reason);
        }
        return payload;
    }
}
