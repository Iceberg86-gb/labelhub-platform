package com.labelhub.api.module.ai.prereview;

import java.time.LocalDateTime;

public class AiPrereviewSignalRow {

    private Long submissionId;
    private String outboxStatus;
    private LocalDateTime outboxLockedAt;
    private String aiCallStatus;
    private Boolean hasAiOverallRecommendation;

    public AiPrereviewSignalRow() {
    }

    public AiPrereviewSignalRow(
        Long submissionId,
        String outboxStatus,
        LocalDateTime outboxLockedAt,
        String aiCallStatus,
        Boolean hasAiOverallRecommendation
    ) {
        this.submissionId = submissionId;
        this.outboxStatus = outboxStatus;
        this.outboxLockedAt = outboxLockedAt;
        this.aiCallStatus = aiCallStatus;
        this.hasAiOverallRecommendation = hasAiOverallRecommendation;
    }

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public String getOutboxStatus() { return outboxStatus; }
    public void setOutboxStatus(String outboxStatus) { this.outboxStatus = outboxStatus; }
    public LocalDateTime getOutboxLockedAt() { return outboxLockedAt; }
    public void setOutboxLockedAt(LocalDateTime outboxLockedAt) { this.outboxLockedAt = outboxLockedAt; }
    public String getAiCallStatus() { return aiCallStatus; }
    public void setAiCallStatus(String aiCallStatus) { this.aiCallStatus = aiCallStatus; }
    public Boolean getHasAiOverallRecommendation() { return hasAiOverallRecommendation; }
    public void setHasAiOverallRecommendation(Boolean hasAiOverallRecommendation) {
        this.hasAiOverallRecommendation = hasAiOverallRecommendation;
    }
}
