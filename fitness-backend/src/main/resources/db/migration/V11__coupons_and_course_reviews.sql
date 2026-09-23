-- 2026-09-23 新功能：
--   1) 营销：优惠券模板 / 会员券，以及销售订单上的用券与折扣金额
--   2) 课程评价（会员对本人已签到的课评价一次）
-- 说明：折扣与券面金额一律用「分」存整数，与既有资金口径（Money.cents）一致。

-- 1) 券模板 ---------------------------------------------------------------
CREATE TABLE coupon_template (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    name VARCHAR(100) NOT NULL,
    coupon_type VARCHAR(20) NOT NULL,               -- FULL_REDUCTION 满减 | DISCOUNT 折扣
    threshold_cents BIGINT NOT NULL DEFAULT 0,      -- 满多少分可用；0 表示无门槛
    discount_cents BIGINT NOT NULL DEFAULT 0,       -- 满减：减多少分
    discount_rate NUMERIC(5,4),                     -- 折扣：0.9000 = 九折
    total_quantity INT NOT NULL DEFAULT 0,          -- 0 表示不限量
    issued_quantity INT NOT NULL DEFAULT 0,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    status VARCHAR(20) NOT NULL,                    -- ACTIVE | INACTIVE
    note VARCHAR(200),
    created_by BIGINT REFERENCES sys_user(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_coupon_template_venue ON coupon_template(venue_id, status);

-- 2) 会员持有的券 ---------------------------------------------------------
CREATE TABLE member_coupon (
    id BIGSERIAL PRIMARY KEY,
    coupon_template_id BIGINT NOT NULL REFERENCES coupon_template(id),
    member_id BIGINT NOT NULL REFERENCES member(id),
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    code VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,                    -- UNUSED | USED | EXPIRED
    issued_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    used_order_id BIGINT REFERENCES sales_order(id),
    discount_cents BIGINT NOT NULL DEFAULT 0,       -- 实际抵扣了多少分
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL
);
CREATE INDEX idx_member_coupon_member ON member_coupon(member_id, status);
CREATE INDEX idx_member_coupon_venue ON member_coupon(venue_id, status);

-- 3) 销售订单记录用券与折扣 -----------------------------------------------
--    original_amount 保持商品标价，paid_amount 记折后实收；
--    权益价值账按 paid_amount 入账，因此退款自然按"实付"计算，不会多退。
ALTER TABLE sales_order ADD COLUMN member_coupon_id BIGINT REFERENCES member_coupon(id);
ALTER TABLE sales_order ADD COLUMN discount_cents BIGINT NOT NULL DEFAULT 0;

-- 4) 课程评价 -------------------------------------------------------------
CREATE TABLE course_review (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES booking(id),
    member_id BIGINT NOT NULL REFERENCES member(id),
    course_id BIGINT NOT NULL REFERENCES course(id),
    schedule_id BIGINT NOT NULL REFERENCES course_schedule(id),
    coach_id BIGINT REFERENCES sys_user(id),
    venue_id BIGINT NOT NULL REFERENCES venue(id),
    rating INT NOT NULL,
    tags VARCHAR(200),
    content VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_course_review_coach ON course_review(coach_id, id DESC);
CREATE INDEX idx_course_review_course ON course_review(course_id, id DESC);
CREATE INDEX idx_course_review_venue ON course_review(venue_id, id DESC);
