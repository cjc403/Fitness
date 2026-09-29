-- 2026-09-28 推荐 1：会员体测与训练档案
--   记录体重/体脂/肌肉量/围度/静息心率与训练目标，用于曲线与"私教课效果"对比。
-- 说明：全部为附加变更；既有会员数据不受影响（训练目标为空表示未设置）。

-- 训练目标（减脂/增肌/体态/体能）；空 = 未设置
ALTER TABLE member ADD COLUMN training_goal VARCHAR(20);
ALTER TABLE member ADD COLUMN training_goal_note VARCHAR(200);

CREATE TABLE body_measurement (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member(id),
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    measured_on DATE NOT NULL,
    weight_kg NUMERIC(5,2),
    body_fat_rate NUMERIC(5,2),
    muscle_kg NUMERIC(5,2),
    chest_cm NUMERIC(5,2),
    waist_cm NUMERIC(5,2),
    hip_cm NUMERIC(5,2),
    resting_heart_rate INT,
    note VARCHAR(300),
    recorded_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP,
    -- 同一会员同一天只保留一条：重复提交视为修正当天记录，曲线不会出现重复点
    CONSTRAINT uk_body_measure_member_day UNIQUE (member_id, measured_on)
);
CREATE INDEX idx_body_measure_member ON body_measurement(member_id, measured_on DESC);
CREATE INDEX idx_body_measure_venue ON body_measurement(venue_id, measured_on DESC);
