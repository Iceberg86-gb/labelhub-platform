ALTER TABLE submissions
    MODIFY COLUMN status VARCHAR(48) NOT NULL DEFAULT 'submitted';

UPDATE submissions
SET status = 'submitted'
WHERE status = 'under_ai_review';
