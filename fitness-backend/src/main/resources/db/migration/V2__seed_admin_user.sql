-- 初始化开发环境管理员账号。
-- 用户名：admin，密码：password。部署前必须修改密码。
INSERT INTO sys_user (username, password_hash, real_name, role_id, venue_id, status)
SELECT
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '系统管理员',
    r.id,
    v.id,
    'ACTIVE'
FROM role r
         CROSS JOIN venue v
WHERE r.code = 'SUPER_ADMIN'
  AND v.code = 'HEADQUARTERS'
ON CONFLICT (username) DO NOTHING;
