ALTER TABLE ai_calls
    ADD COLUMN prompt_version_id BIGINT NULL,
    ADD COLUMN provider_adapter_version VARCHAR(80) NOT NULL DEFAULT 'agent-default-v1',
    ADD CONSTRAINT fk_ai_calls_prompt_version
        FOREIGN KEY (prompt_version_id) REFERENCES prompt_versions(id),
    ADD INDEX idx_ai_calls_prompt_version (prompt_version_id);
