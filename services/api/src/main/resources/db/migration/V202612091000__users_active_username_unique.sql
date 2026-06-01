ALTER TABLE users DROP INDEX username;

ALTER TABLE users
    ADD COLUMN active_username VARCHAR(80)
        GENERATED ALWAYS AS (IF(status='active', username, NULL)) VIRTUAL;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_active_username (active_username);
