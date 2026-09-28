package com.gym.fitness.module.course.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 教室/操课房：门店下的可占用资源。排课可指定教室，同一时段同一教室不能排两节课。 */
@Entity
@Table(name = "room")
@Getter
@Setter
@NoArgsConstructor
public class RoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    @Column(nullable = false, length = 60)
    private String name;
    /** 可容纳人数；0 表示未维护。 */
    @Column(nullable = false)
    private Integer capacity = 0;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 200)
    private String note;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
