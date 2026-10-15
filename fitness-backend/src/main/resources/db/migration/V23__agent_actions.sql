-- Confirmed Agent actions are bound to one actor, venue and login session.
CREATE TABLE agent_action (
 id VARCHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES sys_user(id),
 venue_id BIGINT NOT NULL REFERENCES venue(id),
 role VARCHAR(30) NOT NULL,
 session_hash VARCHAR(64) NOT NULL,
 tool VARCHAR(60) NOT NULL,
 arguments_json TEXT NOT NULL,
 business_snapshot TEXT NOT NULL,
 summary VARCHAR(2000) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','CANCELLED','EXPIRED','FAILED')),
 created_at TIMESTAMP NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 result_json TEXT,
 error VARCHAR(500),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_agent_action_session ON agent_action(user_id,session_hash,created_at DESC);
