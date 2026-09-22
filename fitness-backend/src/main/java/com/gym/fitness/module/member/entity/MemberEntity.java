package com.gym.fitness.module.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@Setter
@NoArgsConstructor
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(name = "member_no", nullable = false, unique = true, length = 50)
    private String memberNo;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(name = "id_card", length = 18)
    private String idCard;

    @Column(length = 10)
    private String gender;

    private LocalDate birthday;

    @Column(length = 200)
    private String address;

    @Column(name = "sales_user_id")
    private Long salesUserId;

    @Column(name = "coach_id")
    private Long coachId;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(length = 20)
    private String status;

    @Column(name = "blacklist_flag")
    private Boolean blacklistFlag;

    @Column(name = "access_token", unique = true, length = 64)
    private String accessToken;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
