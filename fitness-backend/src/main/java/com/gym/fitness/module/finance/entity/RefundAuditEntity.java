package com.gym.fitness.module.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="refund_audit") @Getter @Setter
public class RefundAuditEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long refundId;
    @Column(nullable=false,length=30) private String action;
    @Column(nullable=false,length=1000) private String reason;
    @Column(nullable=false) private Long operatorId;
    @Column(nullable=false) private LocalDateTime occurredAt=LocalDateTime.now();
}

