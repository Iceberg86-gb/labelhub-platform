ALTER TABLE label_schemas
    ADD COLUMN archived_at DATETIME(3) NULL AFTER updated_at,
    ADD INDEX idx_label_schemas_owner_task_archived (owner_id, task_id, archived_at, created_at, id);
