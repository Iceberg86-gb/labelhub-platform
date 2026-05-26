UPDATE users
SET password_hash = REPLACE(password_hash, '$2y$', '$2a$')
WHERE username IN ('owner_demo', 'labeler_demo', 'reviewer_demo')
  AND password_hash LIKE '$2y$%';
