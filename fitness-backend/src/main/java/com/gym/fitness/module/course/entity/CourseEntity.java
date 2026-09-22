package com.gym.fitness.module.course.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "course")
@Getter @Setter @NoArgsConstructor
public class CourseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 20)
    private String type;
    @Column(length = 1000)
    private String description;
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    @Column(length = 20)
    private String intensity;
    @Column(name = "max_capacity")
    private Integer maxCapacity;
    @Column(precision = 10, scale = 2)
    private BigDecimal fee;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    @Column(length = 20)
    private String status;
    @Column(name = "created_by")
    private Long createdBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
