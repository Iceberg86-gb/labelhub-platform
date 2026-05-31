SELECT COUNT(*) INTO @invalid_task_status_count
FROM tasks
WHERE status NOT IN ('draft', 'published', 'paused', 'ended');

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_status
    CHECK (status IN ('draft', 'published', 'paused', 'ended'));
