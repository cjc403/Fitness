package com.gym.fitness.module.member.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MemberScheduleResponse {
    private Long scheduleId;
    private Long courseId;
    private String courseName;
    private String courseType;
    private String description;
    private Integer durationMinutes;
    private String intensity;
    private BigDecimal fee;
    private String coachName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer maxStudents;
    private Integer enrolledCount;
    private boolean booked;
    private String bookingStatus;
    private boolean full;
}
