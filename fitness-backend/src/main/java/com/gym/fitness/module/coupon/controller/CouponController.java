package com.gym.fitness.module.coupon.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.coupon.dto.CouponDtos.*;
import com.gym.fitness.module.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 优惠券：管理端做券模板与发放，会员端看自己的券并在下单前试算。
 */
@RestController
@RequestMapping("/api/v1/commercial")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService service;

    @GetMapping("/coupons")
    @PreAuthorize("hasAuthority('COUPON_READ')")
    public Result<List<TemplateView>> templates() {
        return Result.success(service.listTemplates());
    }

    @PostMapping("/coupons")
    @PreAuthorize("hasAuthority('COUPON_WRITE')")
    public Result<TemplateView> create(@Valid @RequestBody TemplateRequest request) {
        return Result.success(service.createTemplate(request));
    }

    @PutMapping("/coupons/{id}")
    @PreAuthorize("hasAuthority('COUPON_WRITE')")
    public Result<TemplateView> update(@PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
        return Result.success(service.updateTemplate(id, request));
    }

    @PostMapping("/coupons/{id}/issue")
    @PreAuthorize("hasAuthority('COUPON_WRITE')")
    public Result<Map<String, Integer>> issue(@PathVariable Long id, @Valid @RequestBody IssueRequest request) {
        return Result.success(Map.of("issued", service.issue(id, request)));
    }

    @GetMapping("/members/{memberId}/coupons")
    @PreAuthorize("hasAuthority('COUPON_READ')")
    public Result<List<MemberCouponView>> memberCoupons(@PathVariable Long memberId) {
        return Result.success(service.memberCoupons(memberId));
    }

    /** 开卡时按「会员 + 商品」试算这位会员能用的券。 */
    @GetMapping("/members/{memberId}/coupon-quote")
    @PreAuthorize("hasAuthority('COUPON_READ')")
    public Result<CouponQuote> memberQuote(@PathVariable Long memberId, @RequestParam Long productId) {
        return Result.success(service.quote(memberId, productId));
    }

    @GetMapping("/me/coupons")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<List<MemberCouponView>> myCoupons() {
        return Result.success(service.myCoupons());
    }

    @GetMapping("/me/coupon-quote")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<CouponQuote> myQuote(@RequestParam Long productId) {
        return Result.success(service.quoteForCurrentMember(productId));
    }
}
