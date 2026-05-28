ALTER TABLE ai_calls
    ADD COLUMN ai_review_rule_id BIGINT NULL AFTER prompt_version_id;

CREATE INDEX idx_ai_calls_ai_review_rule ON ai_calls(ai_review_rule_id);

ALTER TABLE ai_calls
    ADD CONSTRAINT fk_ai_calls_ai_review_rule
    FOREIGN KEY (ai_review_rule_id) REFERENCES ai_review_rules(id);
