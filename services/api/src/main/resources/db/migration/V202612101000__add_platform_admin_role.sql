ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false AFTER status;

INSERT IGNORE INTO roles (code, name)
VALUES ('PLATFORM_ADMIN', 'Platform Administrator');
