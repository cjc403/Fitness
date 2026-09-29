package com.gym.fitness.module.member.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.member.dto.BodyMeasurementDtos.*;
import com.gym.fitness.module.member.service.BodyMeasurementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 体测档案：会员自助录入/查看自己的，教练与门店员工可代录（教练限自己学员）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BodyMeasurementController {
    private final BodyMeasurementService service;

    @GetMapping("/member/body-measurements")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<Trend> mine() {
        return Result.success(service.myTrend());
    }

    @PostMapping("/member/body-measurements")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<MeasurementView> recordMine(@Valid @RequestBody MeasurementRequest request) {
        return Result.success(service.saveMine(request));
    }

    @PutMapping("/member/training-goal")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<Trend> setMyGoal(@Valid @RequestBody GoalRequest request) {
        return Result.success(service.setMyGoal(request));
    }

    @DeleteMapping("/member/body-measurements/{measurementId}")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<Void> deleteMine(@PathVariable Long measurementId) {
        service.delete(service.myMemberId(), measurementId);
        return Result.success();
    }

    @DeleteMapping("/admin/members/{memberId}/body-measurements/{measurementId}")
    @PreAuthorize("hasAnyAuthority('MEMBER_WRITE','COACH_WORKBENCH')")
    public Result<Void> deleteForMember(@PathVariable Long memberId, @PathVariable Long measurementId) {
        service.delete(memberId, measurementId);
        return Result.success();
    }

    @GetMapping("/admin/members/{memberId}/body-measurements")
    @PreAuthorize("hasAnyAuthority('MEMBER_READ','COACH_WORKBENCH')")
    public Result<Trend> memberTrend(@PathVariable Long memberId) {
        return Result.success(service.memberTrend(memberId));
    }

    @PostMapping("/admin/members/{memberId}/body-measurements")
    @PreAuthorize("hasAnyAuthority('MEMBER_WRITE','COACH_WORKBENCH')")
    public Result<MeasurementView> recordForMember(@PathVariable Long memberId,
                                                   @Valid @RequestBody MeasurementRequest request) {
        return Result.success(service.saveForMember(memberId, request));
    }

    @PutMapping("/admin/members/{memberId}/training-goal")
    @PreAuthorize("hasAnyAuthority('MEMBER_WRITE','COACH_WORKBENCH')")
    public Result<Trend> setGoalForMember(@PathVariable Long memberId, @Valid @RequestBody GoalRequest request) {
        return Result.success(service.setGoalForMember(memberId, request));
    }
}
