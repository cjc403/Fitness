-- 2026-09-29 推荐 6：消息投递记录（短信/邮件通道抽象）
--   项目此前只有站内通知。本迁移只记录"发过什么、走了哪个通道、结果如何"，
--   不接真实供应商：provider=MOCK 表示模拟发送（不会真的发短信/邮件）。
CREATE TABLE message_delivery (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(20) NOT NULL,              -- IN_APP 站内 | SMS 短信 | EMAIL 邮件
    category VARCHAR(30) NOT NULL,             -- 复用站内通知的分类：BOOKING / REFUND / MEMBERSHIP ...
    recipient_masked VARCHAR(120) NOT NULL,    -- 收件人脱敏展示（138****5678 / a***@qq.com）
    recipient_user_id BIGINT REFERENCES sys_user(id),
    member_id BIGINT REFERENCES member(id),
    venue_id BIGINT REFERENCES venue(id),
    subject VARCHAR(120),
    content VARCHAR(500),
    status VARCHAR(20) NOT NULL,               -- SENT | FAILED | SKIPPED
    provider VARCHAR(40),                      -- MOCK / 未来的真实供应商
    provider_message_id VARCHAR(80),
    error VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_delivery_created ON message_delivery(id DESC);
CREATE INDEX idx_message_delivery_member ON message_delivery(member_id, id DESC);
CREATE INDEX idx_message_delivery_channel ON message_delivery(channel, status);
