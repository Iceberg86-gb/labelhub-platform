package com.labelhub.api.module.ai.exception;

public class AiInputHashMismatchException extends RuntimeException {

    private final Long submissionId;
    private final String idempotencyKey;

    public AiInputHashMismatchException(String idempotencyKey) {
        super("Cannot reuse AI result because input changed for idempotency key " + idempotencyKey);
        this.submissionId = null;
        this.idempotencyKey = idempotencyKey;
    }

    public AiInputHashMismatchException(Long submissionId, String idempotencyKey) {
        super("Cannot reuse AI result because input changed for submission " + submissionId
            + " and idempotency key " + idempotencyKey);
        this.submissionId = submissionId;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getSubmissionId() { return submissionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
