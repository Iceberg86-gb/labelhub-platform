ALTER TABLE label_schemas
    ADD COLUMN description TEXT NULL AFTER name,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

ALTER TABLE datasets
    ADD COLUMN source_name VARCHAR(255) NULL AFTER source_type,
    ADD COLUMN error_message TEXT NULL AFTER import_status;
