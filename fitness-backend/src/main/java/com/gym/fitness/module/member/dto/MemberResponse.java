package com.gym.fitness.module.member.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MemberResponse {
    private Long id;
    private String memberNo;
    private String realName;
    private String phone;
    private String email;
    private String idCard;
    private String gender;
    private LocalDate birthday;
    private String address;
    private Long salesUserId;
    private Long coachId;
    private Long venueId;
    private String status;
    private Boolean blacklistFlag;
    private Boolean accountOpened;
    private String accountUsername;
    private String accountStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
