-- Additive migration: historical bookings remain unlinked and are never charged retroactively.
ALTER TABLE member_entitlement ADD COLUMN reserved_uses INTEGER NOT NULL DEFAULT 0;
ALTER TABLE member_entitlement ADD COLUMN lesson_unit_price DECIMAL(12,2);
ALTER TABLE member_entitlement ADD CONSTRAINT ck_reserved_uses CHECK (reserved_uses >= 0 AND (remaining_uses IS NULL OR reserved_uses <= remaining_uses));
UPDATE member_entitlement e SET lesson_unit_price=ROUND(o.paid_amount / e.total_uses,2)
FROM sales_order o WHERE e.source_order_id=o.id AND e.total_uses>0;

CREATE TABLE coach_commission_rule (
 id BIGSERIAL PRIMARY KEY, venue_id BIGINT NOT NULL REFERENCES venue(id), coach_id BIGINT NOT NULL REFERENCES sys_user(id),
 rate DECIMAL(5,2) NOT NULL CHECK(rate BETWEEN 0 AND 100), updated_by BIGINT NOT NULL REFERENCES sys_user(id), updated_at TIMESTAMP NOT NULL,
 UNIQUE(venue_id,coach_id)
);
CREATE TABLE coach_settlement (
 id BIGSERIAL PRIMARY KEY, venue_id BIGINT NOT NULL REFERENCES venue(id), coach_id BIGINT NOT NULL REFERENCES sys_user(id),
 settlement_month VARCHAR(7) NOT NULL, lesson_count INTEGER NOT NULL CHECK(lesson_count>0),
 service_amount DECIMAL(12,2) NOT NULL CHECK(service_amount>=0), commission_amount DECIMAL(12,2) NOT NULL CHECK(commission_amount>=0),
 status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','APPROVED')), created_by BIGINT NOT NULL REFERENCES sys_user(id), created_at TIMESTAMP NOT NULL,
 approved_by BIGINT REFERENCES sys_user(id), approved_at TIMESTAMP, UNIQUE(venue_id,coach_id,settlement_month)
);
CREATE TABLE private_lesson (
 id BIGSERIAL PRIMARY KEY, booking_id BIGINT NOT NULL UNIQUE REFERENCES booking(id), schedule_id BIGINT NOT NULL REFERENCES course_schedule(id),
 entitlement_id BIGINT NOT NULL REFERENCES member_entitlement(id), member_id BIGINT NOT NULL REFERENCES member(id), coach_id BIGINT NOT NULL REFERENCES sys_user(id), venue_id BIGINT NOT NULL REFERENCES venue(id),
 status VARCHAR(20) NOT NULL CHECK(status IN ('RESERVED','PENDING','CONFIRMED','RELEASED','REVERSED')),
 unit_price DECIMAL(12,2) NOT NULL CHECK(unit_price>=0), commission_rate DECIMAL(5,2), commission_amount DECIMAL(12,2),
 settlement_id BIGINT REFERENCES coach_settlement(id), reserved_at TIMESTAMP NOT NULL, submitted_at TIMESTAMP, confirmed_at TIMESTAMP, released_at TIMESTAMP, reversed_at TIMESTAMP, note VARCHAR(500)
);
CREATE INDEX idx_private_lesson_member ON private_lesson(member_id,reserved_at);
CREATE INDEX idx_private_lesson_coach ON private_lesson(coach_id,confirmed_at);
CREATE INDEX idx_private_lesson_entitlement ON private_lesson(entitlement_id,status);
