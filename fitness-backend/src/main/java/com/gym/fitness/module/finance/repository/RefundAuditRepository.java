package com.gym.fitness.module.finance.repository;
import com.gym.fitness.module.finance.entity.RefundAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RefundAuditRepository extends JpaRepository<RefundAuditEntity,Long> {
    List<RefundAuditEntity> findByRefundIdOrderByIdAsc(Long id);
    List<RefundAuditEntity> findByRefundIdInOrderByRefundIdAscIdAsc(List<Long> refundIds);
}

