package com.gym.fitness.module.organization.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VenueResponse {
    private Long id;
    private String name;
    private String code;
    private Long parentVenueId;
    private String address;
    private String contactPhone;
    private String contactEmail;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
