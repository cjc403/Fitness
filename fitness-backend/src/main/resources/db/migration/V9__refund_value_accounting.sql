-- Additive only: preserve all historic business data, credentials and V1-V8 checksums.
-- Historic monetary values remain unknown until an explicit, audited reconciliation.
CREATE TABLE entitlement_value_account (
 id BIGSERIAL PRIMARY KEY,
 entitlement_id BIGINT NOT NULL UNIQUE REFERENCES member_entitlement(id),
 member_id BIGINT NOT NULL REFERENCES member(id), venue_id BIGINT NOT NULL REFERENCES venue(id),
 verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED' CHECK (verification_status IN ('UNVERIFIED','VERIFIED')),
 product_type VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
 balance_cents BIGINT NOT NULL DEFAULT 0 CHECK (balance_cents BETWEEN 0 AND 999999999999),
 version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0), active_refund_id BIGINT,
 paused_at DATE, baseline_remaining_uses INTEGER, reconciled_by BIGINT REFERENCES sys_user(id),
 reconciled_at TIMESTAMP, reconciliation_reason VARCHAR(1000), transfer_evidence VARCHAR(1000)
);
CREATE TABLE entitlement_funding (
 id BIGSERIAL PRIMARY KEY, entitlement_id BIGINT NOT NULL REFERENCES member_entitlement(id),
 order_id BIGINT NOT NULL UNIQUE REFERENCES sales_order(id), venue_id BIGINT NOT NULL REFERENCES venue(id),
 received_cents BIGINT NOT NULL CHECK (received_cents BETWEEN 0 AND 999999999999),
 remaining_cents BIGINT NOT NULL CHECK (remaining_cents BETWEEN 0 AND received_cents),
 product_type VARCHAR(40) NOT NULL, total_units INTEGER, verified BOOLEAN NOT NULL,
 created_at TIMESTAMP NOT NULL
);
CREATE TABLE entitlement_value_event (
 id BIGSERIAL PRIMARY KEY, entitlement_id BIGINT NOT NULL REFERENCES member_entitlement(id),
 venue_id BIGINT NOT NULL REFERENCES venue(id), business_key VARCHAR(150) NOT NULL UNIQUE,
 event_type VARCHAR(40) NOT NULL, delta_cents BIGINT NOT NULL, balance_after_cents BIGINT NOT NULL,
 verified BOOLEAN NOT NULL, units_delta INTEGER, allocations_json TEXT NOT NULL, details_json TEXT NOT NULL,
 operator_id BIGINT REFERENCES sys_user(id), occurred_at TIMESTAMP NOT NULL
);
CREATE TABLE entitlement_term_period (
 id BIGSERIAL PRIMARY KEY, funding_id BIGINT NOT NULL UNIQUE REFERENCES entitlement_funding(id),
 entitlement_id BIGINT NOT NULL REFERENCES member_entitlement(id),
 total_cents BIGINT NOT NULL CHECK (total_cents BETWEEN 0 AND 999999999999),
 total_days INTEGER NOT NULL CHECK (total_days > 0),
 consumed_days INTEGER NOT NULL CHECK (consumed_days BETWEEN 0 AND total_days),
 start_date DATE NOT NULL, next_service_date DATE NOT NULL
);
CREATE TABLE refund_application (
 id BIGSERIAL PRIMARY KEY, refund_no VARCHAR(50) NOT NULL UNIQUE,
 entitlement_id BIGINT NOT NULL REFERENCES member_entitlement(id),
 active_entitlement_id BIGINT UNIQUE REFERENCES member_entitlement(id),
 member_id BIGINT NOT NULL REFERENCES member(id), venue_id BIGINT NOT NULL REFERENCES venue(id),
 status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','APPROVED','PAID','REJECTED','CANCELLED')),
 amount_cents BIGINT NOT NULL CHECK (amount_cents BETWEEN 1 AND 999999999999),
 value_version BIGINT NOT NULL, remaining_uses_snapshot INTEGER, reason VARCHAR(1000) NOT NULL,
 requested_by BIGINT NOT NULL REFERENCES sys_user(id), idempotency_key VARCHAR(100) NOT NULL,
 request_fingerprint VARCHAR(100) NOT NULL, requested_at TIMESTAMP NOT NULL,
 approved_by BIGINT REFERENCES sys_user(id), approved_at TIMESTAMP, decision_reason VARCHAR(1000),
 paid_by BIGINT REFERENCES sys_user(id), paid_at TIMESTAMP, channel VARCHAR(30),
 external_reference VARCHAR(100), payment_note VARCHAR(1000),
 UNIQUE(requested_by,idempotency_key), UNIQUE(channel,external_reference),
 CHECK ((status IN ('PENDING','APPROVED') AND active_entitlement_id IS NOT NULL AND active_entitlement_id=entitlement_id)
     OR (status IN ('PAID','REJECTED','CANCELLED') AND active_entitlement_id IS NULL))
);
ALTER TABLE entitlement_value_account ADD CONSTRAINT fk_value_active_refund
 FOREIGN KEY(active_refund_id) REFERENCES refund_application(id);
CREATE TABLE refund_allocation (
 id BIGSERIAL PRIMARY KEY, refund_id BIGINT NOT NULL REFERENCES refund_application(id),
 funding_id BIGINT NOT NULL REFERENCES entitlement_funding(id), order_id BIGINT NOT NULL REFERENCES sales_order(id),
 amount_cents BIGINT NOT NULL CHECK (amount_cents BETWEEN 1 AND 999999999999),
 payment_transaction_id BIGINT REFERENCES payment_transaction(id), UNIQUE(refund_id,funding_id)
);
CREATE TABLE refund_audit (
 id BIGSERIAL PRIMARY KEY, refund_id BIGINT NOT NULL REFERENCES refund_application(id),
 action VARCHAR(30) NOT NULL, reason VARCHAR(1000) NOT NULL,
 operator_id BIGINT NOT NULL REFERENCES sys_user(id), occurred_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_value_account_member ON entitlement_value_account(member_id);
CREATE INDEX idx_value_account_venue ON entitlement_value_account(venue_id,verification_status);
CREATE INDEX idx_funding_entitlement ON entitlement_funding(entitlement_id,order_id);
CREATE INDEX idx_value_event_entitlement ON entitlement_value_event(entitlement_id,id);
CREATE INDEX idx_value_event_venue_time ON entitlement_value_event(venue_id,occurred_at);
CREATE INDEX idx_term_entitlement ON entitlement_term_period(entitlement_id);
CREATE INDEX idx_refund_venue ON refund_application(venue_id,requested_at);
CREATE INDEX idx_refund_member ON refund_application(member_id,requested_at);
CREATE INDEX idx_refund_allocation ON refund_allocation(refund_id,order_id);
CREATE INDEX idx_refund_audit ON refund_audit(refund_id,id);

INSERT INTO entitlement_value_account
 (entitlement_id,member_id,venue_id,verification_status,product_type,balance_cents,version,paused_at,baseline_remaining_uses)
SELECT id,member_id,venue_id,'UNVERIFIED','UNKNOWN',0,0,
 CASE WHEN status='FROZEN' THEN CAST(frozen_at AS DATE) ELSE NULL END,remaining_uses
FROM member_entitlement;
