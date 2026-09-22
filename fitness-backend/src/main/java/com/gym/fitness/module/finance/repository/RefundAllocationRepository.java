package com.gym.fitness.module.finance.repository;
import com.gym.fitness.module.finance.entity.RefundAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RefundAllocationRepository extends JpaRepository<RefundAllocationEntity,Long> {
    List<RefundAllocationEntity> findByRefundIdOrderByOrderIdAsc(Long id);
    List<RefundAllocationEntity> findByPaymentTransactionIdIn(List<Long> paymentTransactionIds);
    List<RefundAllocationEntity> findByRefundIdIn(List<Long> refundIds);
    List<RefundAllocationEntity> findByRefundIdInOrderByRefundIdAscOrderIdAsc(List<Long> refundIds);
}
