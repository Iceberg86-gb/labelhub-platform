package com.labelhub.api.module.schema.exception;

public class SubmissionNotFoundException extends RuntimeException {
    public SubmissionNotFoundException(Long submissionId) {
        super("Submission not found: " + submissionId);
    }
}
