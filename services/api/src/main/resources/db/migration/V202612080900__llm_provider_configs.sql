CREATE TABLE llm_provider_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    provider_type VARCHAR(64) NOT NULL,
    provider_name VARCHAR(128) NOT NULL,
    base_url VARCHAR(512),
    model_name VARCHAR(128) NOT NULL,
    secret_ciphertext TEXT,
    secret_last4 VARCHAR(4),
    secret_updated_at DATETIME(3),
    secret_ref VARCHAR(512),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_llm_provider_configs_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    UNIQUE KEY uk_llm_provider_configs_owner_name (owner_id, provider_name),
    INDEX idx_llm_provider_configs_owner_enabled (owner_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
