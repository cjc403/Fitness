package com.gym.fitness.module.member.repository;

import com.gym.fitness.module.member.entity.BodyMeasurementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyMeasurementRepository extends JpaRepository<BodyMeasurementEntity, Long> {
    List<BodyMeasurementEntity> findByMemberIdOrderByMeasuredOnDesc(Long memberId);

    List<BodyMeasurementEntity> findByMemberIdOrderByMeasuredOnAsc(Long memberId);

    Optional<BodyMeasurementEntity> findByMemberIdAndMeasuredOn(Long memberId, LocalDate measuredOn);

    long countByMemberId(Long memberId);
}
