CREATE TABLE prompt_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_no INT NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    owner_id BIGINT,
    published_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_prompt_versions_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    UNIQUE KEY uk_prompt_versions_no (version_no),
    UNIQUE KEY uk_prompt_versions_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
