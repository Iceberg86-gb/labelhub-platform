package com.labelhub.api.module.ai.precedent;

/**
 * One precedent as the model sees it (ADR-021 §3), plus the submission id for audit. {@code submission}
 * is already the {@code path: text} excerpt.
 */
public record Precedent(int rank, Long submissionId, String verdict, String source, String submission) {

    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String SOURCE_REVIEWER = "reviewer";
    public static final String SOURCE_SENIOR = "senior";
}
