package com.gym.fitness.module.member.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberPortalProfileResponse {
    private Long id;
    private String memberNo;
    private String realName;
    private String phone;
    private String email;
    private Long venueId;
    private String status;
    private Boolean blacklistFlag;
}
