package com.gym.fitness.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class BodyMeasurementDtos {
    private BodyMeasurementDtos() {
    }

    public record MeasurementView(Long id, Long memberId, LocalDate measuredOn, BigDecimal weightKg,
                                  BigDecimal bodyFatRate, BigDecimal muscleKg, BigDecimal chestCm,
                                  BigDecimal waistCm, BigDecimal hipCm, Integer restingHeartRate,
                                  String note, Long recordedBy, String recordedByName,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 曲线上的一个点。 */
    public record TrendPoint(LocalDate date, BigDecimal weightKg, BigDecimal bodyFatRate,
                             BigDecimal muscleKg, BigDecimal waistCm) {
    }

    /** 首末对比：一项指标从开始到现在的变化。 */
    public record Change(String metric, BigDecimal from, BigDecimal to, BigDecimal delta) {
    }

    public record Trend(Long memberId, String memberName, String trainingGoal, String trainingGoalNote,
                        long recordCount, List<TrendPoint> points, List<Change> changes,
                        List<MeasurementView> records) {
    }

    @Data
    public static class MeasurementRequest {
        @NotNull private LocalDate measuredOn;
        private BigDecimal weightKg;
        private BigDecimal bodyFatRate;
        private BigDecimal muscleKg;
        private BigDecimal chestCm;
        private BigDecimal waistCm;
        private BigDecimal hipCm;
        private Integer restingHeartRate;
        @Size(max = 300) private String note;
    }

    @Data
    public static class GoalRequest {
        /** FAT_LOSS 减脂 | MUSCLE_GAIN 增肌 | POSTURE 体态 | ENDURANCE 体能 | NONE 清空 */
        @NotBlank private String trainingGoal;
        @Size(max = 200) private String note;
    }
}
