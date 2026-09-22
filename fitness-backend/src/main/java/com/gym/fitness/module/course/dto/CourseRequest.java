package com.gym.fitness.module.course.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CourseRequest {
    @NotBlank @Size(max = 100) private String name;
    @NotBlank private String type;
    @Size(max = 1000) private String description;
    @Min(15) @Max(300) private Integer durationMinutes;
    private String intensity;
    @Min(1) @Max(500) private Integer maxCapacity;
    @DecimalMin("0") private BigDecimal fee;
    private Long venueId;
    private String status;
}
