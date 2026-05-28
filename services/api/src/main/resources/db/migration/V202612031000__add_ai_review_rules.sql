CREATE TABLE ai_review_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    current_prompt_version_id BIGINT NOT NULL,
    dimensions_json JSON NOT NULL,
    threshold DECIMAL(8,4) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    activated_at DATETIME(3),
    CONSTRAINT fk_ai_review_rules_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_ai_review_rules_prompt_version FOREIGN KEY (current_prompt_version_id) REFERENCES prompt_versions(id),
    CONSTRAINT fk_ai_review_rules_creator FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_ai_review_rules_task_version (task_id, version_no),
    INDEX idx_ai_review_rules_task_status (task_id, status),
    INDEX idx_ai_review_rules_prompt_version (current_prompt_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
