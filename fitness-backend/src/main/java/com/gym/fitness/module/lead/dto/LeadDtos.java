package com.gym.fitness.module.lead.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
public final class LeadDtos {
    private LeadDtos() {}
    public record CreateRequest(String name,String phone,String source,String intent,Long ownerId,Long venueId,String note,LocalDateTime nextFollowAt,
        @NotBlank @Size(max=100) String idempotencyKey) {}
    public record ProfileRequest(@NotNull @Min(0) Long expectedVersion,String name,String intent,Long ownerId,String note,
        @NotBlank @Size(max=100) String idempotencyKey) {}
    public record FollowRequest(@NotNull @Min(0) Long expectedVersion,String method,String content,String outcome,LocalDateTime nextFollowAt,
        @NotBlank @Size(max=100) String idempotencyKey) {}
    public record TrialRequest(@NotNull @Min(0) Long expectedVersion,Long hostId,LocalDateTime startsAt,LocalDateTime endsAt,String content,
        @NotBlank @Size(max=100) String idempotencyKey) {}
    public record TrialResultRequest(@NotNull @Min(0) Long expectedVersion,String status,String note,
        @NotBlank @Size(max=100) String idempotencyKey) {}
    public record ConvertRequest(@NotNull @Min(0) Long expectedVersion,String mode,Long productId,String paymentMethod,BigDecimal paidAmount,Long orderId,
        @NotBlank @Size(max=100) String idempotencyKey) {}
}
