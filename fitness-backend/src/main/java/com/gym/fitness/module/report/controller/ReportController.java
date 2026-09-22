package com.gym.fitness.module.report.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 报表统计控制器
 */
@Tag(name = "报表统计", description = "场馆业绩、教练工作量、销售业绩等统计报表接口")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('DASHBOARD_READ')")
public class ReportController {
    private final ReportService reportService;

    @Operation(summary = "经营看板汇总")
    @GetMapping("/dashboard")
    public Result<?> dashboard() {
        return Result.success(reportService.dashboard());
    }
    
    @Operation(summary = "场馆业绩统计")
    @GetMapping("/venue-performance")
    public Result<?> getVenuePerformance(@RequestParam Long venueId) {
        return Result.success(reportService.dashboard());
    }
    
    @Operation(summary = "教练工作量统计")
    @GetMapping("/coach-workload")
    public Result<?> getCoachWorkload(@RequestParam Long coachId) {
        return Result.success(reportService.dashboard());
    }
    
    @Operation(summary = "销售业绩统计")
    @GetMapping("/sales-performance")
    public Result<?> getSalesPerformance(@RequestParam Long salesId) {
        return Result.success(reportService.dashboard());
    }
    
    @Operation(summary = "出勤率统计")
    @GetMapping("/attendance-rate")
    public Result<?> getAttendanceRate() {
        return Result.success(reportService.dashboard().get("attendanceRate"));
    }
}
