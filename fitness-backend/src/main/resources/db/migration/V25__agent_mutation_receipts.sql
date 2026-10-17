CREATE TABLE agent_task_receipt (
 id VARCHAR(36) PRIMARY KEY,
 task_id VARCHAR(36) NOT NULL REFERENCES agent_task(id),
 task_version BIGINT NOT NULL,
 document_nonce VARCHAR(128) NOT NULL,
 method VARCHAR(10) NOT NULL,
 path VARCHAR(500) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 request_summary TEXT NOT NULL,
 before_json TEXT NOT NULL,
 result_json TEXT,
 status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP NOT NULL,
 expires_at TIMESTAMP NOT NULL
);
CREATE INDEX ix_agent_receipt_task ON agent_task_receipt(task_id,created_at);
