package com.gym.fitness.module.auth.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.auth.dto.LoginRequest;
import com.gym.fitness.module.auth.dto.LoginResponse;
import com.gym.fitness.module.auth.dto.RefreshTokenRequest;
import com.gym.fitness.module.auth.dto.ChangePasswordRequest;
import com.gym.fitness.module.auth.service.AuthService;
import com.gym.fitness.module.account.dto.MemberAccountResponse;
import com.gym.fitness.module.account.dto.MemberRegistrationRequest;
import com.gym.fitness.module.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "用户登录、登出、Token刷新等接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final AccountService accountService;
    
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }
    
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
    
    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }

    @Operation(summary = "会员公开注册")
    @PostMapping("/register/member")
    public Result<MemberAccountResponse> registerMember(@Valid @RequestBody MemberRegistrationRequest request) {
        return Result.success("注册成功，请使用新账号登录", accountService.registerMember(request));
    }

    @Operation(summary = "查询可注册场馆")
    @GetMapping("/venues")
    public Result<List<AccountService.VenueOption>> venues() {
        return Result.success(accountService.publicVenues());
    }

    @Operation(summary = "修改本人密码")
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.success();
    }
}
