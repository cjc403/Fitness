package com.gym.fitness.module.organization.repository;

import com.gym.fitness.module.organization.entity.VenueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<VenueEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from VenueEntity v where v.id=:id")
    Optional<VenueEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<VenueEntity> findAllByOrderByIdAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    Optional<VenueEntity> findByCodeIgnoreCase(String code);

    List<VenueEntity> findByStatusOrderByNameAsc(String status);
}
