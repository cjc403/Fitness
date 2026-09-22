package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门禁二维码的短时效一次性令牌。
 *
 * <p>此前会员二维码直接用 {@code member.access_token}（一次生成、永不轮换），一张截图可以无限次入场。
 * 现在每次打开会员端都会签发一个 60 秒有效、用后作废的令牌；扫码时校验时效并一次性消费。</p>
 */
@Entity
@Table(name = "access_grant")
@Getter
@Setter
@NoArgsConstructor
public class AccessGrantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    @Column(nullable = false, unique = true, length = 80)
    private String token;
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    @Column(name = "used_by")
    private Long usedBy;
    @Column(name = "used_venue_id")
    private Long usedVenueId;
}
