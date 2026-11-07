package com.labelhub.api.module.agentaccess.entity;

import java.time.LocalDateTime;

/** ADR-022 §4: one MCP tool call of an external agent run. Append-only. */
public class AgentTrajectoryStepEntity {

    private Long id;
    private String runId;
    private Integer stepNo;
    private Long taskId;
    private Long submissionId;
    private Long tokenId;
    private String clientName;
    private String clientVersion;
    private String toolName;
    private String inputHash;
    private String inputSummary;
    private String outputHash;
    private String outputSummary;
    private String status;
    private String policyDecision;
    private Long aiCallId;
    private Integer latencyMs;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public String getInputSummary() { return inputSummary; }
    public void setInputSummary(String inputSummary) { this.inputSummary = inputSummary; }
    public String getOutputHash() { return outputHash; }
    public void setOutputHash(String outputHash) { this.outputHash = outputHash; }
    public String getOutputSummary() { return outputSummary; }
    public void setOutputSummary(String outputSummary) { this.outputSummary = outputSummary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPolicyDecision() { return policyDecision; }
    public void setPolicyDecision(String policyDecision) { this.policyDecision = policyDecision; }
    public Long getAiCallId() { return aiCallId; }
    public void setAiCallId(Long aiCallId) { this.aiCallId = aiCallId; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
