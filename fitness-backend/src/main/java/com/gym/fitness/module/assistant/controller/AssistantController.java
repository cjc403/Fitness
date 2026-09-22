package com.gym.fitness.module.assistant.controller;

import com.gym.fitness.common.response.Result;
import com.gym.fitness.module.assistant.dto.AssistantReply;
import com.gym.fitness.module.assistant.dto.ChatRequest;
import com.gym.fitness.module.assistant.service.AssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/assistant")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ASSISTANT_USE')")
public class AssistantController {
    private final AssistantService service;
    @PostMapping("/chat")
    public Result<AssistantReply> chat(@Valid @RequestBody ChatRequest request) {
        return Result.success(service.reply(request.getMessage(), request.getHistory()));
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(service.status());
    }
}
