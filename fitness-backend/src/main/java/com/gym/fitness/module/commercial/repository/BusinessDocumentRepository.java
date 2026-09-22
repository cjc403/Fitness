package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.BusinessDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessDocumentRepository extends JpaRepository<BusinessDocumentEntity, Long> {
    List<BusinessDocumentEntity> findAllByOrderByCreatedAtDesc();
    List<BusinessDocumentEntity> findByOrderIdInOrderByCreatedAtDesc(List<Long> orderIds);
    boolean existsByOrderIdAndDocumentType(Long orderId, String documentType);
}
