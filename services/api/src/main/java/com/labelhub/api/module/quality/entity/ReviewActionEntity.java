package com.labelhub.api.module.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "review_actions", autoResultMap = true)
public class ReviewActionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Long taskId;
    private Long reviewerId;
    private String reviewLevel;
    private String action;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> structuredReason;
    private String commentText;
    private Integer roundNo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> diffSnapshot;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewLevel() { return reviewLevel; }
    public void setReviewLevel(String reviewLevel) { this.reviewLevel = reviewLevel; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Map<String, Object> getStructuredReason() { return structuredReason; }
    public void setStructuredReason(Map<String, Object> structuredReason) { this.structuredReason = structuredReason; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Map<String, Object> getDiffSnapshot() { return diffSnapshot; }
    public void setDiffSnapshot(Map<String, Object> diffSnapshot) { this.diffSnapshot = diffSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
