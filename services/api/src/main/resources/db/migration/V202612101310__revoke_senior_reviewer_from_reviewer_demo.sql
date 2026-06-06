DELETE ur FROM user_roles ur
JOIN roles r ON r.id = ur.role_id AND r.code = 'SENIOR_REVIEWER'
JOIN users u ON u.id = ur.user_id AND u.id = 1003 AND u.username = 'reviewer_demo';
