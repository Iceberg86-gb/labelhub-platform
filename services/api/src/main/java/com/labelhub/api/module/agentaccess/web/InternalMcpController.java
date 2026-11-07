package com.labelhub.api.module.agentaccess.web;

import com.labelhub.api.generated.model.AgentTrajectoryStep;
import com.labelhub.api.generated.model.AgentTrajectoryStepRequest;
import com.labelhub.api.generated.model.McpAuthorizeRequest;
import com.labelhub.api.generated.model.McpGrant;
import com.labelhub.api.generated.model.McpOpenSubmission;
import com.labelhub.api.generated.web.InternalMcpApi;
import com.labelhub.api.module.agentaccess.mapper.McpSubmissionQueryMapper;
import com.labelhub.api.module.agentaccess.service.AgentTrajectoryService;
import com.labelhub.api.module.agentaccess.service.McpTokenService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ADR-022: endpoints the agent's MCP server calls (X-Internal-Token, like every /internal route). */
@RestController
public class InternalMcpController implements InternalMcpApi {

    private final McpTokenService tokenService;
    private final AgentTrajectoryService trajectoryService;
    private final McpSubmissionQueryMapper submissionQueryMapper;

    public InternalMcpController(McpTokenService tokenService, AgentTrajectoryService trajectoryService,
                                 McpSubmissionQueryMapper submissionQueryMapper) {
        this.tokenService = tokenService;
        this.trajectoryService = trajectoryService;
        this.submissionQueryMapper = submissionQueryMapper;
    }

    @Override
    public ResponseEntity<McpGrant> authorizeMcpCall(@Valid @RequestBody McpAuthorizeRequest request) {
        McpTokenService.Grant grant = tokenService.authorize(request.getToken(), request.getSubmissionId());
        McpGrant dto = new McpGrant();
        dto.setTokenId(grant.tokenId());
        dto.setTaskId(grant.taskId());
        dto.setName(grant.name());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<List<McpOpenSubmission>> listMcpOpenSubmissions(@PathVariable("taskId") Long taskId, Integer limit) {
        int n = limit == null ? 20 : Math.max(1, Math.min(50, limit));
        return ResponseEntity.ok(submissionQueryMapper.selectOpenSubmissions(taskId, n).stream().map(row -> {
            McpOpenSubmission dto = new McpOpenSubmission();
            dto.setSubmissionId(row.getSubmissionId());
            dto.setDatasetItemId(row.getDatasetItemId());
            dto.setCreatedAt(AgentAccessDtoMapper.utc(row.getCreatedAt()));
            return dto;
        }).toList());
    }

    @Override
    public ResponseEntity<AgentTrajectoryStep> appendAgentTrajectoryStep(@Valid @RequestBody AgentTrajectoryStepRequest r) {
        var step = trajectoryService.append(new AgentTrajectoryService.StepCommand(r.getRunId(), r.getStepNo(), r.getTokenId(),
            r.getTaskId(), r.getSubmissionId(), r.getClientName(), r.getClientVersion(), r.getToolName(), r.getInputHash(),
            r.getInputSummary(), r.getOutputHash(), r.getOutputSummary(), r.getStatus().getValue(), r.getPolicyDecision(),
            r.getIdempotencyKey(), r.getLatencyMs()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AgentAccessDtoMapper.step(step));
    }
}
