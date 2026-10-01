-- 2026-10-01 跨店服务分摊（只记账，不自动打款）
--   背景：跨店通用卡由 A 店收款，会员可能在 B 店上课；A 拿了钱、B 提供了服务，
--   需要一条能把"谁该分到多少"算清楚的账，否则连锁内部无法结算。
--   本表**只做记录**：不改变任何资金流向，退款与价值账仍按发卡门店。
CREATE TABLE cross_venue_service (
    id BIGSERIAL PRIMARY KEY,
    entitlement_id BIGINT REFERENCES member_entitlement(id),
    member_id BIGINT NOT NULL REFERENCES member(id),
    issuer_venue_id BIGINT NOT NULL REFERENCES venue(id),
    serving_venue_id BIGINT NOT NULL REFERENCES venue(id),
    ref_type VARCHAR(30) NOT NULL,
    ref_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT now(),
    note VARCHAR(200),
    CONSTRAINT uk_cross_venue_ref UNIQUE (ref_type, ref_id)
);
CREATE INDEX idx_cross_venue_serving ON cross_venue_service(serving_venue_id, occurred_at);
CREATE INDEX idx_cross_venue_issuer ON cross_venue_service(issuer_venue_id, occurred_at);
