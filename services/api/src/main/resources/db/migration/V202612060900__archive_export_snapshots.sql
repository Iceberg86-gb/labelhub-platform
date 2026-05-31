ALTER TABLE export_snapshots
    ADD COLUMN archived_at DATETIME(3) NULL AFTER generated_at,
    ADD INDEX idx_export_snapshots_task_archived (task_id, archived_at, generated_at, id);
