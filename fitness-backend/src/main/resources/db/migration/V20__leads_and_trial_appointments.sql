-- Independent consultation records. Existing member/order history is never rewritten.
CREATE TABLE sales_lead (
    id BIGSERIAL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
    name VARCHAR(50) NOT NULL, phone VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL CHECK(source IN ('WALK_IN','REFERRAL','DOUYIN','WECHAT','OTHER')),
    intent VARCHAR(20) NOT NULL CHECK(intent IN ('HIGH','MEDIUM','LOW')),
    owner_id BIGINT NOT NULL REFERENCES sys_user(id), venue_id BIGINT NOT NULL REFERENCES venue(id),
    stage VARCHAR(30) NOT NULL CHECK(stage IN ('CONSULTING','VISITED','TRIAL_BOOKED','TRIAL_COMPLETED','LOST','CONVERTED')),
    note VARCHAR(1000), next_follow_at TIMESTAMP, visited_at TIMESTAMP, trial_completed_at TIMESTAMP,
    converted_member_id BIGINT REFERENCES member(id), converted_order_id BIGINT UNIQUE REFERENCES sales_order(id), converted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_lead_venue_phone UNIQUE(venue_id,phone),
    CONSTRAINT ck_lead_conversion CHECK ((stage='CONVERTED' AND converted_member_id IS NOT NULL AND converted_order_id IS NOT NULL AND converted_at IS NOT NULL)
        OR (stage<>'CONVERTED' AND converted_member_id IS NULL AND converted_order_id IS NULL AND converted_at IS NULL))
);
CREATE INDEX ix_lead_scope ON sales_lead(venue_id,owner_id,id);
CREATE TABLE lead_event (
    id BIGSERIAL PRIMARY KEY, lead_id BIGINT NOT NULL REFERENCES sales_lead(id), actor_id BIGINT NOT NULL REFERENCES sys_user(id),
    actor_name VARCHAR(50) NOT NULL, type VARCHAR(30) NOT NULL CHECK(type IN ('CREATED','PROFILE','FOLLOW_UP','TRIAL_BOOKED','TRIAL_RESULT','CONVERTED')),
    content VARCHAR(1100) NOT NULL, outcome VARCHAR(30), next_follow_at TIMESTAMP,
    request_key VARCHAR(100) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_lead_event_actor_key UNIQUE(actor_id,request_key)
);
CREATE INDEX ix_lead_event_history ON lead_event(lead_id,id);
CREATE TABLE lead_trial (
    id BIGSERIAL PRIMARY KEY, lead_id BIGINT NOT NULL REFERENCES sales_lead(id), venue_id BIGINT NOT NULL REFERENCES venue(id),
    host_id BIGINT NOT NULL REFERENCES sys_user(id), host_name VARCHAR(50) NOT NULL,
    starts_at TIMESTAMP NOT NULL, ends_at TIMESTAMP NOT NULL, content VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK(status IN ('BOOKED','ATTENDED','NO_SHOW','CANCELLED')),
    result_note VARCHAR(1000), recorded_at TIMESTAMP, created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_lead_trial_duration CHECK(ends_at-starts_at BETWEEN INTERVAL '15 minutes' AND INTERVAL '180 minutes'),
    CONSTRAINT ck_lead_trial_result CHECK((status='BOOKED' AND recorded_at IS NULL) OR (status<>'BOOKED' AND recorded_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_lead_trial_pending ON lead_trial(lead_id) WHERE status='BOOKED';
CREATE INDEX ix_lead_trial_host_time ON lead_trial(venue_id,host_id,starts_at,ends_at) WHERE status='BOOKED';
