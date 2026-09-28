package com.gym.fitness.module.member.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 会籍/教练对会员的跟进记录。
 *
 * <p>配合 {@code member.sales_user_id}（会籍顾问）与 {@code member.coach_id}（专属教练）使用——
 * 这两个字段在 V1 就存在，此前一直没有业务逻辑读它。</p>
 */
@Entity
@Table(name = "member_follow_up")
@Getter
@Setter
@NoArgsConstructor
public class MemberFollowUpEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(name = "staff_user_id", nullable = false)
    private Long staffUserId;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    /** CALL 电话 | WECHAT 微信 | VISIT 到店 | OTHER 其它。 */
    @Column(name = "follow_type", nullable = false, length = 20)
    private String followType;
    @Column(nullable = false, length = 500)
    private String content;
    /** 约定下次跟进时间；到期后会出现在顾问工作台的"待跟进"里。 */
    @Column(name = "next_follow_at")
    private LocalDateTime nextFollowAt;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
