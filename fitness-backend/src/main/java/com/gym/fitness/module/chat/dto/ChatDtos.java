package com.gym.fitness.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {
    }

    /** 可发消息的人：按"员工 / 会员"分组，会员端只会看到员工。 */
    public record Contact(Long userId, String name, String roleCode, String roleName, String venueName,
                          boolean member, long unread) {
    }

    public record ContactGroups(List<Contact> staff, List<Contact> members) {
    }

    public record Conversation(Long userId, String name, String roleName, String roleCode,
                               String lastContent, LocalDateTime lastAt, long unread) {
    }

    public record Message(Long id, boolean mine, String senderName, String content, LocalDateTime createdAt) {
    }

    public record ChatThread(Long userId, String name, String roleName, String roleCode, List<Message> messages) {
    }

    public record Summary(long unreadTotal, long conversationCount) {
    }

    @Data
    public static class SendRequest {
        @NotNull private Long recipientUserId;
        @NotBlank @Size(max = 1000) private String content;
    }
}
