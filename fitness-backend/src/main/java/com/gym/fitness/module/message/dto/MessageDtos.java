package com.gym.fitness.module.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class MessageDtos {
    private MessageDtos() {
    }

    /** 通道状态：是否启用、是否模拟（provider=MOCK 表示不会真的发出去）。 */
    public record ChannelView(String code, String name, boolean enabled, boolean mock, String provider, String note) {
    }

    public record DeliveryView(Long id, String channel, String category, String recipientMasked, Long memberId,
                               Long venueId, String subject, String content, String status, String provider,
                               String providerMessageId, String error, LocalDateTime createdAt) {
    }

    /** 手动发一条消息（用于演示/通知，收件人必须是系统里的会员）。 */
    @Data
    public static class SendRequest {
        @NotNull private Long memberId;
        /** SMS | EMAIL | IN_APP，可多选；为空则发所有已启用通道。 */
        private List<String> channels;
        @NotBlank @Size(max = 30) private String category;
        @NotBlank @Size(max = 120) private String subject;
        @NotBlank @Size(max = 500) private String content;
    }
}
