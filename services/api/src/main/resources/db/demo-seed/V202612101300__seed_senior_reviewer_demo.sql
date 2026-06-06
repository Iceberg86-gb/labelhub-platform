-- Demo senior reviewer account for local/demo review flow. Password: demo1234.
INSERT IGNORE INTO users (id, username, password_hash, display_name, status) VALUES
    (1004, 'senior_reviewer_demo', '$2a$10$SIdRICWTEbYa5zL85HSD0.4vTN4AlFCGLWi0FTNYwC29.kZcfHc0a', '演示 Senior Reviewer', 'active');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'SENIOR_REVIEWER'
WHERE u.id = 1004 AND u.username = 'senior_reviewer_demo';
