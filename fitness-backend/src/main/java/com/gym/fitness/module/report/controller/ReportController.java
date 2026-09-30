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
    
    // 说明：原先的 venue-performance / coach-workload / sales-performance 三个接口
    // 完全忽略入参、都返回同一个本店看板，会误导调用方，已删除（要用请按真实口径实现）。

    @Operation(summary = "出勤率统计")
    @GetMapping("/attendance-rate")
    public Result<?> getAttendanceRate() {
        return Result.success(reportService.dashboard().get("attendanceRate"));
    }
}
