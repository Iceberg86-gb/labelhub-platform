CREATE TABLE refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    INDEX idx_refresh_tokens_user_revoked (user_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
