package com.gym.fitness.module.cashshift.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="cash_shift_payment") @Getter @Setter @NoArgsConstructor
public class CashShiftPaymentEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long shiftId;
 @Column(unique=true,nullable=false) private Long paymentTransactionId;
 private String transactionNo;
 private Long orderId;
 private String transactionType;
 private String channel;
 private Long amountCents;
 private java.time.LocalDateTime occurredAt;
 private Long operatorId;
 private Boolean carriedOver;
}
