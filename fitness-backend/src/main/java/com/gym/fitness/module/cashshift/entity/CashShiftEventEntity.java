package com.gym.fitness.module.cashshift.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="cash_shift_event", uniqueConstraints=@UniqueConstraint(columnNames={"actor_id","request_key"})) @Getter @Setter @NoArgsConstructor
public class CashShiftEventEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long shiftId;
 private Long actorId;
 private String action;
 @Column(length=1000) private String content;
 @Column(columnDefinition="text") private String changesJson;
 private String requestKey;
 private String requestFingerprint;
 private java.time.LocalDateTime createdAt;
}
