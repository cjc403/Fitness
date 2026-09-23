package com.gym.fitness.module.review.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.review.dto.ReviewDtos.*;
import com.gym.fitness.module.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程评价。会员提交与查看自己的评价；教练看本人评分；店长/总部看门店汇总与低分记录。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService service;

    /** 本人已签到、待评价的课次。 */
    @GetMapping("/member/reviews/pending")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<List<PendingReview>> pending() {
        return Result.success(service.pendingForMe());
    }

    @GetMapping("/member/reviews")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<List<ReviewView>> mine() {
        return Result.success(service.mine());
    }

    @PostMapping("/member/reviews")
    @PreAuthorize("hasAuthority('MEMBER_SELF_SERVICE')")
    public Result<ReviewView> submit(@Valid @RequestBody ReviewRequest request) {
        return Result.success(service.submit(request));
    }

    /** 教练看本人评分汇总。 */
    @GetMapping("/coach/reviews/summary")
    @PreAuthorize("hasAuthority('COACH_WORKBENCH')")
    public Result<RatingSummary> coachSummary() {
        return Result.success(service.coachSummary());
    }

    /** 店长看本店、总部看全部：整体平均分、各教练平均分与低分记录。 */
    @GetMapping("/admin/reviews/summary")
    @PreAuthorize("hasAuthority('COURSE_REVIEW_READ')")
    public Result<VenueReviewSummary> venueSummary() {
        return Result.success(service.venueSummary());
    }
}
