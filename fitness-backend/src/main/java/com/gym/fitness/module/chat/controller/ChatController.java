package com.gym.fitness.module.chat.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.chat.dto.ChatDtos.*;
import com.gym.fitness.module.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 站内私信：所有登录角色都能用（谁能发给谁由服务层判定）。
 */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatController {
    private final ChatService service;

    @GetMapping("/contacts")
    public Result<ContactGroups> contacts() {
        return Result.success(service.contacts());
    }

    @GetMapping("/conversations")
    public Result<List<Conversation>> conversations() {
        return Result.success(service.conversations());
    }

    @GetMapping("/summary")
    public Result<Summary> summary() {
        return Result.success(service.summary());
    }

    @GetMapping("/thread/{userId}")
    public Result<ChatThread> thread(@PathVariable Long userId) {
        return Result.success(service.thread(userId));
    }

    @PostMapping("/send")
    public Result<Message> send(@Valid @RequestBody SendRequest request) {
        return Result.success(service.send(request));
    }
}
