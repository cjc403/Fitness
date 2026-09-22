package com.gym.fitness.module.course.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.course.dto.CoachWorkbenchResponse;
import com.gym.fitness.module.course.service.CoachWorkbenchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coach")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('COACH_WORKBENCH')")
public class CoachWorkbenchController {
    private final CoachWorkbenchService service;

    @GetMapping("/workbench")
    public Result<CoachWorkbenchResponse> workbench() {
        return Result.success(service.workbench());
    }
}
