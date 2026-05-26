ALTER TABLE label_schemas
    ADD COLUMN current_version_id BIGINT NULL AFTER owner_id,
    ADD CONSTRAINT fk_label_schemas_current_version
        FOREIGN KEY (current_version_id) REFERENCES schema_versions(id);

CREATE INDEX idx_label_schemas_current_version ON label_schemas(current_version_id);
