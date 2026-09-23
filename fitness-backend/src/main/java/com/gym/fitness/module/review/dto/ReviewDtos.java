package com.gym.fitness.module.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ReviewDtos {
    private ReviewDtos() {
    }

    public record ReviewView(Long id, Long bookingId, Long memberId, String memberName, Long courseId,
                             String courseName, Long scheduleId, Long coachId, String coachName,
                             LocalDateTime startTime, Integer rating, String tags, String content,
                             LocalDateTime createdAt) {
    }

    /** 可评价的课次（本人已签到且还没评过）。 */
    public record PendingReview(Long bookingId, Long scheduleId, Long courseId, String courseName,
                                Long coachId, String coachName, LocalDateTime startTime,
                                LocalDateTime checkedInAt) {
    }

    /** 评分汇总：平均分、各星级分布、最近评价。 */
    public record RatingSummary(Long targetId, String targetName, long reviewCount, Double averageRating,
                                Map<Integer, Long> distribution, List<ReviewView> recent) {
    }

    /** 门店整体评分汇总：各教练平均分 + 低分记录。 */
    public record VenueReviewSummary(long reviewCount, Double averageRating, Map<Integer, Long> distribution,
                                     List<CoachRating> coaches, List<ReviewView> lowRated) {
    }

    public record CoachRating(Long coachId, String coachName, long reviewCount, Double averageRating) {
    }

    @Data
    public static class ReviewRequest {
        @NotNull private Long bookingId;
        @NotNull @Min(1) @Max(5) private Integer rating;
        @Size(max = 200) private String tags;
        @Size(max = 500) private String content;
    }
}
