package com.labelhub.api.module.quality.exception;

public class SelfReviewNotAllowedException extends RuntimeException {
    public SelfReviewNotAllowedException(Long submissionId) {
        super("Reviewer cannot review their own submission: " + submissionId);
    }
}
