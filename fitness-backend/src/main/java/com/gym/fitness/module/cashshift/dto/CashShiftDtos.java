package com.gym.fitness.module.cashshift.dto;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
public final class CashShiftDtos {
 public record OpenRequest(Long venueId,@Size(max=1000) String note,@NotBlank @Size(max=100) String idempotencyKey) {}
 public record ActualChannel(@NotBlank @Size(max=255) String channel,@NotNull BigDecimal actualReceipts,@NotNull BigDecimal actualRefunds) {}
 public record SubmitRequest(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(min=64,max=64) String ledgerFingerprint,@NotNull List<@NotNull @Valid ActualChannel> channels,@Size(max=1000) String differenceReason,@Size(max=1000) String handoverNote,@NotBlank @Size(max=100) String idempotencyKey) {}
 public record ReviewRequest(@NotNull @Min(0) Long expectedVersion,@NotBlank String decision,@Size(max=1000) String reason,@NotBlank @Size(max=100) String idempotencyKey) {}
}
