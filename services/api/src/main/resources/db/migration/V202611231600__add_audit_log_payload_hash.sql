ALTER TABLE audit_logs
  ADD COLUMN payload_hash CHAR(64) NULL AFTER payload;
