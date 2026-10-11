package com.gym.fitness.module.booking.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.booking.dto.BookingRequest;
import com.gym.fitness.module.booking.dto.BookingResponse;
import com.gym.fitness.module.booking.service.BookingService;
import com.gym.fitness.module.booking.service.BookingOverdueService;
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
    private final BookingOverdueService overdueService;
    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_READ')")
    public Result<List<BookingResponse>> list() { return Result.success(service.list().stream().map(BookingResponse::from).toList()); }
    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_WRITE')")
    public Result<BookingResponse> create(@Valid @RequestBody BookingRequest request) { return Result.success(BookingResponse.from(service.create(request))); }
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('BOOKING_WRITE')")
    public Result<BookingResponse> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) { return Result.success(BookingResponse.from(service.cancel(id, reason))); }
    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('CHECKIN_WRITE')")
    public Result<BookingResponse> checkIn(@PathVariable Long id) { return Result.success(BookingResponse.from(service.checkIn(id))); }
    @GetMapping("/overdue") @PreAuthorize("hasAuthority('CHECKIN_WRITE')")
    public Result<List<BookingOverdueService.OverdueItem>> overdue(){return Result.success(overdueService.overdue());}
    @PostMapping("/overdue/resolve") @PreAuthorize("hasAuthority('CHECKIN_WRITE')")
    public Result<BookingOverdueService.OverdueResult> resolveOverdue(){return Result.success(overdueService.resolve());}
    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('CHECKIN_WRITE')")
    public Result<BookingResponse> markNoShow(@PathVariable Long id) { return Result.success(BookingResponse.from(service.markNoShow(id))); }
}
