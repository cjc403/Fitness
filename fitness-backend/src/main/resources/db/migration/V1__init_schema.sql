-- 健身房管理系统数据库初始化脚本

-- 场馆表
CREATE TABLE venue (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE,
    parent_venue_id BIGINT,
    address VARCHAR(200),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_venue_parent FOREIGN KEY (parent_venue_id) REFERENCES venue(id)
);

-- 角色表
CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 权限表
CREATE TABLE permission (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(50),
    parent_id BIGINT,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id) REFERENCES permission(id)
);

-- 角色权限关联表
CREATE TABLE role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);

-- 系统用户表 (员工/教练/经理等)
CREATE TABLE sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    real_name VARCHAR(50),
    role_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    photo_url VARCHAR(255),
    is_coach BOOLEAN DEFAULT FALSE,
    coach_introduction TEXT,
    position VARCHAR(50),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES role(id),
    CONSTRAINT fk_user_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_user_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 会员表
CREATE TABLE member (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE,
    member_no VARCHAR(50) NOT NULL UNIQUE,
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100),
    id_card VARCHAR(18),
    gender VARCHAR(10),
    birthday DATE,
    address VARCHAR(200),
    photo_url VARCHAR(255),
    sales_user_id BIGINT,
    coach_id BIGINT,
    venue_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    blacklist_flag BOOLEAN DEFAULT FALSE,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_member_sales FOREIGN KEY (sales_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_member_coach FOREIGN KEY (coach_id) REFERENCES sys_user(id),
    CONSTRAINT fk_member_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_member_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 会员变更历史表
CREATE TABLE member_history (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    reason VARCHAR(500),
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_history_changed_by FOREIGN KEY (changed_by) REFERENCES sys_user(id)
);

-- 会员黑名单表
CREATE TABLE member_blacklist (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    venue_id BIGINT,
    reason VARCHAR(500) NOT NULL,
    added_by BIGINT NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_blacklist_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_blacklist_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_blacklist_added_by FOREIGN KEY (added_by) REFERENCES sys_user(id)
);

-- 会员积分记录表
CREATE TABLE member_points (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    points INTEGER NOT NULL,
    description VARCHAR(200),
    related_id BIGINT,
    related_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_points_member FOREIGN KEY (member_id) REFERENCES member(id)
);

-- 卡类型表
CREATE TABLE card_type (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    validity_days INTEGER NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    description TEXT,
    venue_id BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_type_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
);

-- 套餐类型表
CREATE TABLE package_type (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    validity_days INTEGER NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    included_group_course_count INTEGER DEFAULT 0,
    included_regular_course_count INTEGER DEFAULT 0,
    included_private_course_count INTEGER DEFAULT 0,
    description TEXT,
    venue_id BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_package_type_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
);

-- 会员卡表
CREATE TABLE member_card (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    card_type_id BIGINT NOT NULL,
    card_no VARCHAR(50) UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    venue_id BIGINT NOT NULL,
    created_by BIGINT,
    CONSTRAINT fk_member_card_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_member_card_type FOREIGN KEY (card_type_id) REFERENCES card_type(id),
    CONSTRAINT fk_member_card_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_member_card_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 会员套餐表
CREATE TABLE member_package (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    package_type_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    remaining_group_course INTEGER DEFAULT 0,
    remaining_regular_course INTEGER DEFAULT 0,
    remaining_private_course INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    venue_id BIGINT NOT NULL,
    created_by BIGINT,
    CONSTRAINT fk_member_package_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_member_package_type FOREIGN KEY (package_type_id) REFERENCES package_type(id),
    CONSTRAINT fk_member_package_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_member_package_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 会员服务暂停表
CREATE TABLE member_pause (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    pause_start DATE NOT NULL,
    pause_end DATE,
    reason VARCHAR(500),
    approved_by BIGINT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pause_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_pause_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user(id)
);

-- 合同模板表
CREATE TABLE contract_template (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content_template TEXT NOT NULL,
    venue_id BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_template_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_template_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 合同表
CREATE TABLE contract (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    contract_template_id BIGINT,
    contract_no VARCHAR(50) UNIQUE,
    contract_data JSONB,
    deposit_amount DECIMAL(10, 2) DEFAULT 0,
    total_amount DECIMAL(10, 2) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) DEFAULT 'DRAFT',
    signed_at TIMESTAMP,
    approved_by BIGINT,
    approved_at TIMESTAMP,
    venue_id BIGINT NOT NULL,
    file_url VARCHAR(255),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_contract_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_contract_template FOREIGN KEY (contract_template_id) REFERENCES contract_template(id),
    CONSTRAINT fk_contract_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_contract_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user(id),
    CONSTRAINT fk_contract_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 定金表
CREATE TABLE deposit (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    contract_id BIGINT,
    amount DECIMAL(10, 2) NOT NULL,
    used_amount DECIMAL(10, 2) DEFAULT 0,
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'AVAILABLE',
    created_by BIGINT,
    CONSTRAINT fk_deposit_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_deposit_contract FOREIGN KEY (contract_id) REFERENCES contract(id),
    CONSTRAINT fk_deposit_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 课程表 (课程定义)
CREATE TABLE course (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    duration_minutes INTEGER,
    intensity VARCHAR(20),
    max_capacity INTEGER,
    fee DECIMAL(10, 2) DEFAULT 0,
    allowed_roles TEXT,
    allowed_permissions TEXT,
    venue_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_course_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 排课表 (具体课程安排)
CREATE TABLE course_schedule (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    coach_id BIGINT,
    venue_id BIGINT NOT NULL,
    scheduled_start_time TIMESTAMP NOT NULL,
    scheduled_end_time TIMESTAMP NOT NULL,
    max_students INTEGER,
    enrolled_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'SCHEDULED',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_schedule_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_schedule_coach FOREIGN KEY (coach_id) REFERENCES sys_user(id),
    CONSTRAINT fk_schedule_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_schedule_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 预约表
CREATE TABLE booking (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'BOOKED',
    booked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancelled_by BIGINT,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(200),
    expiry_time TIMESTAMP,
    CONSTRAINT fk_booking_schedule FOREIGN KEY (schedule_id) REFERENCES course_schedule(id),
    CONSTRAINT fk_booking_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_booking_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES sys_user(id)
);

-- 出勤记录表
CREATE TABLE attendance (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    late_flag BOOLEAN DEFAULT FALSE,
    late_minutes INTEGER,
    remarks VARCHAR(500),
    checked_in_by BIGINT,
    checked_in_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
    CONSTRAINT fk_attendance_checked_by FOREIGN KEY (checked_in_by) REFERENCES sys_user(id)
);

-- 请假申请表
CREATE TABLE leave_request (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    coach_id BIGINT,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    status VARCHAR(20) DEFAULT 'PENDING',
    approved_by BIGINT,
    approved_at TIMESTAMP,
    rejected_reason VARCHAR(200),
    CONSTRAINT fk_leave_booking FOREIGN KEY (booking_id) REFERENCES booking(id),
    CONSTRAINT fk_leave_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_leave_coach FOREIGN KEY (coach_id) REFERENCES sys_user(id),
    CONSTRAINT fk_leave_approved_by FOREIGN KEY (approved_by) REFERENCES sys_user(id)
);

-- 储物柜表
CREATE TABLE locker (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    locker_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'AVAILABLE',
    current_member_id BIGINT,
    rent_start_date DATE,
    rent_end_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_locker_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_locker_member FOREIGN KEY (current_member_id) REFERENCES member(id),
    UNIQUE(venue_id, locker_number)
);

-- 储物柜租赁记录表
CREATE TABLE locker_rental (
    id BIGSERIAL PRIMARY KEY,
    locker_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    fee DECIMAL(10, 2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rental_locker FOREIGN KEY (locker_id) REFERENCES locker(id),
    CONSTRAINT fk_rental_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_rental_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 营销内容表
CREATE TABLE marketing_content (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content_body TEXT,
    image_url VARCHAR(255),
    content_type VARCHAR(50),
    associated_course_ids BIGINT[],
    associated_activity_ids BIGINT[],
    published_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'DRAFT',
    venue_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_marketing_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_marketing_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 会员画像表
CREATE TABLE member_profile (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    attribute_name VARCHAR(100) NOT NULL,
    attribute_value TEXT,
    attribute_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_member FOREIGN KEY (member_id) REFERENCES member(id)
);

-- 业务规则表
CREATE TABLE business_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_type VARCHAR(50) NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    rule_parameters JSONB,
    venue_id BIGINT,
    effective_from DATE,
    effective_to DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rule_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
    CONSTRAINT fk_rule_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
);

-- 审计日志表
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    entity_name VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    operation VARCHAR(20) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    CONSTRAINT fk_audit_changed_by FOREIGN KEY (changed_by) REFERENCES sys_user(id)
);

-- 创建索引
CREATE INDEX idx_venue_parent ON venue(parent_venue_id);
CREATE INDEX idx_user_role ON sys_user(role_id);
CREATE INDEX idx_user_venue ON sys_user(venue_id);
CREATE INDEX idx_member_venue ON member(venue_id);
CREATE INDEX idx_member_coach ON member(coach_id);
CREATE INDEX idx_member_sales ON member(sales_user_id);
CREATE INDEX idx_member_history_member ON member_history(member_id);
CREATE INDEX idx_booking_schedule ON booking(schedule_id);
CREATE INDEX idx_booking_member ON booking(member_id);
CREATE INDEX idx_booking_status ON booking(status);
CREATE INDEX idx_schedule_course ON course_schedule(course_id);
CREATE INDEX idx_schedule_coach ON course_schedule(coach_id);
CREATE INDEX idx_schedule_venue ON course_schedule(venue_id);
CREATE INDEX idx_schedule_time ON course_schedule(scheduled_start_time);
CREATE INDEX idx_audit_entity ON audit_log(entity_name, entity_id);
CREATE INDEX idx_audit_changed_by ON audit_log(changed_by);
CREATE INDEX idx_audit_changed_at ON audit_log(changed_at);

-- 插入初始数据
INSERT INTO role (name, code, description) VALUES
    ('超级管理员', 'SUPER_ADMIN', '系统超级管理员'),
    ('场馆经理', 'MANAGER', '场馆经理'),
    ('销售人员', 'SALES', '销售人员'),
    ('前台员工', 'RECEPTION', '前台员工'),
    ('教练', 'COACH', '教练'),
    ('会员', 'MEMBER', '会员');

-- 插入默认场馆
INSERT INTO venue (name, code, status) VALUES
    ('总部', 'HEADQUARTERS', 'ACTIVE');
