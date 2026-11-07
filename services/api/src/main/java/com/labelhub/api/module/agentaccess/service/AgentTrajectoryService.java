package com.labelhub.api.module.agentaccess.service;

import com.labelhub.api.module.agentaccess.entity.AgentTrajectoryStepEntity;
import com.labelhub.api.module.agentaccess.entity.McpAccessTokenEntity;
import com.labelhub.api.module.agentaccess.mapper.AgentTrajectoryStepMapper;
import com.labelhub.api.module.agentaccess.mapper.AgentTrajectoryStepMapper.RunSummaryRow;
import com.labelhub.api.module.agentaccess.mapper.McpAccessTokenMapper;
import com.labelhub.api.module.agentaccess.mapper.McpSubmissionQueryMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * ADR-022 §4: the append-only trajectory of external agent runs. The agent's MCP server appends one step per
 * tool call; the API enforces that a run belongs to one token and task, that the submission is in that task,
 * and decides whether a submit_review proposal was recorded or replayed (from the ai_call's provider).
 */
@Service
public class AgentTrajectoryService {

    public static final int SUMMARY_MAX_CODE_POINTS = 500;
    public static final Set<String> STATUSES = Set.of("ok", "error", "denied");
    public static final String PROPOSAL_SUBMITTED = "proposal_submitted";
    public static final String PROPOSAL_RECORDED = "proposal_recorded";
    public static final String PROPOSAL_REPLAYED = "proposal_replayed";

    public record StepCommand(String runId, int stepNo, Long tokenId, Long taskId, Long submissionId, String clientName,
                              String clientVersion, String toolName, String inputHash, String inputSummary, String outputHash,
                              String outputSummary, String status, String policyDecision, String idempotencyKey, int latencyMs) {
    }

    private final AgentTrajectoryStepMapper stepMapper;
    private final McpAccessTokenMapper tokenMapper;
    private final McpSubmissionQueryMapper submissionQueryMapper;
    private final McpTokenService tokenService;
    private final Clock clock;

    public AgentTrajectoryService(AgentTrajectoryStepMapper stepMapper, McpAccessTokenMapper tokenMapper,
                                  McpSubmissionQueryMapper submissionQueryMapper, McpTokenService tokenService, Clock clock) {
        this.stepMapper = stepMapper;
        this.tokenMapper = tokenMapper;
        this.submissionQueryMapper = submissionQueryMapper;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    public AgentTrajectoryStepEntity append(StepCommand c) {
        McpAccessTokenEntity token = tokenMapper.selectById(c.tokenId());
        if (token == null || !Objects.equals(token.getTaskId(), c.taskId())) {
            throw new McpAccessException(McpAccessException.Kind.UNPROCESSABLE, "token does not belong to the task");
        }
        if (c.submissionId() != null && !Objects.equals(submissionQueryMapper.selectTaskIdBySubmissionId(c.submissionId()), c.taskId())) {
            throw new McpAccessException(McpAccessException.Kind.UNPROCESSABLE, "submission is not in the task");
        }
        if (!STATUSES.contains(c.status())) {
            throw new McpAccessException(McpAccessException.Kind.UNPROCESSABLE, "invalid status");
        }
        AgentTrajectoryStepEntity first = stepMapper.selectFirstByRunId(c.runId());
        if (first != null && (!Objects.equals(first.getTokenId(), c.tokenId()) || !Objects.equals(first.getTaskId(), c.taskId()))) {
            throw new McpAccessException(McpAccessException.Kind.CONFLICT, "run belongs to another token");
        }
        AgentTrajectoryStepEntity step = new AgentTrajectoryStepEntity();
        step.setRunId(c.runId());
        step.setStepNo(c.stepNo());
        step.setTaskId(c.taskId());
        step.setSubmissionId(c.submissionId());
        step.setTokenId(c.tokenId());
        step.setClientName(c.clientName());
        step.setClientVersion(c.clientVersion());
        step.setToolName(c.toolName());
        step.setInputHash(c.inputHash());
        step.setInputSummary(truncate(c.inputSummary()));
        step.setOutputHash(c.outputHash());
        step.setOutputSummary(truncate(c.outputSummary()));
        step.setStatus(c.status());
        step.setPolicyDecision(c.policyDecision());
        step.setLatencyMs(Math.max(0, c.latencyMs()));
        step.setCreatedAt(LocalDateTime.now(clock));
        if (PROPOSAL_SUBMITTED.equals(c.policyDecision()) && c.submissionId() != null && c.idempotencyKey() != null) {
            Long aiCallId = submissionQueryMapper.selectAiCallIdByIdempotencyKey(c.submissionId(), c.idempotencyKey());
            step.setAiCallId(aiCallId);
            String provider = aiCallId == null ? null : submissionQueryMapper.selectAiCallProvider(aiCallId);
            step.setPolicyDecision(Objects.equals(provider, "mcp:" + c.clientName()) ? PROPOSAL_RECORDED : PROPOSAL_REPLAYED);
        }
        try {
            stepMapper.insert(step);
        } catch (DuplicateKeyException ex) {
            throw new McpAccessException(McpAccessException.Kind.CONFLICT, "step " + c.stepNo() + " of run " + c.runId() + " already exists");
        }
        return step;
    }

    public List<RunSummaryRow> runs(Long taskId, Long ownerId) {
        tokenService.requireOwnedTask(taskId, ownerId);
        return stepMapper.selectRunSummariesByTaskId(taskId);
    }

    public List<AgentTrajectoryStepEntity> steps(String runId, Long ownerId) {
        AgentTrajectoryStepEntity first = stepMapper.selectFirstByRunId(runId);
        if (first == null) {
            throw new McpAccessException(McpAccessException.Kind.NOT_FOUND, "run not found");
        }
        try {
            tokenService.requireOwnedTask(first.getTaskId(), ownerId);
        } catch (com.labelhub.api.module.task.service.TaskNotFoundException ex) {
            throw new McpAccessException(McpAccessException.Kind.NOT_FOUND, "run not found");
        }
        return stepMapper.selectByRunId(runId);
    }

    private static final java.util.regex.Pattern TOKEN_LIKE = java.util.regex.Pattern.compile("lhm_[A-Za-z0-9_-]{8,}");

    /** Defense in depth: the agent redacts before sending; anything token-shaped is masked again here. */
    static String truncate(String raw) {
        String text = raw == null ? null : TOKEN_LIKE.matcher(raw).replaceAll("lhm_***");
        if (text == null || text.codePointCount(0, text.length()) <= SUMMARY_MAX_CODE_POINTS) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, SUMMARY_MAX_CODE_POINTS)) + "…";
    }
}
