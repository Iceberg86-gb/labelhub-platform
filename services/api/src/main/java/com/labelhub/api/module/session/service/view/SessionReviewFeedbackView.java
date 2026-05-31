package com.labelhub.api.module.session.service.view;

import java.time.LocalDateTime;

public record SessionReviewFeedbackView(
    Long ledgerEntryId,
    Long reviewerUserId,
    String reason,
    LocalDateTime createdAt
) {
}
