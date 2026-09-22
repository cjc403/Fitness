package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {
    List<PaymentTransactionEntity> findAllByOrderByOccurredAtDesc();
    List<PaymentTransactionEntity> findByOrderIdInOrderByOccurredAtDesc(List<Long> orderIds);
    @Query("select p from PaymentTransactionEntity p, SalesOrderEntity o " +
            "where p.orderId=o.id and p.status='SUCCESS' and p.transactionType in ('PAYMENT','REFUND') " +
            "and p.occurredAt>=:from and p.occurredAt<:until " +
            "and (:venueId is null or o.venueId=:venueId) order by p.occurredAt asc, p.id asc")
    List<PaymentTransactionEntity> findSuccessfulCashTransactions(@Param("from") LocalDateTime from,
                                                                    @Param("until") LocalDateTime until,
                                                                    @Param("venueId") Long venueId);
    @Query("select p from PaymentTransactionEntity p, SalesOrderEntity o " +
            "where p.orderId=o.id and p.status='SUCCESS' and p.transactionType in ('PAYMENT','REFUND') " +
            "and p.occurredAt>=:from and p.occurredAt<:until " +
            "and (:venueId is null or o.venueId=:venueId) order by p.occurredAt asc, p.id asc")
    List<PaymentTransactionEntity> findSuccessfulCashTransactions(@Param("from") LocalDateTime from,
                                                                    @Param("until") LocalDateTime until,
                                                                    @Param("venueId") Long venueId, Pageable pageable);
}
