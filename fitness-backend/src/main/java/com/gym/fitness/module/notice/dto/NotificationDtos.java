package com.gym.fitness.module.notice.dto;

import java.time.LocalDateTime;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationView(Long id, String category, String title, String body,
                                   String refType, Long refId, LocalDateTime createdAt, boolean read) {}

    public record NotificationSummary(long unread) {}
}
