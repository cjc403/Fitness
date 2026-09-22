ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

INSERT INTO permission (name, code, type, description)
VALUES ('管理员工账号', 'ACCOUNT_MANAGE', 'API', '创建、启停员工账号及重置密码')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code IN ('SUPER_ADMIN', 'MANAGER') AND p.code = 'ACCOUNT_MANAGE'
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_user_status ON sys_user(status);
CREATE INDEX IF NOT EXISTS idx_user_last_login ON sys_user(last_login_at);
