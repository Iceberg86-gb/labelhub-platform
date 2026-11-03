package com.labelhub.api.module.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** ADR-020 §3: one append-only auto-pass policy event (enable / disable / circuit_break). */
public class AiReviewAutoPolicyEventEntity {

    public static final String ENABLE = "enable";
    public static final String DISABLE = "disable";
    public static final String CIRCUIT_BREAK = "circuit_break";

    private Long id;
    private Long aiReviewRuleId;
    private Long taskId;
    private String action;
    private Long promptVersionId;
    private String modelName;
    private BigDecimal maxError;
    private Integer minSamples;
    private BigDecimal sampleRate;
    private Map<String, Object> evidence;
    private String actorType;
    private Long actorId;
    private String reason;
    private LocalDateTime createdAt;

    public boolean isEnable() { return ENABLE.equals(action); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAiReviewRuleId() { return aiReviewRuleId; }
    public void setAiReviewRuleId(Long aiReviewRuleId) { this.aiReviewRuleId = aiReviewRuleId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getPromptVersionId() { return promptVersionId; }
    public void setPromptVersionId(Long promptVersionId) { this.promptVersionId = promptVersionId; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public BigDecimal getMaxError() { return maxError; }
    public void setMaxError(BigDecimal maxError) { this.maxError = maxError; }
    public Integer getMinSamples() { return minSamples; }
    public void setMinSamples(Integer minSamples) { this.minSamples = minSamples; }
    public BigDecimal getSampleRate() { return sampleRate; }
    public void setSampleRate(BigDecimal sampleRate) { this.sampleRate = sampleRate; }
    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
