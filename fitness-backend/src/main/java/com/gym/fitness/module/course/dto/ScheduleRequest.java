package com.gym.fitness.module.course.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduleRequest {
    @NotNull private Long courseId;
    private Long coachId;
    @NotBlank private String coachName;
    private Long venueId;
    /** 上课教室（可选）。 */
    private Long roomId;
    @NotNull @FutureOrPresent private LocalDateTime scheduledStartTime;
    @NotNull private LocalDateTime scheduledEndTime;
    @Min(1) @Max(500) private Integer maxStudents;
}
