package com.gym.fitness.module.analytics.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.analytics.dto.AnalyticsDtos.*;
import com.gym.fitness.module.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 经营分析：概览、教练绩效、会员风险名单。 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService service;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    public Result<Overview> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(service.overview(from, to));
    }

    @GetMapping("/coaches")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    public Result<List<CoachRank>> coaches(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(service.coachRanking(from, to));
    }

    @GetMapping("/members")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    public Result<MemberAnalytics> members(@RequestParam(defaultValue = "false") boolean riskOnly) {
        return Result.success(service.memberAnalytics(riskOnly));
    }
}
