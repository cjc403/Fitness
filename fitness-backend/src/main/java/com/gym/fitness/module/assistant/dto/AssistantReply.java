package com.gym.fitness.module.assistant.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssistantReply {
    private String answer;
    private List<String> suggestions;
    private String source;
    private boolean aiEnabled;
}
