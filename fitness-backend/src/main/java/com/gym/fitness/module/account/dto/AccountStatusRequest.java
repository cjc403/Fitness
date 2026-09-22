package com.gym.fitness.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AccountStatusRequest {
    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED")
    private String status;
}
