package com.gym.fitness.module.workout.dto;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
public final class TrainingPlanDtos {
 private TrainingPlanDtos() {}
 public record ExerciseInput(String name,String mode,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer sets,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer reps,BigDecimal weightKg,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer minutes,String note) {}
 public record Create(@NotNull @JsonDeserialize(using=InputNumbers.LongValue.class) Long memberId,String title,String goal,LocalDate startDate,LocalDate endDate,String note,List<@Valid ExerciseInput> exercises,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String idempotencyKey) {}
 public record Draft(String title,String goal,LocalDate startDate,LocalDate endDate,String note,List<@Valid ExerciseInput> exercises,@NotNull @Min(0) @JsonDeserialize(using=InputNumbers.LongValue.class) Long expectedVersion,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String idempotencyKey) {}
 public record Transition(@NotNull @Min(0) @JsonDeserialize(using=InputNumbers.LongValue.class) Long expectedVersion,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String idempotencyKey,String reason) {}
 public record SessionExerciseInput(@JsonDeserialize(using=InputNumbers.LongValue.class) Long exerciseId,String status,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer sets,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer reps,BigDecimal weightKg,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer minutes,String note) {}
 public record RecordSession(@NotNull @Min(0) @JsonDeserialize(using=InputNumbers.LongValue.class) Long expectedVersion,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String idempotencyKey,LocalDate performedOn,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer durationMinutes,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer effort,String note,@JsonDeserialize(using=InputNumbers.LongValue.class) Long bookingId,List<@Valid SessionExerciseInput> exercises) {}
 public record Feedback(@NotNull @Min(0) @JsonDeserialize(using=InputNumbers.LongValue.class) Long expectedVersion,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,80}") String idempotencyKey,@JsonDeserialize(using=InputNumbers.IntegerValue.class) Integer rating,String content) {}
 public record PageView<T>(List<T> records,long total,int page,int size) {}
 public record Student(Long id,String realName,String memberNo,Long venueId,String trainingGoal,String trainingGoalNote) {}
 public record VenueView(Long id,String name) {}
 public record Summary(long draftPlans,long activePlans,long archivedPlans,long sessionsThisMonth) {}
 public record Workbench(Summary summary,PageView<PlanSummary> plans,List<VenueView> venues) {}
 public record PlanSummary(Long id,Long memberId,String memberName,String memberNo,Long coachId,String coachName,Long venueId,String venueName,String title,String goal,LocalDate startDate,LocalDate endDate,String note,String status,long version,long exerciseCount,long sessionCount,LocalDate lastSessionOn,boolean canEdit,boolean canPublish,boolean canArchive,boolean canRecord,boolean canViewBody) {}
 public record ExerciseView(Long id,int position,String name,String mode,Integer sets,Integer reps,BigDecimal weightKg,Integer minutes,String note) {}
 public record SessionExerciseView(Long exerciseId,String name,String mode,int position,Integer sets,Integer reps,BigDecimal weightKg,Integer minutes,String status,String note,Integer targetSets,Integer targetReps,BigDecimal targetWeightKg,Integer targetMinutes) {}
 public record SessionView(Long id,Long planId,long version,LocalDate performedOn,int durationMinutes,Integer effort,String note,Long bookingId,String courseName,String status,String voidReason,String coachName,LocalDateTime createdAt,Integer feedbackRating,String feedbackContent,LocalDateTime feedbackAt,boolean canVoid,boolean canFeedback,List<SessionExerciseView> exercises) {}
 public record EventView(Long id,String type,String actorName,LocalDateTime createdAt,String reason,Long sessionId) {}
 public record Detail(PlanSummary plan,List<ExerciseView> exercises,PageView<SessionView> sessions,List<EventView> events,long eventTotal) {}
 public record BookingOption(Long id,String courseName,LocalDateTime startTime) {}
}
