package com.labelhub.api.module.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "senior_review_cases", autoResultMap = true)
public class SeniorReviewCaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Long taskId;
    @TableField(exist = false)
    private String taskTitle;
    @TableField(exist = false)
    private String schemaName;
    @TableField(exist = false)
    private Integer schemaVersionNumber;
    private String caseKey;
    private String caseType;
    private String sourceSignal;
    private String status;
    private String priority;
    private Long reviewerVerdictEntryId;
    private Long aiOverallEntryId;
    private Long reviewerId;
    private Long seniorReviewerId;
    private String resolution;
    private String reason;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> accountability;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public Integer getSchemaVersionNumber() { return schemaVersionNumber; }
    public void setSchemaVersionNumber(Integer schemaVersionNumber) { this.schemaVersionNumber = schemaVersionNumber; }
    public String getCaseKey() { return caseKey; }
    public void setCaseKey(String caseKey) { this.caseKey = caseKey; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public String getSourceSignal() { return sourceSignal; }
    public void setSourceSignal(String sourceSignal) { this.sourceSignal = sourceSignal; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public Long getReviewerVerdictEntryId() { return reviewerVerdictEntryId; }
    public void setReviewerVerdictEntryId(Long reviewerVerdictEntryId) { this.reviewerVerdictEntryId = reviewerVerdictEntryId; }
    public Long getAiOverallEntryId() { return aiOverallEntryId; }
    public void setAiOverallEntryId(Long aiOverallEntryId) { this.aiOverallEntryId = aiOverallEntryId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public Long getSeniorReviewerId() { return seniorReviewerId; }
    public void setSeniorReviewerId(Long seniorReviewerId) { this.seniorReviewerId = seniorReviewerId; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Map<String, Object> getAccountability() { return accountability; }
    public void setAccountability(Map<String, Object> accountability) { this.accountability = accountability; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
