package com.gym.fitness.module.member.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.commercial.dto.CommercialDtos;
import com.gym.fitness.module.member.dto.MemberBookingResponse;
import com.gym.fitness.module.member.dto.MemberPortalProfileResponse;
import com.gym.fitness.module.member.dto.MemberPurchaseRequest;
import com.gym.fitness.module.member.dto.MemberScheduleResponse;
import com.gym.fitness.module.member.dto.CourseRecommendationResponse;
import com.gym.fitness.module.member.dto.RecommendationRequest;
import com.gym.fitness.module.member.service.CourseRecommendationService;
import com.gym.fitness.module.member.service.MemberPortalService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
public class MemberPortalController {
    private final MemberPortalService service;
    private final CourseRecommendationService recommendationService;

    @GetMapping("/profile")
    public Result<MemberPortalProfileResponse> profile() {
        return Result.success(service.profile());
    }

    @GetMapping("/schedules")
    public Result<List<MemberScheduleResponse>> schedules() {
        return Result.success(service.schedules());
    }

    @GetMapping("/bookings")
    public Result<List<MemberBookingResponse>> bookings() {
        return Result.success(service.bookings());
    }

    @GetMapping("/products")
    public Result<List<CommercialDtos.MemberProductView>> products() {
        return Result.success(service.products());
    }

    @PostMapping("/purchase")
    public Result<CommercialDtos.OrderResponse> purchase(@Valid @RequestBody MemberPurchaseRequest request) {
        return Result.success(service.purchase(request.getProductId(), request.getIdempotencyKey(), request.getMemberCouponId()));
    }

    @PostMapping("/recommendations")
    public Result<CourseRecommendationResponse> recommendations(
            @Valid @RequestBody RecommendationRequest request) {
        return Result.success(recommendationService.recommend(request));
    }

    @PostMapping("/bookings")
    public Result<Void> createBooking(@Valid @RequestBody ScheduleSelection request) {
        service.createBooking(request.getScheduleId(), request.getEntitlementId());
        return Result.success();
    }

    @PutMapping("/bookings/{id}/cancel")
    public Result<Void> cancelBooking(@PathVariable Long id) {
        service.cancelBooking(id);
        return Result.success();
    }

    @PostMapping("/schedules/{id}/waitlist")
    public Result<Void> joinWaitlist(@PathVariable Long id) {
        service.joinWaitlist(id);
        return Result.success();
    }

    @PostMapping("/bookings/{id}/leave")
    public Result<Void> requestLeave(@PathVariable Long id, @RequestBody(required = false) LeaveRequest request) {
        service.requestLeave(id, request == null ? null : request.getReason());
        return Result.success();
    }

    @Data
    public static class ScheduleSelection {
        @NotNull
        private Long scheduleId;
        private Long entitlementId;
    }

    @Data
    public static class LeaveRequest { private String reason; }
}
