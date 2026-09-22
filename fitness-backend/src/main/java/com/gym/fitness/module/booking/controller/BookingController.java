package com.gym.fitness.module.booking.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.booking.dto.BookingRequest;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.service.BookingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 预约管理控制器
 */
@Tag(name = "预约与签到")
@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService service;
    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public Result<List<BookingEntity>> list() { return Result.success(service.list()); }
    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_WRITE')")
    public Result<BookingEntity> create(@Valid @RequestBody BookingRequest request) { return Result.success(service.create(request)); }
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('BOOKING_WRITE')")
    public Result<BookingEntity> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) { return Result.success(service.cancel(id, reason)); }
    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('CHECKIN_WRITE')")
    public Result<BookingEntity> checkIn(@PathVariable Long id) { return Result.success(service.checkIn(id)); }
    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('CHECKIN_WRITE')")
    public Result<BookingEntity> markNoShow(@PathVariable Long id) { return Result.success(service.markNoShow(id)); }
}
