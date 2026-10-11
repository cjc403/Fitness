package com.gym.fitness.module.cashshift.repository;
import com.gym.fitness.module.cashshift.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CashShiftPaymentRepository extends JpaRepository<CashShiftPaymentEntity,Long> {
org.springframework.data.domain.Page<CashShiftPaymentEntity> findByShiftIdOrderByPaymentTransactionIdAsc(Long id,org.springframework.data.domain.Pageable page); List<CashShiftPaymentEntity> findByShiftIdOrderByPaymentTransactionIdAsc(Long id);
long countByShiftIdAndCarriedOverTrue(Long id);
long countByShiftId(Long id);
}
