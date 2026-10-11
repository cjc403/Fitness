package com.gym.fitness.module.cashshift.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="cash_shift_channel", uniqueConstraints=@UniqueConstraint(columnNames={"shift_id","channel"})) @Getter @Setter @NoArgsConstructor
public class CashShiftChannelEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long shiftId;
 private String channel;
 private Long systemReceipts;
 private Long systemRefunds;
 private Long actualReceipts;
 private Long actualRefunds;
}
