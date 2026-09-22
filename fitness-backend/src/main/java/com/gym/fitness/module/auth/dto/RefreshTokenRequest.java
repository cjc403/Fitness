package com.gym.fitness.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "刷新Token不能为空")
    private String refreshToken;
}
