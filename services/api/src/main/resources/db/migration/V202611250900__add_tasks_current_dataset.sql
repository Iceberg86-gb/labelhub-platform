ALTER TABLE tasks
    ADD COLUMN current_dataset_id BIGINT NULL AFTER current_schema_version_id,
    ADD CONSTRAINT fk_tasks_current_dataset
        FOREIGN KEY (current_dataset_id) REFERENCES datasets(id);

CREATE INDEX idx_tasks_current_dataset ON tasks(current_dataset_id);
