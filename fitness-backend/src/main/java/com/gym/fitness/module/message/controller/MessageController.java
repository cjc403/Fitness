package com.gym.fitness.module.message.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.message.dto.MessageDtos.*;
import com.gym.fitness.module.message.service.MessageDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 消息通道与投递记录。 */
@RestController
@RequestMapping("/api/v1/admin/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageDispatchService service;

    @GetMapping("/channels")
    @PreAuthorize("hasAnyAuthority('MEMBER_READ','ACCOUNT_MANAGE')")
    public Result<List<ChannelView>> channels() {
        return Result.success(service.channelViews());
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAnyAuthority('MEMBER_READ','ACCOUNT_MANAGE')")
    public Result<List<DeliveryView>> deliveries() {
        return Result.success(service.recent());
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('MEMBER_WRITE')")
    public Result<List<DeliveryView>> send(@Valid @RequestBody SendRequest request) {
        return Result.success(service.sendToMember(request));
    }
}
