package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.CrossVenueServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CrossVenueServiceRepository extends JpaRepository<CrossVenueServiceEntity, Long> {
    Optional<CrossVenueServiceEntity> findByRefTypeAndRefId(String refType, Long refId);

    List<CrossVenueServiceEntity> findByOccurredAtBetweenOrderByIdAsc(LocalDateTime from, LocalDateTime to);

    void deleteByRefTypeAndRefId(String refType, Long refId);
}
