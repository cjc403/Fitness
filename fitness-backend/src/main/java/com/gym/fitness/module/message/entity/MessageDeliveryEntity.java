package com.gym.fitness.module.message.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 一次消息投递的记录（站内/短信/邮件共用一张表，便于统一审计）。 */
@Entity
@Table(name = "message_delivery")
@Getter
@Setter
@NoArgsConstructor
public class MessageDeliveryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String channel;
    @Column(nullable = false, length = 30)
    private String category;
    /** 脱敏后的收件人（绝不存完整手机号/邮箱，避免日志与页面泄露）。 */
    @Column(name = "recipient_masked", nullable = false, length = 120)
    private String recipientMasked;
    @Column(name = "recipient_user_id")
    private Long recipientUserId;
    @Column(name = "member_id")
    private Long memberId;
    @Column(name = "venue_id")
    private Long venueId;
    @Column(length = 120)
    private String subject;
    @Column(length = 500)
    private String content;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 40)
    private String provider;
    @Column(name = "provider_message_id", length = 80)
    private String providerMessageId;
    @Column(length = 300)
    private String error;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
