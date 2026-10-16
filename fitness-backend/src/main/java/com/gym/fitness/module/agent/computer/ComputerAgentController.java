package com.gym.fitness.module.agent.computer;

import com.gym.fitness.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.gym.fitness.module.agent.computer.ComputerAgentDtos.*;

@RestController
@RequestMapping("/api/v1/agent/computer")
@PreAuthorize("hasAuthority('ASSISTANT_USE')")
@RequiredArgsConstructor
public class ComputerAgentController {
    private final ComputerAgentService service;

    @GetMapping("/session")
    public Result<Session> session() { return Result.success(service.session()); }

    @PostMapping(value = "/next", consumes = "application/json")
    public Result<Decision> next(@RequestBody String payload) { return Result.success(service.nextPayload(payload)); }
}
