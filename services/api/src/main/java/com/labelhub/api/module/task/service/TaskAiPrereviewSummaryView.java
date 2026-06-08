package com.labelhub.api.module.task.service;

public record TaskAiPrereviewSummaryView(
    Long taskId,
    Long totalCount,
    Long pendingCount,
    Long processingCount,
    Long completedCount,
    Long failedCount,
    Long enqueueableCount
) {
}
