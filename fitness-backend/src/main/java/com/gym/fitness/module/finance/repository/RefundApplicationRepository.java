package com.gym.fitness.module.finance.repository;
import com.gym.fitness.module.finance.entity.RefundApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RefundApplicationRepository extends JpaRepository<RefundApplicationEntity,Long> {
    Optional<RefundApplicationEntity> findByRequestedByAndIdempotencyKey(Long user,String key);
    Optional<RefundApplicationEntity> findByChannelAndExternalReference(String channel,String reference);
    List<RefundApplicationEntity> findAllByOrderByIdDesc();
    List<RefundApplicationEntity> findByVenueIdOrderByIdDesc(Long id);
    List<RefundApplicationEntity> findByMemberIdOrderByIdDesc(Long id);
}

