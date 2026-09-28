package com.gym.fitness.module.course.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 排课编辑请求。
 *
 * <p>与新增排课的 ScheduleRequest 的差别：这里不做 scheduledStartTime 的 @FutureOrPresent 校验。
 * 编辑的用途正是纠正已经建错的排课（含已经过期的历史排课），如果沿用"必须晚于当前时间"，
 * 那种排课就永远改不动了。开始/结束的先后关系改由 CourseService 在服务层校验。</p>
 */
@Data
public class ScheduleUpdateRequest {
    @NotNull private Long courseId;
    private Long coachId;
    @NotBlank private String coachName;
    @NotNull private LocalDateTime scheduledStartTime;
    @NotNull private LocalDateTime scheduledEndTime;
    @Min(1) @Max(500) private Integer maxStudents;
    /** 上课教室（可选）。 */
    private Long roomId;
}
