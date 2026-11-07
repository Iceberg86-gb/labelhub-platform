package com.labelhub.api.module.agentaccess.service;

import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.agentaccess.entity.McpAccessTokenEntity;
import com.labelhub.api.module.agentaccess.mapper.McpAccessTokenMapper;
import com.labelhub.api.module.agentaccess.mapper.McpSubmissionQueryMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-022 §2: task-scoped MCP access tokens. The plaintext is returned once at issue time; only its SHA-256 is
 * stored. Every MCP tool call re-authorizes, so revocation and expiry take effect on the next call.
 */
@Service
public class McpTokenService {

    public static final String TOKEN_PREFIX = "lhm_";
    public static final int DEFAULT_EXPIRES_DAYS = 30;
    public static final int MAX_EXPIRES_DAYS = 90;

    public record Issued(McpAccessTokenEntity token, String plaintext) {
    }

    public record Grant(Long tokenId, Long taskId, String name) {
    }

    private final McpAccessTokenMapper tokenMapper;
    private final McpSubmissionQueryMapper submissionQueryMapper;
    private final TaskMapper taskMapper;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public McpTokenService(McpAccessTokenMapper tokenMapper, McpSubmissionQueryMapper submissionQueryMapper, TaskMapper taskMapper,
                           AuditLogService auditLogService, Clock clock) {
        this.tokenMapper = tokenMapper;
        this.submissionQueryMapper = submissionQueryMapper;
        this.taskMapper = taskMapper;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public Issued issue(Long taskId, Long ownerId, String name, Integer expiresInDays) {
        requireOwnedTask(taskId, ownerId);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new McpAccessException(McpAccessException.Kind.BAD_REQUEST, "name must be 1-80 characters");
        }
        int days = expiresInDays == null ? DEFAULT_EXPIRES_DAYS : expiresInDays;
        if (days < 1 || days > MAX_EXPIRES_DAYS) {
            throw new McpAccessException(McpAccessException.Kind.BAD_REQUEST, "expiresInDays must be between 1 and " + MAX_EXPIRES_DAYS);
        }
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String plaintext = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        LocalDateTime now = LocalDateTime.now(clock);
        McpAccessTokenEntity token = new McpAccessTokenEntity();
        token.setTaskId(taskId);
        token.setOwnerId(ownerId);
        token.setName(trimmed);
        token.setTokenHash(sha256(plaintext));
        token.setTokenPrefix(plaintext.substring(0, 12));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusDays(days));
        tokenMapper.insert(token);
        auditLogService.record(AuditEventBuilder.forAction(AuditActions.MCP_TOKEN_ISSUE)
            .actorUser(ownerId)
            .resource("mcp_access_token", token.getId())
            .payload("taskId", taskId)
            .payload("name", trimmed)
            .payload("tokenPrefix", token.getTokenPrefix())
            .payload("expiresAt", token.getExpiresAt().toString()));
        return new Issued(token, plaintext);
    }

    public List<McpAccessTokenEntity> list(Long taskId, Long ownerId) {
        requireOwnedTask(taskId, ownerId);
        return tokenMapper.selectByTaskId(taskId);
    }

    @Transactional
    public McpAccessTokenEntity revoke(Long tokenId, Long ownerId) {
        McpAccessTokenEntity token = tokenMapper.selectById(tokenId);
        if (token == null) {
            throw new McpAccessException(McpAccessException.Kind.NOT_FOUND, "token not found");
        }
        TaskEntity task = taskMapper.selectById(token.getTaskId());
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new McpAccessException(McpAccessException.Kind.NOT_FOUND, "token not found");
        }
        if (token.getRevokedAt() == null && tokenMapper.revoke(tokenId, LocalDateTime.now(clock), ownerId) == 1) {
            auditLogService.record(AuditEventBuilder.forAction(AuditActions.MCP_TOKEN_REVOKE)
                .actorUser(ownerId)
                .resource("mcp_access_token", tokenId)
                .payload("taskId", token.getTaskId())
                .payload("tokenPrefix", token.getTokenPrefix()));
        }
        return tokenMapper.selectById(tokenId);
    }

    /** Every MCP tool call: 401 for unknown/revoked/expired tokens, 404 for a submission outside the task. */
    public Grant authorize(String plaintext, Long submissionId) {
        if (plaintext == null || !plaintext.startsWith(TOKEN_PREFIX)) {
            throw new McpAccessException(McpAccessException.Kind.UNAUTHORIZED, "invalid token");
        }
        McpAccessTokenEntity token = tokenMapper.selectByHash(sha256(plaintext));
        if (token == null || !"active".equals(token.status(LocalDateTime.now(clock)))) {
            throw new McpAccessException(McpAccessException.Kind.UNAUTHORIZED, "invalid token");
        }
        if (submissionId != null && !Objects.equals(submissionQueryMapper.selectTaskIdBySubmissionId(submissionId), token.getTaskId())) {
            throw new McpAccessException(McpAccessException.Kind.NOT_FOUND, "submission not found");
        }
        return new Grant(token.getId(), token.getTaskId(), token.getName());
    }

    public String status(McpAccessTokenEntity token) {
        return token.status(LocalDateTime.now(clock));
    }

    void requireOwnedTask(Long taskId, Long ownerId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(taskId);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
