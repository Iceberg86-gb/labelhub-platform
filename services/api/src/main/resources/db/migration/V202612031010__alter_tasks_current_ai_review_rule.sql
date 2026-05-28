ALTER TABLE tasks
    ADD COLUMN current_ai_review_rule_id BIGINT NULL,
    ADD CONSTRAINT fk_tasks_current_ai_review_rule
        FOREIGN KEY (current_ai_review_rule_id) REFERENCES ai_review_rules(id),
    ADD INDEX idx_tasks_current_ai_review_rule (current_ai_review_rule_id);
