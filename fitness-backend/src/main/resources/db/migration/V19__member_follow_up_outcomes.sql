-- Extend existing follow-up records without altering historic business actions.
ALTER TABLE member_follow_up ADD COLUMN outcome VARCHAR(20) NOT NULL DEFAULT 'NOT_RECORDED';
ALTER TABLE member_follow_up ADD COLUMN request_key VARCHAR(64);
ALTER TABLE member_follow_up ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE member_follow_up ADD CONSTRAINT ck_follow_outcome CHECK
    (outcome IN ('NOT_RECORDED', 'NO_CONTACT', 'VISIT_BOOKED', 'RENEWAL_PENDING', 'DECLINED', 'OTHER'));
ALTER TABLE member_follow_up ADD CONSTRAINT uk_follow_member_staff_request UNIQUE (member_id, staff_user_id, request_key);
