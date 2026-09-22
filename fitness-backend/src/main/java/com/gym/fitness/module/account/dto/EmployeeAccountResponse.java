package com.gym.fitness.module.account.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EmployeeAccountResponse {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String roleCode;
    private String roleName;
    private Long venueId;
    private String venueName;
    private String status;
    private Boolean mustChangePassword;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
