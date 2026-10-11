package com.gym.fitness.module.workout.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="training_plan_exercise",uniqueConstraints=@UniqueConstraint(columnNames={"plan_id","position"})) @Getter @Setter @NoArgsConstructor
public class TrainingPlanExerciseEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="plan_id",nullable=false) private Long planId;
 @Column(name="position",nullable=false) private Integer position;
 @Column(name="name",nullable=false,length=100) private String name;
 @Column(name="mode",nullable=false,length=20) private String mode;
 @Column(name="sets") private Integer sets;
 @Column(name="reps") private Integer reps;
 @Column(name="weight_kg",precision=7,scale=2) private BigDecimal weightKg;
 @Column(name="minutes") private Integer minutes;
 @Column(name="note",length=300) private String note;
}
