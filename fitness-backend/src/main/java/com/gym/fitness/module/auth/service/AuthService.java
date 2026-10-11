package com.gym.fitness.module.auth.service;

import com.gym.fitness.module.auth.dto.LoginRequest;
import com.gym.fitness.module.auth.dto.LoginResponse;
import com.gym.fitness.module.auth.dto.ChangePasswordRequest;

/**
 * 认证服务接口
 */
public interface AuthService {
    
    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * 用户登出
     */
    void logout(String refreshToken);
    
    /**
     * 刷新Token
     */
    LoginResponse refreshToken(String refreshToken);

    LoginResponse changePassword(ChangePasswordRequest request);
}
