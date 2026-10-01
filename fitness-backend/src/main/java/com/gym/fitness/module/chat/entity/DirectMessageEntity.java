package com.gym.fitness.module.chat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 站内一对一私信（真实投递，不经过任何外部通道）。 */
@Entity
@Table(name = "direct_message")
@Getter
@Setter
@NoArgsConstructor
public class DirectMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;
    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;
    /** 姓名快照：对方改名后历史消息不改写。 */
    @Column(name = "sender_name", nullable = false, length = 50)
    private String senderName;
    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;
    @Column(name = "venue_id")
    private Long venueId;
    @Column(nullable = false, length = 1000)
    private String content;
    @Column(name = "read_at")
    private LocalDateTime readAt;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
