package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.ProductVenueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVenueRepository extends JpaRepository<ProductVenueEntity, Long> {
    List<ProductVenueEntity> findByProductIdOrderByVenueIdAsc(Long productId);

    void deleteByProductId(Long productId);

    long countByVenueId(Long venueId);
}
