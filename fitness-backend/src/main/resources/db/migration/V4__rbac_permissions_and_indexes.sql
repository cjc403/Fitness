-- Persist the application RBAC matrix for future dynamic role management.
INSERT INTO permission (name, code, type, description) VALUES
    ('查看经营看板', 'DASHBOARD_READ', 'API', '查看经营指标与趋势'),
    ('查看会员', 'MEMBER_READ', 'API', '查看会员资料'),
    ('维护会员', 'MEMBER_WRITE', 'API', '新增、编辑、暂停和恢复会员'),
    ('查看课程', 'COURSE_READ', 'API', '查看课程模板与排课'),
    ('维护课程', 'COURSE_WRITE', 'API', '新增和编辑课程模板'),
    ('维护排课', 'SCHEDULE_WRITE', 'API', '创建课程排期'),
    ('查看预约', 'BOOKING_READ', 'API', '查看预约记录'),
    ('维护预约', 'BOOKING_WRITE', 'API', '代会员预约与取消'),
    ('到店签到', 'CHECKIN_WRITE', 'API', '为预约会员签到'),
    ('查看场馆', 'VENUE_READ', 'API', '查看场馆资料'),
    ('创建场馆', 'VENUE_CREATE', 'API', '创建连锁分店'),
    ('维护场馆', 'VENUE_WRITE', 'API', '编辑场馆资料'),
    ('切换场馆', 'VENUE_SWITCH', 'API', '切换总部管理视角'),
    ('使用智能客服', 'ASSISTANT_USE', 'API', '使用智能问答功能'),
    ('会员自助服务', 'MEMBER_SELF_SERVICE', 'API', '会员查看和操作本人预约'),
    ('教练工作台', 'COACH_WORKBENCH', 'API', '教练查看本人排课与学员')
ON CONFLICT (code) DO NOTHING;

UPDATE role SET name = '总部管理员' WHERE code = 'SUPER_ADMIN';
UPDATE role SET name = '店长' WHERE code = 'MANAGER';
UPDATE role SET name = '前台' WHERE code = 'RECEPTION';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = ANY (ARRAY[
    'DASHBOARD_READ','MEMBER_READ','MEMBER_WRITE','COURSE_READ','COURSE_WRITE',
    'SCHEDULE_WRITE','BOOKING_READ','BOOKING_WRITE','CHECKIN_WRITE',
    'VENUE_READ','VENUE_WRITE','ASSISTANT_USE'])
WHERE r.code = 'MANAGER' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = ANY (ARRAY[
    'COURSE_READ','BOOKING_READ','CHECKIN_WRITE','COACH_WORKBENCH','ASSISTANT_USE'])
WHERE r.code = 'COACH' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = ANY (ARRAY[
    'MEMBER_READ','MEMBER_WRITE','COURSE_READ','BOOKING_READ','BOOKING_WRITE',
    'CHECKIN_WRITE','ASSISTANT_USE'])
WHERE r.code = 'RECEPTION' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = ANY (ARRAY[
    'COURSE_READ','MEMBER_SELF_SERVICE','ASSISTANT_USE'])
WHERE r.code = 'MEMBER' ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = ANY (ARRAY[
    'MEMBER_READ','MEMBER_WRITE','ASSISTANT_USE'])
WHERE r.code = 'SALES' ON CONFLICT DO NOTHING;

-- Composite indexes for the high-frequency tenant and date filters.
CREATE INDEX IF NOT EXISTS idx_course_venue_status ON course(venue_id, status);
CREATE INDEX IF NOT EXISTS idx_schedule_venue_status_time ON course_schedule(venue_id, status, scheduled_start_time);
CREATE INDEX IF NOT EXISTS idx_schedule_coach_venue_time ON course_schedule(coach_id, venue_id, scheduled_start_time);
CREATE INDEX IF NOT EXISTS idx_booking_member_status ON booking(member_id, status);
