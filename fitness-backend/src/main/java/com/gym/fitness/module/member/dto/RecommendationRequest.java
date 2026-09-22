package com.gym.fitness.module.member.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RecommendationRequest {
    @Size(max = 100)
    private String goal;

    @Pattern(regexp = "^(ANY|MORNING|AFTERNOON|EVENING)$", message = "偏好时段不正确")
    private String preferredTime = "ANY";
}
