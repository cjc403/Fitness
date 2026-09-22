package com.gym.fitness.module.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应DTO
 */
@Data
public class LoginResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UserInfo userInfo;
    
    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String realName;
        private String role;
        private String roleName;
        private List<String> permissions;
        private Long organizationId;
        private String organizationName;
        private Boolean mustChangePassword;
    }
}
