-- 2026-09-29 推荐 5：跨门店通用（连锁）
--   ① 商品可声明"适用门店"：空表示仅限商品所属门店（保持既有行为，向后兼容）
--   ② 权益记录开卡时的适用门店快照（逗号分隔 venue id；空 = 仅发卡门店）
--   本迁移只做加法；退款与价值账仍按发卡门店结算（见服务层注释说明的边界）。
CREATE TABLE product_venue (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES membership_product(id),
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    CONSTRAINT uk_product_venue UNIQUE (product_id, venue_id)
);
CREATE INDEX idx_product_venue_product ON product_venue(product_id);

-- 权益的适用门店快照：开卡时确定，之后商品改了也不影响已售出的卡
ALTER TABLE member_entitlement ADD COLUMN venue_scope VARCHAR(200);
