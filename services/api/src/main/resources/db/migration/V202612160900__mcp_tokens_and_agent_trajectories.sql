-- ADR-022 (H5): task-scoped MCP access tokens for external agents, and their append-only trajectories.

-- Current-state table: only revoked_at / revoked_by change (revocation). The token itself is never stored,
-- only its SHA-256; issuing and revoking are written to audit_logs.
CREATE TABLE mcp_access_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at DATETIME(3) NULL,
    revoked_by BIGINT NULL,
    CONSTRAINT fk_mcp_tokens_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_mcp_tokens_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_mcp_tokens_revoker FOREIGN KEY (revoked_by) REFERENCES users(id),
    UNIQUE KEY uk_mcp_tokens_hash (token_hash),
    INDEX idx_mcp_tokens_task (task_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Append-only (conventions §4): one row per MCP tool call. Inputs/outputs are kept as canonical SHA-256 plus a
-- redacted, truncated summary. Rows are never updated or deleted (except by the owner's whole-task hard delete).
CREATE TABLE agent_trajectory_steps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    step_no INT NOT NULL,
    task_id BIGINT NOT NULL,
    submission_id BIGINT NULL,
    token_id BIGINT NOT NULL,
    client_name VARCHAR(128) NOT NULL,
    client_version VARCHAR(64) NULL,
    tool_name VARCHAR(64) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    input_summary TEXT NULL,
    output_hash CHAR(64) NULL,
    output_summary TEXT NULL,
    status VARCHAR(16) NOT NULL,
    policy_decision VARCHAR(48) NULL,
    ai_call_id BIGINT NULL,
    latency_ms INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_trajectory_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_trajectory_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_trajectory_token FOREIGN KEY (token_id) REFERENCES mcp_access_tokens(id),
    CONSTRAINT fk_trajectory_ai_call FOREIGN KEY (ai_call_id) REFERENCES ai_calls(id),
    CONSTRAINT chk_trajectory_status CHECK (status IN ('ok', 'error', 'denied')),
    UNIQUE KEY uk_trajectory_run_step (run_id, step_no),
    INDEX idx_trajectory_task_time (task_id, created_at, id),
    INDEX idx_trajectory_submission (submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
