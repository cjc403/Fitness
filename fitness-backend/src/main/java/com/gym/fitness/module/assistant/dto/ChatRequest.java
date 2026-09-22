package com.gym.fitness.module.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequest {
    @NotBlank @Size(max = 1000)
    private String message;

    @Valid @Size(max = 6)
    private List<ChatTurn> history = new ArrayList<>();

    @Data
    public static class ChatTurn {
        @NotBlank @Pattern(regexp = "user|assistant")
        private String role;

        @NotBlank @Size(max = 500)
        private String content;
    }
}
