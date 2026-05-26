package com.labelhub.api.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "ai_calls", autoResultMap = true)
public class AiCallEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private String fieldPath;
    private String purpose;
    private String promptVersion;
    @TableField("model_provider")
    private String modelProvider;
    private String modelName;
    private String inputHash;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestPayload;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responsePayload;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> scores;
    private String verdict;
    private Integer tokenInput;
    private Integer tokenOutput;
    private BigDecimal costDecimal;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer cacheHitTokens;
    private Integer latencyMs;
    private String status;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    @TableField(exist = false)
    private String outputHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getModelProvider() { return modelProvider; }
    public void setModelProvider(String modelProvider) { this.modelProvider = modelProvider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public Map<String, Object> getRequestPayload() { return requestPayload; }
    public void setRequestPayload(Map<String, Object> requestPayload) { this.requestPayload = requestPayload; }
    public Map<String, Object> getResponsePayload() { return responsePayload; }
    public void setResponsePayload(Map<String, Object> responsePayload) { this.responsePayload = responsePayload; }
    public Map<String, Object> getScores() { return scores; }
    public void setScores(Map<String, Object> scores) { this.scores = scores; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public Integer getTokenInput() { return tokenInput; }
    public void setTokenInput(Integer tokenInput) { this.tokenInput = tokenInput; }
    public Integer getTokenOutput() { return tokenOutput; }
    public void setTokenOutput(Integer tokenOutput) { this.tokenOutput = tokenOutput; }
    public BigDecimal getCostDecimal() { return costDecimal; }
    public void setCostDecimal(BigDecimal costDecimal) { this.costDecimal = costDecimal; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Integer getCacheHitTokens() { return cacheHitTokens; }
    public void setCacheHitTokens(Integer cacheHitTokens) { this.cacheHitTokens = cacheHitTokens; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getOutputHash() { return outputHash; }
    public void setOutputHash(String outputHash) { this.outputHash = outputHash; }
}
