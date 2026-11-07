package com.labelhub.api.module.agentaccess.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.agentaccess.entity.McpAccessTokenEntity;
import com.labelhub.api.module.agentaccess.mapper.McpAccessTokenMapper;
import com.labelhub.api.module.agentaccess.mapper.McpSubmissionQueryMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ADR-022 §2: tokens are hashed, task-scoped, expire and revoke immediately. */
class McpTokenServiceTest {

    private static final Long TASK = 44L;
    private static final Long OWNER = 1001L;

    private final McpAccessTokenMapper tokenMapper = mock(McpAccessTokenMapper.class);
    private final McpSubmissionQueryMapper submissionQueryMapper = mock(McpSubmissionQueryMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final List<McpAccessTokenEntity> stored = new ArrayList<>();
    private Instant now = Instant.parse("2026-11-07T10:00:00Z");
    private McpTokenService service;

    @BeforeEach
    void setUp() {
        service = new McpTokenService(tokenMapper, submissionQueryMapper, taskMapper, audit, new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        });
        TaskEntity task = new TaskEntity();
        task.setId(TASK);
        task.setOwnerId(OWNER);
        when(taskMapper.selectById(TASK)).thenReturn(task);
        when(tokenMapper.insert(any())).thenAnswer(inv -> {
            McpAccessTokenEntity e = inv.getArgument(0);
            e.setId(1L + stored.size());
            stored.add(e);
            return 1;
        });
        when(tokenMapper.selectByHash(any())).thenAnswer(inv -> stored.stream().filter(e -> e.getTokenHash().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(tokenMapper.selectById(any())).thenAnswer(inv -> stored.stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(tokenMapper.revoke(any(), any(), any())).thenAnswer(inv -> {
            McpAccessTokenEntity e = stored.stream().filter(t -> t.getId().equals(inv.getArgument(0))).findFirst().orElseThrow();
            if (e.getRevokedAt() != null) {
                return 0;
            }
            e.setRevokedAt(inv.getArgument(1));
            e.setRevokedBy(inv.getArgument(2));
            return 1;
        });
    }

    @Test
    void issue_returns_plaintext_once_and_stores_only_the_hash() {
        McpTokenService.Issued issued = service.issue(TASK, OWNER, "claude code", null);

        assertThat(issued.plaintext()).startsWith("lhm_").hasSizeGreaterThan(40);
        McpAccessTokenEntity row = stored.get(0);
        assertThat(row.getTokenHash()).hasSize(64).isEqualTo(McpTokenService.sha256(issued.plaintext())).isNotEqualTo(issued.plaintext());
        assertThat(issued.plaintext()).startsWith(row.getTokenPrefix());
        assertThat(row.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-12-07T10:00:00"));
        verify(audit).record(any());
    }

    @Test
    void authorize_scopes_to_the_task_and_refuses_revoked_or_expired_tokens() {
        String token = service.issue(TASK, OWNER, "agent", 1).plaintext();
        when(submissionQueryMapper.selectTaskIdBySubmissionId(7L)).thenReturn(TASK);
        when(submissionQueryMapper.selectTaskIdBySubmissionId(8L)).thenReturn(99L);

        assertThat(service.authorize(token, null).taskId()).isEqualTo(TASK);
        assertThat(service.authorize(token, 7L).tokenId()).isEqualTo(1L);
        assertThatThrownBy(() -> service.authorize(token, 8L)).isInstanceOf(McpAccessException.class)
            .satisfies(e -> assertThat(((McpAccessException) e).kind()).isEqualTo(McpAccessException.Kind.NOT_FOUND));
        assertThatThrownBy(() -> service.authorize("lhm_nope", null)).satisfies(e -> assertThat(((McpAccessException) e).kind()).isEqualTo(McpAccessException.Kind.UNAUTHORIZED));
        assertThatThrownBy(() -> service.authorize(null, null)).isInstanceOf(McpAccessException.class);

        now = now.plusSeconds(86_400);  // expires after one day
        assertThatThrownBy(() -> service.authorize(token, null)).satisfies(e -> assertThat(((McpAccessException) e).kind()).isEqualTo(McpAccessException.Kind.UNAUTHORIZED));
    }

    @Test
    void revoke_takes_effect_immediately_is_idempotent_and_owner_only() {
        String token = service.issue(TASK, OWNER, "agent", null).plaintext();

        assertThatThrownBy(() -> service.revoke(1L, 2002L)).isInstanceOf(McpAccessException.class);
        McpAccessTokenEntity revoked = service.revoke(1L, OWNER);
        assertThat(service.status(revoked)).isEqualTo("revoked");
        service.revoke(1L, OWNER);
        verify(audit, times(2)).record(any());  // issue + one revoke
        assertThatThrownBy(() -> service.authorize(token, null)).isInstanceOf(McpAccessException.class);
    }

    @Test
    void only_the_task_owner_issues_or_lists_and_inputs_are_bounded() {
        assertThatThrownBy(() -> service.issue(TASK, 2002L, "x", null)).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.list(TASK, 2002L)).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.issue(TASK, OWNER, "  ", null)).isInstanceOf(McpAccessException.class);
        assertThatThrownBy(() -> service.issue(TASK, OWNER, "x", 91)).isInstanceOf(McpAccessException.class);
    }

    @Test
    void trajectory_summaries_mask_anything_token_shaped() {
        assertThat(AgentTrajectoryService.truncate("auth lhm_abcdefghijklmnop done")).isEqualTo("auth lhm_*** done");
        assertThat(AgentTrajectoryService.truncate("x".repeat(600))).hasSize(501).endsWith("…");
    }
}
