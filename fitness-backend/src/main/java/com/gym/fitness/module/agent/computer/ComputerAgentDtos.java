package com.gym.fitness.module.agent.computer;

import com.fasterxml.jackson.databind.JsonNode;
import com.gym.fitness.module.auth.dto.LoginResponse;
import java.util.List;

public final class ComputerAgentDtos {
    private ComputerAgentDtos() { }
    public record Session(Long userId, String role, Long venueId, String sessionKey, String home,
                          List<String> permissions, LoginResponse.UserInfo userInfo) { }
    public record Decision(String action, JsonNode arguments, String summary) { }
}
