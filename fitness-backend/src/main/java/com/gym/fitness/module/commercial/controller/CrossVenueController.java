package com.gym.fitness.module.commercial.controller;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.commercial.entity.MembershipProductEntity;
import com.gym.fitness.module.commercial.repository.MembershipProductRepository;
import com.gym.fitness.module.commercial.service.CrossVenueService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品的跨店适用范围（连锁）。
 *
 * <p>适用门店为空表示"仅限商品所属门店"，与历史行为一致。</p>
 */
@RestController
@RequestMapping("/api/v1/commercial/products")
@RequiredArgsConstructor
public class CrossVenueController {
    private final CrossVenueService crossVenue;
    private final MembershipProductRepository products;

    /** 只有总部或商品所属门店的人能读/改该商品的适用范围。 */
    private MembershipProductEntity requireManageable(Long id) {
        MembershipProductEntity product = products.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
        if (!com.gym.fitness.common.util.SecurityUtil.isSuperAdmin()) {
            Long venueId = com.gym.fitness.common.util.SecurityUtil.getCurrentOrganizationId();
            if (venueId == null || !venueId.equals(product.getVenueId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他门店的商品");
            }
        }
        return product;
    }

    @GetMapping("/{id}/venue-scope")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public Result<List<Long>> scope(@PathVariable Long id) {
        requireManageable(id);
        return Result.success(crossVenue.declaredScope(id));
    }

    @PutMapping("/{id}/venue-scope")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public Result<List<Long>> updateScope(@PathVariable Long id, @RequestBody ScopeRequest request) {
        MembershipProductEntity product = requireManageable(id);
        return Result.success(crossVenue.replaceScope(id, product.getVenueId(), request.getVenueIds()));
    }

    @Data
    public static class ScopeRequest {
        /** 除销售门店之外的适用门店；空数组 = 仅限本店。 */
        private List<Long> venueIds;
    }
}
