package com.labelhub.api.module.admin.audit;

/**
 * Centralized registry of audit log action type strings.
 *
 * <p>Comments indicate implementation status:
 * <ul>
 *   <li>No comment: implemented in M7-P1 with default fail-fast same-transaction audit write
 *   <li>{@code // REQUIRES_NEW}: implemented in M7-P1 with REQUIRES_NEW propagation
 *       so the audit row persists across business transaction rollback
 *   <li>{@code // DEFERRED}: namespace reserved; write site not in M7-P1 scope
 * </ul>
 */
public final class AuditActions {

    public static final String TASK_TRANSITION = "task.transition";
    public static final String TASK_DELETE = "task.delete";
    public static final String SCHEMA_PUBLISH = "schema.publish";
    public static final String SCHEMA_ARCHIVE = "schema.archive";                            // DEFERRED: implementation in future phase
    public static final String SCHEMA_VERSION_CREATE = "schema.version_create";
    public static final String SUBMISSION_CREATE = "submission.create";
    public static final String SUBMISSION_SUPERSEDE = "submission.supersede";
    public static final String AI_REVIEW_FIELD_ASSIST = "ai_review.field_assist";
    public static final String AI_REVIEW_FAILED = "ai_review.failed";                        // REQUIRES_NEW: implementation lands in Cluster 3
    public static final String AI_REVIEW_RECORDED_FAILED_CALL = "ai_review.recorded_failed_call";
    public static final String REVIEW_APPROVE = "review.approve";
    public static final String REVIEW_REJECT = "review.reject";
    public static final String EXPORT_SNAPSHOT_CREATE = "export.snapshot_create";
    public static final String EXPORT_SNAPSHOT_ARCHIVE = "export.snapshot_archive";
    public static final String EXPORT_SNAPSHOT_DIFF = "export.snapshot_diff";                // DEFERRED: implementation in future phase

    private AuditActions() {}
}
