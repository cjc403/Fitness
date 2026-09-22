package com.gym.fitness.module.training.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="coach_commission_rule",uniqueConstraints=@UniqueConstraint(columnNames={"venue_id","coach_id"}))
@Getter @Setter @NoArgsConstructor
public class CommissionRuleEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="venue_id",nullable=false) private Long venueId;
    @Column(name="coach_id",nullable=false) private Long coachId;
    @Column(nullable=false,precision=5,scale=2) private BigDecimal rate;
    @Column(name="updated_by",nullable=false) private Long updatedBy;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
