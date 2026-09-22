package com.gym.fitness.module.member.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CourseRecommendationResponse {
    private String summary;
    private String source;
    private String algorithmVersion;
    private List<Item> recommendations;

    @Data
    @Builder
    public static class Item {
        private Long scheduleId;
        private Long courseId;
        private String courseName;
        private String courseType;
        private String intensity;
        private String coachName;
        private LocalDateTime startTime;
        private Integer remainingPlaces;
        private BigDecimal fee;
        private int score;
        private List<String> reasons;
    }
}
