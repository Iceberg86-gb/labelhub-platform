package com.labelhub.api.module.session.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "sessions", autoResultMap = true)
public class SessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long datasetItemId;
    private Long labelerId;
    private Long schemaVersionId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> claimSnapshot;
    private String status;
    @TableField(exist = false)
    private String workStatus;
    @TableField(exist = false)
    private String finalVerdict;
    private LocalDateTime claimedAt;
    private LocalDateTime submittedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getDatasetItemId() { return datasetItemId; }
    public void setDatasetItemId(Long datasetItemId) { this.datasetItemId = datasetItemId; }
    public Long getLabelerId() { return labelerId; }
    public void setLabelerId(Long labelerId) { this.labelerId = labelerId; }
    public Long getSchemaVersionId() { return schemaVersionId; }
    public void setSchemaVersionId(Long schemaVersionId) { this.schemaVersionId = schemaVersionId; }
    public Map<String, Object> getClaimSnapshot() { return claimSnapshot; }
    public void setClaimSnapshot(Map<String, Object> claimSnapshot) { this.claimSnapshot = claimSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWorkStatus() { return workStatus; }
    public void setWorkStatus(String workStatus) { this.workStatus = workStatus; }
    public String getFinalVerdict() { return finalVerdict; }
    public void setFinalVerdict(String finalVerdict) { this.finalVerdict = finalVerdict; }
    public LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(LocalDateTime claimedAt) { this.claimedAt = claimedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
