package com.labelhub.api.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_review_rules")
public class AiReviewRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    @TableField("version_no")
    private Integer versionNumber;
    private Long currentPromptVersionId;
    private String dimensionsJson;
    private BigDecimal threshold;
    @TableField("status")
    private String statusCode;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public Long getCurrentPromptVersionId() { return currentPromptVersionId; }
    public void setCurrentPromptVersionId(Long currentPromptVersionId) { this.currentPromptVersionId = currentPromptVersionId; }
    public String getDimensionsJson() { return dimensionsJson; }
    public void setDimensionsJson(String dimensionsJson) { this.dimensionsJson = dimensionsJson; }
    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }
}
