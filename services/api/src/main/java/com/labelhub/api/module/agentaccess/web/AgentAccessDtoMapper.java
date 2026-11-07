package com.labelhub.api.module.agentaccess.web;

import com.labelhub.api.generated.model.AgentRunSummary;
import com.labelhub.api.generated.model.AgentTrajectoryStep;
import com.labelhub.api.generated.model.McpToken;
import com.labelhub.api.module.agentaccess.entity.AgentTrajectoryStepEntity;
import com.labelhub.api.module.agentaccess.entity.McpAccessTokenEntity;
import com.labelhub.api.module.agentaccess.mapper.AgentTrajectoryStepMapper.RunSummaryRow;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class AgentAccessDtoMapper {

    private AgentAccessDtoMapper() {
    }

    static OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    static McpToken token(McpAccessTokenEntity e, String status) {
        McpToken dto = new McpToken();
        dto.setId(e.getId());
        dto.setTaskId(e.getTaskId());
        dto.setName(e.getName());
        dto.setTokenPrefix(e.getTokenPrefix());
        dto.setCreatedAt(utc(e.getCreatedAt()));
        dto.setExpiresAt(utc(e.getExpiresAt()));
        dto.setRevokedAt(utc(e.getRevokedAt()));
        dto.setStatus(McpToken.StatusEnum.fromValue(status));
        return dto;
    }

    static AgentTrajectoryStep step(AgentTrajectoryStepEntity e) {
        AgentTrajectoryStep dto = new AgentTrajectoryStep();
        dto.setId(e.getId());
        dto.setRunId(e.getRunId());
        dto.setStepNo(e.getStepNo());
        dto.setTaskId(e.getTaskId());
        dto.setSubmissionId(e.getSubmissionId());
        dto.setTokenId(e.getTokenId());
        dto.setClientName(e.getClientName());
        dto.setClientVersion(e.getClientVersion());
        dto.setToolName(e.getToolName());
        dto.setInputHash(e.getInputHash());
        dto.setInputSummary(e.getInputSummary());
        dto.setOutputHash(e.getOutputHash());
        dto.setOutputSummary(e.getOutputSummary());
        dto.setStatus(AgentTrajectoryStep.StatusEnum.fromValue(e.getStatus()));
        dto.setPolicyDecision(e.getPolicyDecision());
        dto.setAiCallId(e.getAiCallId());
        dto.setLatencyMs(e.getLatencyMs());
        dto.setCreatedAt(utc(e.getCreatedAt()));
        return dto;
    }

    static AgentRunSummary run(RunSummaryRow r) {
        AgentRunSummary dto = new AgentRunSummary();
        dto.setRunId(r.getRunId());
        dto.setTokenId(r.getTokenId());
        dto.setTokenName(r.getTokenName());
        dto.setClientName(r.getClientName());
        dto.setClientVersion(r.getClientVersion());
        dto.setSteps(r.getSteps() == null ? 0 : r.getSteps().intValue());
        dto.setProposals(r.getProposals() == null ? 0 : r.getProposals().intValue());
        dto.setStartedAt(utc(r.getStartedAt()));
        dto.setLastStepAt(utc(r.getLastStepAt()));
        dto.setLastStatus(AgentRunSummary.LastStatusEnum.fromValue(r.getLastStatus()));
        return dto;
    }
}
