package com.gym.fitness.module.notice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 站内通知。此前排课被改、商品下架、退款审批结果都只能靠会员自己发现，
 * 站内消息是解锁"能通知会员"这件事的最小闭环。
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;
    @Column(name = "member_id")
    private Long memberId;
    @Column(name = "venue_id")
    private Long venueId;
    @Column(nullable = false, length = 30)
    private String category;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(length = 1000)
    private String body;
    @Column(name = "ref_type", length = 30)
    private String refType;
    @Column(name = "ref_id")
    private Long refId;
    @Column(name = "read_at")
    private LocalDateTime readAt;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
