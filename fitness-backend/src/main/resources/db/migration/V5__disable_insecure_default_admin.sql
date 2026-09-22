-- V2 existed for the classroom demo. Production must explicitly supply APP_ADMIN_PASSWORD.
UPDATE sys_user
SET status = 'INACTIVE'
WHERE username = 'admin'
  AND password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy';
