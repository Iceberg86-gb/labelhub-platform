package com.labelhub.api.module.quality.mapper;

import java.time.LocalDateTime;

public class ReviewerSubmissionQueueRow {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private Long labelerId;
    private Long schemaVersionId;
    private String statusCode;
    private LocalDateTime submittedAt;
    private Long derivedFromEntryId;
    private String reviewerVerdict;
    private String reviewLevel;
    private String aiRecommendation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }
    public Long getLabelerId() { return labelerId; }
    public void setLabelerId(Long labelerId) { this.labelerId = labelerId; }
    public Long getSchemaVersionId() { return schemaVersionId; }
    public void setSchemaVersionId(Long schemaVersionId) { this.schemaVersionId = schemaVersionId; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getDerivedFromEntryId() { return derivedFromEntryId; }
    public void setDerivedFromEntryId(Long derivedFromEntryId) { this.derivedFromEntryId = derivedFromEntryId; }
    public String getReviewerVerdict() { return reviewerVerdict; }
    public void setReviewerVerdict(String reviewerVerdict) { this.reviewerVerdict = reviewerVerdict; }
    public String getReviewLevel() { return reviewLevel; }
    public void setReviewLevel(String reviewLevel) { this.reviewLevel = reviewLevel; }
    public String getAiRecommendation() { return aiRecommendation; }
    public void setAiRecommendation(String aiRecommendation) { this.aiRecommendation = aiRecommendation; }
}
