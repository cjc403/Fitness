package com.gym.fitness.module.booking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequest {
    @NotNull private Long scheduleId;
    @NotNull private Long memberId;
    private Long entitlementId;
}
