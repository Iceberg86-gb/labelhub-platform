package com.labelhub.api.module.agentaccess.web;

import com.labelhub.api.generated.model.AgentRunSummary;
import com.labelhub.api.generated.model.AgentTrajectoryStep;
import com.labelhub.api.generated.model.McpToken;
import com.labelhub.api.generated.model.McpTokenIssueRequest;
import com.labelhub.api.generated.model.McpTokenIssued;
import com.labelhub.api.generated.web.AgentAccessApi;
import com.labelhub.api.module.agentaccess.service.AgentTrajectoryService;
import com.labelhub.api.module.agentaccess.service.McpTokenService;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ADR-022: owner-facing MCP token management and the trajectory viewer. */
@RestController
public class AgentAccessController implements AgentAccessApi {

    private final McpTokenService tokenService;
    private final AgentTrajectoryService trajectoryService;

    public AgentAccessController(McpTokenService tokenService, AgentTrajectoryService trajectoryService) {
        this.tokenService = tokenService;
        this.trajectoryService = trajectoryService;
    }

    @Override
    public ResponseEntity<McpTokenIssued> issueMcpToken(@PathVariable("taskId") Long taskId, @Valid @RequestBody McpTokenIssueRequest request) {
        McpTokenService.Issued issued = tokenService.issue(taskId, currentUserId(), request.getName(), request.getExpiresInDays());
        McpTokenIssued dto = new McpTokenIssued();
        dto.setToken(AgentAccessDtoMapper.token(issued.token(), tokenService.status(issued.token())));
        dto.setPlaintext(issued.plaintext());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Override
    public ResponseEntity<List<McpToken>> listMcpTokens(@PathVariable("taskId") Long taskId) {
        return ResponseEntity.ok(tokenService.list(taskId, currentUserId()).stream()
            .map(t -> AgentAccessDtoMapper.token(t, tokenService.status(t))).toList());
    }

    @Override
    public ResponseEntity<McpToken> revokeMcpToken(@PathVariable("tokenId") Long tokenId) {
        var token = tokenService.revoke(tokenId, currentUserId());
        return ResponseEntity.ok(AgentAccessDtoMapper.token(token, tokenService.status(token)));
    }

    @Override
    public ResponseEntity<List<AgentRunSummary>> listAgentRuns(@PathVariable("taskId") Long taskId) {
        return ResponseEntity.ok(trajectoryService.runs(taskId, currentUserId()).stream().map(AgentAccessDtoMapper::run).toList());
    }

    @Override
    public ResponseEntity<List<AgentTrajectoryStep>> listAgentRunSteps(@PathVariable("runId") String runId) {
        return ResponseEntity.ok(trajectoryService.steps(runId, currentUserId()).stream().map(AgentAccessDtoMapper::step).toList());
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }
}
