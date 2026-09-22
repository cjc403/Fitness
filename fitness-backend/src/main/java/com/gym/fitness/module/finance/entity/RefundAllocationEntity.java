package com.gym.fitness.module.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="refund_allocation",uniqueConstraints=@UniqueConstraint(columnNames={"refund_id","funding_id"})) @Getter @Setter
public class RefundAllocationEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long refundId;
    @Column(nullable=false) private Long fundingId;
    @Column(nullable=false) private Long orderId;
    @Column(nullable=false) private long amountCents;
    private Long paymentTransactionId;
}

