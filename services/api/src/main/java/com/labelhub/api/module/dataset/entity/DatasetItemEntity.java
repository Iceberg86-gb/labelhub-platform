package com.labelhub.api.module.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "dataset_items", autoResultMap = true)
public class DatasetItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private Long taskId;
    private Integer ordinal;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> itemPayload;
    private String itemHash;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getOrdinal() { return ordinal; }
    public void setOrdinal(Integer ordinal) { this.ordinal = ordinal; }
    public Map<String, Object> getItemPayload() { return itemPayload; }
    public void setItemPayload(Map<String, Object> itemPayload) { this.itemPayload = itemPayload; }
    public String getItemHash() { return itemHash; }
    public void setItemHash(String itemHash) { this.itemHash = itemHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
