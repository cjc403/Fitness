package com.gym.fitness.module.course.repository;

import com.gym.fitness.module.course.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseRepository extends JpaRepository<CourseEntity, Long> {
    List<CourseEntity> findAllByOrderByIdDesc();
    List<CourseEntity> findAllByVenueIdOrderByIdDesc(Long venueId);
    long countByVenueId(Long venueId);
}
