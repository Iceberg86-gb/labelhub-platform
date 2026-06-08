package com.labelhub.api.module.task.service;

public record TaskWorkflowProgressView(
    Long taskId,
    Integer quotaTotal,
    Integer quotaClaimed,
    Long unclaimedCount,
    Long labelingCount,
    Long submittedCount,
    Long aiPrereviewCompletedCount,
    Long pendingReviewCount,
    Long pendingSeniorReviewCount,
    Long approvedCount,
    Long rejectedCount
) {
}
