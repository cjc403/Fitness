package com.gym.fitness.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotBlank @Size(min = 8, max = 72)
    private String newPassword;
}
