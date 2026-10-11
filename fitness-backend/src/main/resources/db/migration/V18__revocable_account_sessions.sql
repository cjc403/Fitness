-- Account safety changes revoke both access and refresh tokens. Existing
-- signed, unversioned tokens have version zero until the next revocation.
ALTER TABLE sys_user ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sys_user ADD CONSTRAINT ck_sys_user_session_version CHECK (session_version >= 0);

-- Logout revokes one login/device, while password/status/scope changes revoke
-- all account sessions. Persist tombstones across application restarts.
CREATE TABLE revoked_auth_session (
    session_id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sys_user(id) ON DELETE CASCADE,
    revoked_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_revoked_auth_session_expiry ON revoked_auth_session(expires_at);
