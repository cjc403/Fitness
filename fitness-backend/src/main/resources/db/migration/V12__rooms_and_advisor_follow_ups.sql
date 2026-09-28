-- 2026-09-28 新功能：
--   1) 教室/场地资源：排课可以指定教室，并支持同一时段同一教室的占用冲突校验
--   2) 会籍跟进记录：让 member.sales_user_id / coach_id 这两个既有字段真正用起来，
--      支撑"会籍顾问工作台：我的会员 + 跟进 + 业绩"
-- 说明：member.sales_user_id 与 member.coach_id 在 V1 就已存在（含外键与索引），本次不改动它们。

-- 1) 教室/场地 -------------------------------------------------------------
CREATE TABLE room (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    name VARCHAR(60) NOT NULL,
    capacity INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,            -- ACTIVE | INACTIVE
    note VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_room_venue ON room(venue_id, status);

-- 排课可选指定教室；历史排课 room_id 为空表示"未指定教室"，不做回溯修改
ALTER TABLE course_schedule ADD COLUMN room_id BIGINT REFERENCES room(id);
CREATE INDEX idx_schedule_room_time ON course_schedule(room_id, scheduled_start_time);

-- 2) 会籍/教练跟进记录 -----------------------------------------------------
CREATE TABLE member_follow_up (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member(id),
    staff_user_id BIGINT NOT NULL REFERENCES sys_user(id),
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    follow_type VARCHAR(20) NOT NULL,       -- CALL | WECHAT | VISIT | OTHER
    content VARCHAR(500) NOT NULL,
    next_follow_at TIMESTAMP,               -- 约定下次跟进时间（可空）
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_follow_member ON member_follow_up(member_id, id DESC);
CREATE INDEX idx_follow_staff ON member_follow_up(staff_user_id, id DESC);
CREATE INDEX idx_follow_next ON member_follow_up(next_follow_at);
