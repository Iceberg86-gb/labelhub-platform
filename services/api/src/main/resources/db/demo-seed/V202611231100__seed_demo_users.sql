-- Demo accounts for M1 local defense flow. Password for all accounts: demo1234.
INSERT IGNORE INTO users (id, username, password_hash, display_name, status) VALUES
    (1001, 'owner_demo', '$2y$10$acwRCkgseKk2nBe4/B6Rc.L9Uj27tZJvVcUSFtOulVTe16BhTb3Ti', '演示 Owner', 'active'),
    (1002, 'labeler_demo', '$2y$10$SIdRICWTEbYa5zL85HSD0.4vTN4AlFCGLWi0FTNYwC29.kZcfHc0a', '演示 Labeler', 'active'),
    (1003, 'reviewer_demo', '$2y$10$8AFisjtIgwkCI7uTkT0ZaeUvQ01M6HO6jxUwseavTIZkurDbQVjfO', '演示 Reviewer', 'active');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'OWNER'
WHERE u.id = 1001 AND u.username = 'owner_demo';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'LABELER'
WHERE u.id = 1002 AND u.username = 'labeler_demo';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'REVIEWER'
WHERE u.id = 1003 AND u.username = 'reviewer_demo';
