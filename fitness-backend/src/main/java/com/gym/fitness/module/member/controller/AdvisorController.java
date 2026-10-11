package com.gym.fitness.module.member.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.member.dto.AdvisorDtos.*;
import com.gym.fitness.module.member.service.AdvisorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 会籍顾问工作台、会员归属人选与跟进记录。 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdvisorController {
    private final AdvisorService service;
    private final com.gym.fitness.module.member.service.RetentionService retentionService;

    @GetMapping("/advisor/retention")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','SUPER_ADMIN')")
    public Result<RetentionWorkbench> retention(@RequestParam(defaultValue = "ALL") String filter,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(retentionService.workbench(filter, keyword));
    }

    /** 会员归属人下拉：会籍顾问 / 教练。 */
    @GetMapping("/advisors")
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    public Result<List<AdvisorView>> advisors(@RequestParam(required = false, defaultValue = "SALES") String role) {
        return Result.success(service.listAssignable(role));
    }

    @GetMapping("/advisor/workbench")
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    public Result<AdvisorWorkbench> workbench() {
        return Result.success(service.workbench());
    }

    @GetMapping("/members/{memberId}/follow-ups")
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    public Result<List<FollowUpView>> followUps(@PathVariable Long memberId) {
        return Result.success(service.memberFollowUps(memberId));
    }

    @PostMapping("/members/{memberId}/follow-ups")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<FollowUpView> addFollowUp(@PathVariable Long memberId, @Valid @RequestBody FollowUpRequest request) {
        return Result.success(service.addFollowUp(memberId, request));
    }
}
