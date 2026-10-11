package com.gym.fitness.module.organization.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.organization.dto.VenueRequest;
import com.gym.fitness.module.organization.dto.VenueResponse;
import com.gym.fitness.module.organization.service.VenueService;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 场馆管理控制器
 */
@Tag(name = "场馆管理", description = "场馆的增删改查、切换等接口")
@RestController
@RequestMapping("/api/v1/admin/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;
    private final JwtTokenProvider tokenProvider;
    
    @Operation(summary = "获取场馆列表")
    @GetMapping
    @PreAuthorize("hasAuthority('VENUE_READ')")
    public Result<java.util.List<VenueResponse>> listVenues() {
        return Result.success(venueService.list());
    }
    
    @Operation(summary = "获取场馆详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VENUE_READ')")
    public Result<VenueResponse> getVenue(@PathVariable Long id) {
        return Result.success(venueService.get(id));
    }
    
    @Operation(summary = "创建场馆")
    @PostMapping
    @PreAuthorize("hasAuthority('VENUE_CREATE')")
    public Result<VenueResponse> createVenue(@Valid @RequestBody VenueRequest request) {
        return Result.success(venueService.create(request));
    }

    @Operation(summary = "更新场馆")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VENUE_WRITE')")
    public Result<VenueResponse> updateVenue(@PathVariable Long id,
                                             @Valid @RequestBody VenueRequest request) {
        return Result.success(venueService.update(id, request));
    }
    
    @Operation(summary = "切换场馆")
    @PostMapping("/{id}/switch")
    @PreAuthorize("hasAuthority('VENUE_SWITCH')")
    public Result<java.util.Map<String, Object>> switchVenue(@PathVariable Long id) {
        var user = SecurityUtil.getCurrentUser();
        if (user == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        VenueResponse venue = venueService.get(id);
        String accessToken = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole(), id, user.getSessionVersion(), user.getSessionId());
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", tokenProvider.generateRefreshToken(user.getId(), user.getSessionVersion(), user.getSessionId()));
        data.put("venue", venue);
        return Result.success(data);
    }
}
