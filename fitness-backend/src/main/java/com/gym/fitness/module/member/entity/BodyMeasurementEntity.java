package com.gym.fitness.module.member.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员体测记录（一次测量一行）。
 *
 * <p>同一会员同一天只保留一条（数据库唯一约束兜底）：重复提交视为修正当天记录，
 * 这样趋势曲线不会出现重复点。</p>
 */
@Entity
@Table(name = "body_measurement")
@Getter
@Setter
@NoArgsConstructor
public class BodyMeasurementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;
    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;
    @Column(name = "body_fat_rate", precision = 5, scale = 2)
    private BigDecimal bodyFatRate;
    @Column(name = "muscle_kg", precision = 5, scale = 2)
    private BigDecimal muscleKg;
    @Column(name = "chest_cm", precision = 5, scale = 2)
    private BigDecimal chestCm;
    @Column(name = "waist_cm", precision = 5, scale = 2)
    private BigDecimal waistCm;
    @Column(name = "hip_cm", precision = 5, scale = 2)
    private BigDecimal hipCm;
    @Column(name = "resting_heart_rate")
    private Integer restingHeartRate;
    @Column(length = 300)
    private String note;
    @Column(name = "recorded_by")
    private Long recordedBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
