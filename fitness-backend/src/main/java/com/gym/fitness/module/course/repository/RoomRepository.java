package com.gym.fitness.module.course.repository;

import com.gym.fitness.module.course.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    List<RoomEntity> findAllByOrderByIdAsc();
    List<RoomEntity> findByVenueIdOrderByIdAsc(Long venueId);
    boolean existsByVenueIdAndName(Long venueId, String name);
    boolean existsByVenueIdAndNameAndIdNot(Long venueId, String name, Long id);
}
