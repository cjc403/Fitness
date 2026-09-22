package com.gym.fitness.module.account.controller;

import com.gym.fitness.common.response.PageResult;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.account.dto.*;
import com.gym.fitness.module.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ACCOUNT_MANAGE')")
public class AccountController {
    private final AccountService service;

    @GetMapping("/employees")
    public Result<PageResult<EmployeeAccountResponse>> employees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long venueId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(service.listEmployees(keyword, roleCode, status, venueId, page, size));
    }

    @PostMapping("/employees")
    public Result<EmployeeAccountResponse> createEmployee(@Valid @RequestBody EmployeeAccountRequest request) {
        return Result.success(service.createEmployee(request));
    }

    @PutMapping("/employees/{id}")
    public Result<EmployeeAccountResponse> updateEmployee(@PathVariable Long id,
                                                           @Valid @RequestBody EmployeeAccountUpdateRequest request) {
        return Result.success(service.updateEmployee(id, request));
    }

    @PutMapping("/employees/{id}/status")
    public Result<EmployeeAccountResponse> updateEmployeeStatus(@PathVariable Long id,
                                                                 @Valid @RequestBody AccountStatusRequest request) {
        return Result.success(service.updateEmployeeStatus(id, request));
    }

    @PutMapping("/employees/{id}/password")
    public Result<EmployeeAccountResponse> resetEmployeePassword(@PathVariable Long id,
                                                                  @Valid @RequestBody PasswordResetRequest request) {
        return Result.success(service.resetEmployeePassword(id, request));
    }
}
