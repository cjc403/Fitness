package com.gym.fitness.module.notice.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.notice.dto.NotificationDtos.NotificationSummary;
import com.gym.fitness.module.notice.dto.NotificationDtos.NotificationView;
import com.gym.fitness.module.notice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 站内消息：会员端与员工端共用（按当前登录用户过滤），所以只要求已登录。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public Result<List<NotificationView>> mine() {
        return Result.success(service.mine());
    }

    @GetMapping("/summary")
    public Result<NotificationSummary> summary() {
        return Result.success(new NotificationSummary(service.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return Result.success();
    }

    @PostMapping("/read-all")
    public Result<NotificationSummary> markAllRead() {
        service.markAllRead();
        return Result.success(new NotificationSummary(service.unreadCount()));
    }
}
