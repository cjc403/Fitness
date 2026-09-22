package com.gym.fitness.module.account.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemberAccountResponse {
    private Long userId;
    private Long memberId;
    private String memberNo;
    private String username;
    private String accountStatus;
    private Boolean mustChangePassword;
}
