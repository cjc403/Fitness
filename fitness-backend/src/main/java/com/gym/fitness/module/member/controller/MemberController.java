package com.gym.fitness.module.member.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.common.response.PageResult;
import com.gym.fitness.module.member.dto.MemberRequest;
import com.gym.fitness.module.member.dto.MemberResponse;
import com.gym.fitness.module.member.service.MemberService;
import com.gym.fitness.module.account.dto.AccountStatusRequest;
import com.gym.fitness.module.account.dto.MemberAccountRequest;
import com.gym.fitness.module.account.dto.MemberAccountResponse;
import com.gym.fitness.module.account.dto.PasswordResetRequest;
import com.gym.fitness.module.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 会员管理控制器
 */
@Tag(name = "会员管理", description = "会员的增删改查、暂停、续费、升级等接口")
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AccountService accountService;
    
    @Operation(summary = "获取会员列表")
    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    public Result<PageResult<MemberResponse>> listMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long venueId) {
        return Result.success(memberService.list(keyword, page, size, venueId));
    }
    
    @Operation(summary = "获取会员详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBER_READ')")
    public Result<MemberResponse> getMember(@PathVariable Long id) {
        return Result.success(memberService.get(id));
    }
    
    @Operation(summary = "创建会员")
    @PostMapping
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<MemberResponse> createMember(@Valid @RequestBody MemberRequest request) {
        return Result.success(memberService.create(request));
    }
    
    @Operation(summary = "更新会员信息")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<MemberResponse> updateMember(@PathVariable Long id,
                                               @Valid @RequestBody MemberRequest request) {
        return Result.success(memberService.update(id, request));
    }
    
    @Operation(summary = "暂停会员服务")
    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<MemberResponse> pauseMember(@PathVariable Long id) {
        return Result.success(memberService.pause(id));
    }
    
    @Operation(summary = "会员续费")
    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<MemberResponse> renewMember(@PathVariable Long id) {
        return Result.success(memberService.renew(id));
    }

    @Operation(summary = "为现有会员开通登录账号")
    @PostMapping("/{id}/account")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<MemberAccountResponse> openAccount(@PathVariable Long id,
                                                      @Valid @RequestBody MemberAccountRequest request) {
        return Result.success(accountService.openMemberAccount(id, request));
    }

    @Operation(summary = "重置会员登录密码")
    @PutMapping("/{id}/account/password")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<MemberAccountResponse> resetPassword(@PathVariable Long id,
                                                        @Valid @RequestBody PasswordResetRequest request) {
        return Result.success(accountService.resetMemberPassword(id, request));
    }

    @Operation(summary = "启用或停用会员登录账号")
    @PutMapping("/{id}/account/status")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<MemberAccountResponse> updateAccountStatus(@PathVariable Long id,
                                                              @Valid @RequestBody AccountStatusRequest request) {
        return Result.success(accountService.updateMemberAccountStatus(id, request));
    }
}
