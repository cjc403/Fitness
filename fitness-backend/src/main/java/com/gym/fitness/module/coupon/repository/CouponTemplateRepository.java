package com.gym.fitness.module.coupon.repository;

import com.gym.fitness.module.coupon.entity.CouponTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplateEntity, Long> {
    List<CouponTemplateEntity> findAllByOrderByIdDesc();
    List<CouponTemplateEntity> findByVenueIdOrderByIdDesc(Long venueId);
}
