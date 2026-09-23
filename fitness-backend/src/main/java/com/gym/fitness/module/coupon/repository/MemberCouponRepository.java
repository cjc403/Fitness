package com.gym.fitness.module.coupon.repository;

import com.gym.fitness.module.coupon.entity.MemberCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface MemberCouponRepository extends JpaRepository<MemberCouponEntity, Long> {
    List<MemberCouponEntity> findByMemberIdOrderByIdDesc(Long memberId);
    long countByCouponTemplateId(Long couponTemplateId);
    long countByVenueIdAndStatus(Long venueId, String status);

    /** 下单用券时加行锁读取，防止同一张券被两笔订单同时抵扣。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MemberCouponEntity c where c.id = :id")
    Optional<MemberCouponEntity> findByIdForUpdate(@Param("id") Long id);
}
