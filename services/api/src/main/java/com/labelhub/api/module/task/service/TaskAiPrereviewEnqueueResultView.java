package com.labelhub.api.module.task.service;

public record TaskAiPrereviewEnqueueResultView(
    Long taskId,
    Long enqueuedCount,
    Long skippedCount,
    TaskAiPrereviewSummaryView summary
) {
}
