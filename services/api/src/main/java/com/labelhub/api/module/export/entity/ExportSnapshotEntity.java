package com.labelhub.api.module.export.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@TableName(value = "export_snapshots", autoResultMap = true)
public class ExportSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long exportJobId;
    private Long taskId;
    private String fileHash;
    private String manifestHash;
    private String sourceStateHash;
    private String objectKey;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> fileManifest;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> recordCounts;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> schemaVersionIds;
    private Long verdictRuleVersionId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> dataScope;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> fieldMappingSnapshot;
    private String canonicalizationVersion;
    private LocalDateTime generatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExportJobId() { return exportJobId; }
    public void setExportJobId(Long exportJobId) { this.exportJobId = exportJobId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getManifestHash() { return manifestHash; }
    public void setManifestHash(String manifestHash) { this.manifestHash = manifestHash; }
    public String getSourceStateHash() { return sourceStateHash; }
    public void setSourceStateHash(String sourceStateHash) { this.sourceStateHash = sourceStateHash; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public Map<String, Object> getFileManifest() { return fileManifest; }
    public void setFileManifest(Map<String, Object> fileManifest) { this.fileManifest = fileManifest; }
    public Map<String, Integer> getRecordCounts() { return recordCounts; }
    public void setRecordCounts(Map<String, Integer> recordCounts) { this.recordCounts = recordCounts; }
    public List<Long> getSchemaVersionIds() { return schemaVersionIds; }
    public void setSchemaVersionIds(List<Long> schemaVersionIds) { this.schemaVersionIds = schemaVersionIds; }
    public Long getVerdictRuleVersionId() { return verdictRuleVersionId; }
    public void setVerdictRuleVersionId(Long verdictRuleVersionId) { this.verdictRuleVersionId = verdictRuleVersionId; }
    public Map<String, Object> getDataScope() { return dataScope; }
    public void setDataScope(Map<String, Object> dataScope) { this.dataScope = dataScope; }
    public Map<String, Object> getFieldMappingSnapshot() { return fieldMappingSnapshot; }
    public void setFieldMappingSnapshot(Map<String, Object> fieldMappingSnapshot) { this.fieldMappingSnapshot = fieldMappingSnapshot; }
    public String getCanonicalizationVersion() { return canonicalizationVersion; }
    public void setCanonicalizationVersion(String canonicalizationVersion) { this.canonicalizationVersion = canonicalizationVersion; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
