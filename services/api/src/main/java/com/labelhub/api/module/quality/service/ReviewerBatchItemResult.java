package com.labelhub.api.module.quality.service;

public record ReviewerBatchItemResult(
    Long submissionId,
    String status,
    Long ledgerEntryId,
    String error
) {
}
