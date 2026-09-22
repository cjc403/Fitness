package com.gym.fitness.module.course.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CoachWorkbenchResponse {
    private Summary summary;
    private List<CoachSchedule> schedules;

    @Data
    @Builder
    public static class Summary {
        private long todaySchedules;
        private long upcomingSchedules;
        private long bookedStudents;
        private long attendedStudents;
        private long completedSessions;
        private double deliveredHours;
        private BigDecimal serviceRevenue;
        private BigDecimal estimatedCommission;
    }

    @Data
    @Builder
    public static class CoachSchedule {
        private Long id;
        private String courseName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String status;
        private Integer maxStudents;
        private Integer enrolledCount;
        private List<StudentBooking> students;
    }

    @Data
    @Builder
    public static class StudentBooking {
        private Long bookingId;
        private Long memberId;
        private String memberNo;
        private String realName;
        private String phone;
        private String status;
        private LocalDateTime checkedInAt;
    }
}
