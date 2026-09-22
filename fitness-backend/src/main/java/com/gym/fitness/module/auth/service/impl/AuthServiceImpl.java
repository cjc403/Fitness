package com.gym.fitness.module.auth.service.impl;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.module.auth.dto.LoginRequest;
import com.gym.fitness.module.auth.dto.LoginResponse;
import com.gym.fitness.module.auth.dto.ChangePasswordRequest;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.auth.service.AuthService;
import com.gym.fitness.security.JwtTokenProvider;
import com.gym.fitness.security.RolePermissionCatalog;
import com.gym.fitness.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.gym.fitness.common.util.SecurityUtil;

import java.time.LocalDateTime;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration}")
    private long accessTokenExpiration;
    
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            UserDetailsImpl user = (UserDetailsImpl) authentication.getPrincipal();
            sysUserRepository.findById(user.getId()).ifPresent(entity -> {
                entity.setLastLoginAt(LocalDateTime.now());
                sysUserRepository.save(entity);
            });
            return buildLoginResponse(user);
        } catch (AuthenticationException e) {
            log.warn("登录失败: username={}", request.getUsername());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }
    
    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }
    
    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        SysUserEntity entity = sysUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        UserDetailsImpl user = new UserDetailsImpl();
        user.setId(entity.getId());
        user.setUsername(entity.getUsername());
        user.setRealName(entity.getRealName());
        user.setRole(entity.getRole().getCode());
        user.setPermissions(RolePermissionCatalog.permissionsFor(entity.getRole().getCode()));
        user.setOrganizationId(entity.getVenueId());
        user.setOrganizationName(entity.getVenue().getName());
        user.setMustChangePassword(Boolean.TRUE.equals(entity.getMustChangePassword()));
        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        SysUserEntity user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "当前密码不正确");
        }
        ensureStrongPassword(request.getNewPassword());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        sysUserRepository.save(user);
    }

    private LoginResponse buildLoginResponse(UserDetailsImpl user) {
        String accessToken = tokenProvider.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getOrganizationId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(accessTokenExpiration);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        userInfo.setRole(user.getRole());
        userInfo.setRoleName(RolePermissionCatalog.roleName(user.getRole()));
        userInfo.setPermissions(RolePermissionCatalog.permissionsFor(user.getRole()));
        userInfo.setOrganizationId(user.getOrganizationId());
        userInfo.setOrganizationName(user.getOrganizationName());
        userInfo.setMustChangePassword(user.isMustChangePassword());
        response.setUserInfo(userInfo);
        return response;
    }

    private void ensureStrongPassword(String password) {
        if (password == null || password.length() < 8
                || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码至少8位，且需包含大小写字母、数字和特殊字符");
        }
    }
}
