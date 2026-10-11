package com.gym.fitness.module.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Revocation tombstones contain no credentials; account changes use session_version. */
@Entity
@Table(name = "revoked_auth_session")
@Getter
@Setter
public class RevokedAuthSessionEntity {
    @Id @Column(name = "session_id", length = 36) private String sessionId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "revoked_at", nullable = false) private Instant revokedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
}
