-- 2026-09-22 第二轮体检的修复：
--   1) 开卡/下单补幂等键（此前只有退款流程有幂等，收款反而更容易重复）
--   2) 门禁二维码改为短时效一次性令牌（此前用会员永久 access_token，截图可无限复用）
--   3) 站内通知表
--   4) 补几个高频外键列索引

-- 1) 销售订单幂等键 ---------------------------------------------------------
ALTER TABLE sales_order ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE sales_order ADD COLUMN request_fingerprint VARCHAR(128);
-- 同一操作者用同一个幂等键只会落一单；历史行该列为 NULL，PostgreSQL 下 NULL 互不相等，不受影响。
ALTER TABLE sales_order ADD CONSTRAINT uk_sales_order_operator_idempotency UNIQUE (operator_id, idempotency_key);

-- 2) 门禁短时效一次性令牌 ---------------------------------------------------
CREATE TABLE access_grant (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member(id),
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    token VARCHAR(80) NOT NULL UNIQUE,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    used_by BIGINT REFERENCES sys_user(id),
    used_venue_id BIGINT REFERENCES venue(id)
);
CREATE INDEX idx_access_grant_expires ON access_grant(expires_at);
CREATE INDEX idx_access_grant_member ON access_grant(member_id, id DESC);

-- 3) 站内通知 --------------------------------------------------------------
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL REFERENCES sys_user(id),
    member_id BIGINT REFERENCES member(id),
    venue_id BIGINT REFERENCES venue(id),
    category VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(1000),
    ref_type VARCHAR(30),
    ref_id BIGINT,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification(recipient_user_id, id DESC);
CREATE INDEX idx_notification_unread ON notification(recipient_user_id, read_at);

-- 4) 补齐高频外键列索引（删除守卫、来源核对、结算查询都会走这些列）---------
CREATE INDEX IF NOT EXISTS idx_sales_order_product ON sales_order(product_id);
CREATE INDEX IF NOT EXISTS idx_entitlement_product ON member_entitlement(product_id);
CREATE INDEX IF NOT EXISTS idx_entitlement_source_order ON member_entitlement(source_order_id);
CREATE INDEX IF NOT EXISTS idx_private_lesson_schedule ON private_lesson(schedule_id);
CREATE INDEX IF NOT EXISTS idx_private_lesson_venue ON private_lesson(venue_id);
CREATE INDEX IF NOT EXISTS idx_refund_entitlement ON refund_application(entitlement_id);
