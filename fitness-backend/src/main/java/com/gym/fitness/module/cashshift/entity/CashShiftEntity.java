package com.gym.fitness.module.cashshift.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="cash_shift") @Getter @Setter @NoArgsConstructor
public class CashShiftEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long venueId;
 @Column(unique=true) private Long activeVenueId;
 private String shiftNo;
 private java.time.LocalDate businessDate;
 private String status;
 private Long version;
 private Long openedBy;
 private java.time.LocalDateTime openedAt;
 private Long submittedBy;
 private java.time.LocalDateTime closedAt;
 private Long reviewedBy;
 private java.time.LocalDateTime reviewedAt;
 @Column(length=1000) private String reviewReason;
 @Column(length=1000) private String note;
 @Column(length=1000) private String differenceReason;
 @Column(length=1000) private String handoverNote;
 private Long baselinePaymentId;
 private Long openingPaymentId;
 private Long cutoffPaymentId;
 private String ledgerFingerprint;
 private Long systemReceipts;
 private Long systemRefunds;
 private Long actualReceipts;
 private Long actualRefunds;
}
