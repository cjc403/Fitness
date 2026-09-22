-- Unified commercial model for products, paid orders, member entitlements and access control.
CREATE TABLE membership_product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    validity_days INTEGER NOT NULL,
    total_uses INTEGER,
    access_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    price DECIMAL(12, 2) NOT NULL,
    venue_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_commercial_product_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_commercial_product_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT uk_commercial_product_name UNIQUE (venue_id, name),
    CONSTRAINT ck_commercial_product_price CHECK (price >= 0),
    CONSTRAINT ck_commercial_product_validity CHECK (validity_days > 0)
);

CREATE TABLE sales_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    order_type VARCHAR(20) NOT NULL,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    original_amount DECIMAL(12, 2) NOT NULL,
    paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    refunded_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(30),
    status VARCHAR(30) NOT NULL,
    remark VARCHAR(500),
    operator_id BIGINT,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_order_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_sales_order_product FOREIGN KEY (product_id) REFERENCES membership_product(id),
    CONSTRAINT fk_sales_order_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_sales_order_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
);

CREATE TABLE member_entitlement (
    id BIGSERIAL PRIMARY KEY,
    entitlement_no VARCHAR(50) NOT NULL UNIQUE,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    source_order_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    total_uses INTEGER,
    remaining_uses INTEGER,
    status VARCHAR(30) NOT NULL,
    frozen_at TIMESTAMP,
    accumulated_frozen_days INTEGER NOT NULL DEFAULT 0,
    transferred_from_member_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entitlement_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_entitlement_product FOREIGN KEY (product_id) REFERENCES membership_product(id),
    CONSTRAINT fk_entitlement_order FOREIGN KEY (source_order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_entitlement_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_entitlement_transfer_member FOREIGN KEY (transferred_from_member_id) REFERENCES member(id),
    CONSTRAINT ck_entitlement_dates CHECK (valid_until >= valid_from)
);

CREATE TABLE entitlement_operation (
    id BIGSERIAL PRIMARY KEY,
    entitlement_id BIGINT NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    before_status VARCHAR(30),
    after_status VARCHAR(30),
    amount DECIMAL(12, 2),
    note VARCHAR(500),
    operator_id BIGINT,
    operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entitlement_operation_entitlement FOREIGN KEY (entitlement_id) REFERENCES member_entitlement(id),
    CONSTRAINT fk_entitlement_operation_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
);

CREATE TABLE payment_transaction (
    id BIGSERIAL PRIMARY KEY,
    transaction_no VARCHAR(50) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    external_reference VARCHAR(100),
    operator_id BIGINT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_payment_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);

CREATE TABLE business_document (
    id BIGSERIAL PRIMARY KEY,
    document_no VARCHAR(50) NOT NULL UNIQUE,
    document_type VARCHAR(20) NOT NULL,
    order_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    tax_no VARCHAR(50),
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_document_order FOREIGN KEY (order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_business_document_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_business_document_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

ALTER TABLE member ADD COLUMN IF NOT EXISTS access_token VARCHAR(64);
UPDATE member SET access_token = md5(random()::text || id::text || clock_timestamp()::text)
WHERE access_token IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_member_access_token ON member(access_token);

CREATE TABLE access_record (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT,
    entitlement_id BIGINT,
    venue_id BIGINT NOT NULL,
    access_token_masked VARCHAR(20),
    direction VARCHAR(10) NOT NULL,
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(200),
    operator_id BIGINT,
    scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_access_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_access_entitlement FOREIGN KEY (entitlement_id) REFERENCES member_entitlement(id),
    CONSTRAINT fk_access_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_access_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
);

ALTER TABLE booking
    ADD COLUMN IF NOT EXISTS waitlist_position INTEGER,
    ADD COLUMN IF NOT EXISTS leave_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS no_show_penalty_points INTEGER NOT NULL DEFAULT 0;

INSERT INTO permission (name, code, type, description) VALUES
    ('查看会籍商品', 'PRODUCT_READ', 'API', '查看会员卡与课包商品'),
    ('维护会籍商品', 'PRODUCT_WRITE', 'API', '创建和上下架会员卡与课包'),
    ('查看销售订单', 'SALE_READ', 'API', '查看开卡续费订单与支付流水'),
    ('办理销售订单', 'SALE_WRITE', 'API', '开卡、续费及退款'),
    ('查看会员权益', 'MEMBERSHIP_READ', 'API', '查看会员卡和课包权益'),
    ('维护会员权益', 'MEMBERSHIP_WRITE', 'API', '冻结、解冻、转卡及次数核销'),
    ('查看财务记录', 'FINANCE_READ', 'API', '查看支付、合同、收据和发票'),
    ('办理入场核销', 'ACCESS_WRITE', 'API', '二维码签到和入场核销'),
    ('查看教练业绩', 'COACH_PERFORMANCE_READ', 'API', '查看课时、收入与提成统计')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'SUPER_ADMIN' AND p.code IN (
    'PRODUCT_READ','PRODUCT_WRITE','SALE_READ','SALE_WRITE','MEMBERSHIP_READ',
    'MEMBERSHIP_WRITE','FINANCE_READ','ACCESS_WRITE','COACH_PERFORMANCE_READ')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'MANAGER' AND p.code IN (
    'PRODUCT_READ','PRODUCT_WRITE','SALE_READ','SALE_WRITE','MEMBERSHIP_READ',
    'MEMBERSHIP_WRITE','FINANCE_READ','ACCESS_WRITE','COACH_PERFORMANCE_READ')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'RECEPTION' AND p.code IN (
    'PRODUCT_READ','SALE_READ','SALE_WRITE','MEMBERSHIP_READ','MEMBERSHIP_WRITE',
    'FINANCE_READ','ACCESS_WRITE')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.code = 'COACH' AND p.code = 'COACH_PERFORMANCE_READ'
ON CONFLICT DO NOTHING;

CREATE INDEX idx_product_venue_status ON membership_product(venue_id, status);
CREATE INDEX idx_sales_order_venue_created ON sales_order(venue_id, created_at DESC);
CREATE INDEX idx_sales_order_member ON sales_order(member_id);
CREATE INDEX idx_entitlement_member_status ON member_entitlement(member_id, status);
CREATE INDEX idx_entitlement_venue_expiry ON member_entitlement(venue_id, valid_until);
CREATE INDEX idx_payment_order ON payment_transaction(order_id);
CREATE INDEX idx_document_order ON business_document(order_id);
CREATE INDEX idx_access_venue_time ON access_record(venue_id, scanned_at DESC);
CREATE INDEX idx_booking_waitlist ON booking(schedule_id, status, waitlist_position);

