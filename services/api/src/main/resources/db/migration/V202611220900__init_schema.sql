CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(80) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    instruction_rich_text MEDIUMTEXT,
    tags JSON,
    reward_rule JSON,
    deadline_at DATETIME(3),
    quota_total INT NOT NULL DEFAULT 0,
    quota_claimed INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    owner_id BIGINT NOT NULL,
    current_schema_version_id BIGINT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    INDEX idx_tasks_status_deadline (status, deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_transitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    reason VARCHAR(512),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_task_transitions_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_task_transitions_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_task_transitions_task_time (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE datasets (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    original_file_key VARCHAR(255),
    item_count INT NOT NULL DEFAULT 0,
    import_status VARCHAR(32) NOT NULL DEFAULT 'created',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_datasets_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    INDEX idx_datasets_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dataset_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    item_payload JSON NOT NULL,
    item_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'available',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_dataset_items_dataset FOREIGN KEY (dataset_id) REFERENCES datasets(id),
    CONSTRAINT fk_dataset_items_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    UNIQUE KEY uk_dataset_items_ordinal (dataset_id, ordinal),
    INDEX idx_dataset_items_task_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE label_schemas (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT,
    name VARCHAR(120) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_label_schemas_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_label_schemas_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE schema_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schema_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    schema_json JSON NOT NULL,
    field_stable_ids JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    published_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_schema_versions_schema FOREIGN KEY (schema_id) REFERENCES label_schemas(id),
    UNIQUE KEY uk_schema_versions_no (schema_id, version_no),
    UNIQUE KEY uk_schema_versions_hash (schema_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_schema_version
    FOREIGN KEY (current_schema_version_id) REFERENCES schema_versions(id);

CREATE TABLE sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    dataset_item_id BIGINT NOT NULL,
    labeler_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    claim_snapshot JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'claimed',
    claimed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    submitted_at DATETIME(3),
    CONSTRAINT fk_sessions_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_sessions_dataset_item FOREIGN KEY (dataset_item_id) REFERENCES dataset_items(id),
    CONSTRAINT fk_sessions_labeler FOREIGN KEY (labeler_id) REFERENCES users(id),
    CONSTRAINT fk_sessions_schema_version FOREIGN KEY (schema_version_id) REFERENCES schema_versions(id),
    UNIQUE KEY uk_sessions_item_labeler (dataset_item_id, labeler_id),
    INDEX idx_sessions_labeler_status (labeler_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE drafts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    draft_payload JSON NOT NULL,
    revision_no INT NOT NULL,
    saved_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_drafts_session FOREIGN KEY (session_id) REFERENCES sessions(id),
    UNIQUE KEY uk_drafts_session_revision (session_id, revision_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE submissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    dataset_item_id BIGINT NOT NULL,
    labeler_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    answer_payload JSON NOT NULL,
    provenance JSON,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(48) NOT NULL DEFAULT 'under_ai_review',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    superseded_by_id BIGINT,
    CONSTRAINT fk_submissions_session FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_submissions_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_submissions_item FOREIGN KEY (dataset_item_id) REFERENCES dataset_items(id),
    CONSTRAINT fk_submissions_labeler FOREIGN KEY (labeler_id) REFERENCES users(id),
    CONSTRAINT fk_submissions_schema_version FOREIGN KEY (schema_version_id) REFERENCES schema_versions(id),
    CONSTRAINT fk_submissions_superseded_by FOREIGN KEY (superseded_by_id) REFERENCES submissions(id),
    INDEX idx_submissions_task_status (task_id, status),
    INDEX idx_submissions_schema_version (schema_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT,
    field_path VARCHAR(255),
    purpose VARCHAR(48) NOT NULL,
    prompt_version VARCHAR(80) NOT NULL,
    model_provider VARCHAR(40) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    request_payload JSON NOT NULL,
    response_payload JSON,
    scores JSON,
    verdict VARCHAR(32),
    token_input INT NOT NULL DEFAULT 0,
    token_output INT NOT NULL DEFAULT 0,
    cost_decimal DECIMAL(12, 6) NOT NULL DEFAULT 0,
    latency_ms INT,
    status VARCHAR(32) NOT NULL DEFAULT 'created',
    idempotency_key VARCHAR(160) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3),
    CONSTRAINT fk_ai_calls_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    UNIQUE KEY uk_ai_calls_idempotency (idempotency_key),
    INDEX idx_ai_calls_submission (submission_id),
    INDEX idx_ai_calls_field (submission_id, field_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_calls_in_field (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    field_path VARCHAR(255) NOT NULL,
    ai_call_id BIGINT NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    user_modified_after BOOLEAN NOT NULL DEFAULT FALSE,
    ordinal INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_ai_calls_in_field_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_ai_calls_in_field_call FOREIGN KEY (ai_call_id) REFERENCES ai_calls(id),
    UNIQUE KEY uk_ai_calls_in_field_order (submission_id, field_path, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE adjudication_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    rule_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    activated_at DATETIME(3),
    CONSTRAINT fk_adjudication_rules_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_adjudication_rules_creator FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_adjudication_rules_task_version (task_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE quality_ledger_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    evidence_type VARCHAR(48) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id BIGINT,
    ai_call_id BIGINT,
    payload JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_quality_ledger_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_quality_ledger_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_quality_ledger_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT fk_quality_ledger_ai_call FOREIGN KEY (ai_call_id) REFERENCES ai_calls(id),
    INDEX idx_quality_ledger_submission_time (submission_id, created_at),
    INDEX idx_quality_ledger_task_time (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE current_verdicts (
    submission_id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    rule_version_id BIGINT NOT NULL,
    verdict VARCHAR(48) NOT NULL,
    confidence DECIMAL(5, 4),
    derived_from_ledger_entry_id BIGINT,
    derived_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_current_verdicts_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_current_verdicts_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_current_verdicts_rule FOREIGN KEY (rule_version_id) REFERENCES adjudication_rules(id),
    CONSTRAINT fk_current_verdicts_ledger FOREIGN KEY (derived_from_ledger_entry_id) REFERENCES quality_ledger_entries(id),
    INDEX idx_current_verdicts_task_verdict (task_id, verdict)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE review_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    review_level VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    structured_reason JSON,
    comment_text TEXT,
    round_no INT NOT NULL DEFAULT 1,
    diff_snapshot JSON,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_review_actions_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_review_actions_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_review_actions_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
    INDEX idx_review_actions_submission_round (submission_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE export_jobs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    format VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'created',
    parameters JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at DATETIME(3),
    completed_at DATETIME(3),
    file_key VARCHAR(255),
    file_size BIGINT,
    download_count INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_export_jobs_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_export_jobs_requester FOREIGN KEY (requested_by) REFERENCES users(id),
    INDEX idx_export_jobs_task_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE export_snapshots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    export_job_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    file_hash CHAR(64) NOT NULL,
    schema_version_ids JSON NOT NULL,
    verdict_rule_version_id BIGINT,
    data_scope JSON NOT NULL,
    field_mapping_snapshot JSON NOT NULL,
    canonicalization_version VARCHAR(40) NOT NULL,
    generated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_export_snapshots_job FOREIGN KEY (export_job_id) REFERENCES export_jobs(id),
    CONSTRAINT fk_export_snapshots_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_export_snapshots_rule FOREIGN KEY (verdict_rule_version_id) REFERENCES adjudication_rules(id),
    UNIQUE KEY uk_export_snapshots_file_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_type VARCHAR(32) NOT NULL,
    actor_id BIGINT,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id BIGINT,
    payload JSON,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_audit_logs_resource_time (resource_type, resource_id, created_at),
    INDEX idx_audit_logs_actor_time (actor_type, actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(80),
    locked_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3),
    INDEX idx_outbox_polling (status, next_retry_at, id),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO roles (code, name) VALUES
    ('OWNER', 'Owner'),
    ('LABELER', 'Labeler'),
    ('REVIEWER', 'Reviewer'),
    ('AI_AGENT', 'AI Agent');
