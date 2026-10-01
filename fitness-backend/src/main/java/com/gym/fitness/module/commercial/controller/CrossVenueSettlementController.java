package com.gym.fitness.module.commercial.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.commercial.service.CrossVenueSettlementService;
import com.gym.fitness.module.commercial.service.CrossVenueSettlementService.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 跨店服务分摊汇总：总部看全部；店长只看与本店有关的分摊（本店提供服务，或本店发出的卡）。
 */
@RestController
@RequestMapping("/api/v1/admin/finance/cross-venue-settlement")
@RequiredArgsConstructor
public class CrossVenueSettlementController {
    private final CrossVenueSettlementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public Result<Summary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(service.summary(from, to));
    }
}
