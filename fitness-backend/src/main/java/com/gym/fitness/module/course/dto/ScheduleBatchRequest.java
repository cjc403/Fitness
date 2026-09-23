package com.gym.fitness.module.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 周期性批量排课：按「周几 + 时段 + 日期区间」一次性生成整段课表。
 *
 * <p>{@code dryRun=true} 时只做预检并返回将生成 / 将被跳过的课次，不落库，
 * 供界面在确认前先展示冲突情况。</p>
 */
@Data
public class ScheduleBatchRequest {
    @NotNull private Long courseId;
    private Long coachId;
    @NotBlank private String coachName;
    private Long venueId;
    /** 1=周一 … 7=周日，可多选。 */
    @NotEmpty private List<Integer> weekdays;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;
    @NotNull private LocalDate fromDate;
    @NotNull private LocalDate toDate;
    @Min(1) @Max(500) private Integer maxStudents;
    private Boolean dryRun;
}
