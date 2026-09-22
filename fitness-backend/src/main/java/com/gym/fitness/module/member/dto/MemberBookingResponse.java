package com.gym.fitness.module.member.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MemberBookingResponse {
    private Long id;
    private Long scheduleId;
    private String courseName;
    private String coachName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private LocalDateTime bookedAt;
    private LocalDateTime checkedInAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Integer waitlistPosition;
    private LocalDateTime leaveRequestedAt;
    private Integer noShowPenaltyPoints;
    private boolean cancellationAllowed;
}
