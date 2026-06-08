package com.labelhub.api.module.task.mapper;

public class TaskWorkflowProgressRow {
    private Long taskId;
    private Integer quotaTotal;
    private Integer quotaClaimed;
    private Long unclaimedCount;
    private Long labelingCount;
    private Long submittedCount;
    private Long aiPrereviewCompletedCount;
    private Long pendingReviewCount;
    private Long pendingSeniorReviewCount;
    private Long approvedCount;
    private Long rejectedCount;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getQuotaTotal() { return quotaTotal; }
    public void setQuotaTotal(Integer quotaTotal) { this.quotaTotal = quotaTotal; }
    public Integer getQuotaClaimed() { return quotaClaimed; }
    public void setQuotaClaimed(Integer quotaClaimed) { this.quotaClaimed = quotaClaimed; }
    public Long getUnclaimedCount() { return unclaimedCount; }
    public void setUnclaimedCount(Long unclaimedCount) { this.unclaimedCount = unclaimedCount; }
    public Long getLabelingCount() { return labelingCount; }
    public void setLabelingCount(Long labelingCount) { this.labelingCount = labelingCount; }
    public Long getSubmittedCount() { return submittedCount; }
    public void setSubmittedCount(Long submittedCount) { this.submittedCount = submittedCount; }
    public Long getAiPrereviewCompletedCount() { return aiPrereviewCompletedCount; }
    public void setAiPrereviewCompletedCount(Long aiPrereviewCompletedCount) { this.aiPrereviewCompletedCount = aiPrereviewCompletedCount; }
    public Long getPendingReviewCount() { return pendingReviewCount; }
    public void setPendingReviewCount(Long pendingReviewCount) { this.pendingReviewCount = pendingReviewCount; }
    public Long getPendingSeniorReviewCount() { return pendingSeniorReviewCount; }
    public void setPendingSeniorReviewCount(Long pendingSeniorReviewCount) { this.pendingSeniorReviewCount = pendingSeniorReviewCount; }
    public Long getApprovedCount() { return approvedCount; }
    public void setApprovedCount(Long approvedCount) { this.approvedCount = approvedCount; }
    public Long getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(Long rejectedCount) { this.rejectedCount = rejectedCount; }
}
