ALTER TABLE export_snapshots DROP INDEX uk_export_snapshots_file_hash;
ALTER TABLE export_snapshots ADD INDEX idx_export_snapshots_file_hash (file_hash);

ALTER TABLE export_snapshots
    ADD COLUMN manifest_hash CHAR(64) NULL AFTER file_hash,
    ADD COLUMN source_state_hash CHAR(64) NULL AFTER manifest_hash,
    ADD COLUMN object_key VARCHAR(512) NULL AFTER source_state_hash,
    ADD COLUMN file_manifest JSON NULL AFTER object_key,
    ADD COLUMN record_counts JSON NULL AFTER file_manifest;

ALTER TABLE export_snapshots
    ADD INDEX idx_export_snapshots_manifest_hash (manifest_hash),
    ADD INDEX idx_export_snapshots_source_state_hash (source_state_hash);
