package com.labelhub.api.module.schema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "submissions", autoResultMap = true)
public class SubmissionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long taskId;
    private Long datasetItemId;
    private Long labelerId;
    private Long schemaVersionId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> answerPayload;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> provenance;
    private String contentHash;
    @TableField("status")
    private String statusCode;
    private LocalDateTime createdAt;
    private Long supersededById;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getDatasetItemId() { return datasetItemId; }
    public void setDatasetItemId(Long datasetItemId) { this.datasetItemId = datasetItemId; }
    public Long getLabelerId() { return labelerId; }
    public void setLabelerId(Long labelerId) { this.labelerId = labelerId; }
    public Long getSubmittedBy() { return labelerId; }
    public void setSubmittedBy(Long submittedBy) { this.labelerId = submittedBy; }
    public Long getSchemaVersionId() { return schemaVersionId; }
    public void setSchemaVersionId(Long schemaVersionId) { this.schemaVersionId = schemaVersionId; }
    public Map<String, Object> getAnswerPayload() { return answerPayload; }
    public void setAnswerPayload(Map<String, Object> answerPayload) { this.answerPayload = answerPayload; }
    public Map<String, Object> getProvenance() { return provenance; }
    public void setProvenance(Map<String, Object> provenance) { this.provenance = provenance; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getSupersededById() { return supersededById; }
    public void setSupersededById(Long supersededById) { this.supersededById = supersededById; }
}
