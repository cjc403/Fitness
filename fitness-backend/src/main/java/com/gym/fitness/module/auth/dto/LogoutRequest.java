package com.gym.fitness.module.auth.dto;

import jakarta.validation.constraints.Size;

public record LogoutRequest(@Size(max = 4096) String refreshToken) { }
