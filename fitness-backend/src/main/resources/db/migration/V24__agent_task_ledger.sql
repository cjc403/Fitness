-- Durable task progress only. Existing domain tables and V1-V23 stay byte-identical.
CREATE TABLE agent_task (
 id VARCHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES sys_user(id),
 venue_id BIGINT NOT NULL REFERENCES venue(id),
 role VARCHAR(30) NOT NULL,
 session_hash VARCHAR(64) NOT NULL,
 document_nonce VARCHAR(128) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 goal VARCHAR(2000) NOT NULL,
 status VARCHAR(30) NOT NULL,
 plan_json TEXT NOT NULL,
 checkpoint_json TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL
);
CREATE INDEX ix_agent_task_owner ON agent_task(user_id,session_hash,updated_at DESC);
CREATE TABLE agent_task_step (
 id VARCHAR(36) PRIMARY KEY,
 task_id VARCHAR(36) NOT NULL REFERENCES agent_task(id),
 task_version BIGINT NOT NULL,
 kind VARCHAR(40) NOT NULL,
 detail_json TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL
);
CREATE INDEX ix_agent_task_step ON agent_task_step(task_id,created_at);
