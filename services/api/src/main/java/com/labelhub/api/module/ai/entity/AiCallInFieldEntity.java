package com.labelhub.api.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_calls_in_field")
public class AiCallInFieldEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private String fieldPath;
    private Long aiCallId;
    private Boolean accepted;
    private Boolean userModifiedAfter;
    private Integer ordinal;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public Long getAiCallId() { return aiCallId; }
    public void setAiCallId(Long aiCallId) { this.aiCallId = aiCallId; }
    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }
    public Boolean getUserModifiedAfter() { return userModifiedAfter; }
    public void setUserModifiedAfter(Boolean userModifiedAfter) { this.userModifiedAfter = userModifiedAfter; }
    public Integer getOrdinal() { return ordinal; }
    public void setOrdinal(Integer ordinal) { this.ordinal = ordinal; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
