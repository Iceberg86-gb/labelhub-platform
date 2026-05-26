package com.labelhub.api.module.schema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@TableName(value = "schema_versions", autoResultMap = true)
public class SchemaVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long schemaId;
    @TableField("version_no")
    private Integer versionNumber;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> schemaJson;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fieldStableIds;
    private String contentHash;
    @TableField("status")
    private String statusCode;
    private LocalDateTime publishedAt;
    @TableField(exist = false)
    private Long ownerId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSchemaId() { return schemaId; }
    public void setSchemaId(Long schemaId) { this.schemaId = schemaId; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public Map<String, Object> getSchemaJson() { return schemaJson; }
    public void setSchemaJson(Map<String, Object> schemaJson) { this.schemaJson = schemaJson; }
    public List<String> getFieldStableIds() { return fieldStableIds; }
    public void setFieldStableIds(List<String> fieldStableIds) { this.fieldStableIds = fieldStableIds; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
