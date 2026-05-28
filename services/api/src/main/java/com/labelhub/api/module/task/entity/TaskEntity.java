package com.labelhub.api.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.generated.model.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@TableName(value = "tasks", autoResultMap = true)
public class TaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String instructionRichText;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rewardRule;
    private LocalDateTime deadlineAt;
    private Integer quotaTotal;
    private Integer quotaClaimed;
    @TableField("status")
    private String statusCode;
    private Long ownerId;
    private Long currentSchemaVersionId;
    private Long currentDatasetId;
    private Long currentAiReviewRuleId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInstructionRichText() { return instructionRichText; }
    public void setInstructionRichText(String instructionRichText) { this.instructionRichText = instructionRichText; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public Map<String, Object> getRewardRule() { return rewardRule; }
    public void setRewardRule(Map<String, Object> rewardRule) { this.rewardRule = rewardRule; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public Integer getQuotaTotal() { return quotaTotal; }
    public void setQuotaTotal(Integer quotaTotal) { this.quotaTotal = quotaTotal; }
    public Integer getQuotaClaimed() { return quotaClaimed; }
    public void setQuotaClaimed(Integer quotaClaimed) { this.quotaClaimed = quotaClaimed; }
    public TaskStatus getStatus() { return TaskStatus.fromValue(statusCode); }
    public void setStatus(TaskStatus status) { this.statusCode = status == null ? null : status.getValue(); }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getCurrentSchemaVersionId() { return currentSchemaVersionId; }
    public void setCurrentSchemaVersionId(Long currentSchemaVersionId) { this.currentSchemaVersionId = currentSchemaVersionId; }
    public Long getCurrentDatasetId() { return currentDatasetId; }
    public void setCurrentDatasetId(Long currentDatasetId) { this.currentDatasetId = currentDatasetId; }
    public Long getCurrentAiReviewRuleId() { return currentAiReviewRuleId; }
    public void setCurrentAiReviewRuleId(Long currentAiReviewRuleId) { this.currentAiReviewRuleId = currentAiReviewRuleId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
