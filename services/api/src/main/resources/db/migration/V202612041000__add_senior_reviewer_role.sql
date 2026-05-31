INSERT IGNORE INTO roles (code, name) VALUES ('SENIOR_REVIEWER', 'Senior Reviewer');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'SENIOR_REVIEWER'
WHERE u.id = 1003 AND u.username = 'reviewer_demo';
