package com.gym.fitness.module.course.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class RoomDtos {
    private RoomDtos() {
    }

    public record RoomView(Long id, Long venueId, String name, Integer capacity, String status, String note) {
    }

    /** 某天某个教室的占用情况。 */
    public record RoomOccupancy(Long roomId, String roomName, Integer capacity, long usedMinutes,
                                int occupancyRate, List<Session> sessions) {
        public record Session(Long scheduleId, String courseName, String coachName, LocalDateTime start,
                              LocalDateTime end, Integer enrolledCount, Integer maxStudents) {
        }
    }

    @Data
    public static class RoomRequest {
        @NotBlank @Size(max = 60) private String name;
        @Min(0) private Integer capacity;
        private String status;
        @Size(max = 200) private String note;
        /** 仅超级管理员可指定归属门店。 */
        private Long venueId;
    }
}
