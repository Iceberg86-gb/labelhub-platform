-- ADR-020 §3 (W3b): auto-pass policy as an append-only event log. The current mode of a rule version is its
-- latest event: enable -> auto; disable / circuit_break -> suggest. Rows are never updated or deleted
-- (except by the owner's whole-task hard delete, like every other task-scoped fact).
CREATE TABLE ai_review_auto_policy_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ai_review_rule_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    prompt_version_id BIGINT NULL,
    model_name VARCHAR(120) NULL,
    max_error DECIMAL(8,6) NULL,
    min_samples INT NULL,
    sample_rate DECIMAL(8,6) NULL,
    evidence JSON NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_auto_policy_rule FOREIGN KEY (ai_review_rule_id) REFERENCES ai_review_rules(id),
    CONSTRAINT fk_auto_policy_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_auto_policy_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_auto_policy_action CHECK (action IN ('enable', 'disable', 'circuit_break')),
    INDEX idx_auto_policy_rule_time (ai_review_rule_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
