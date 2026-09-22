package com.gym.fitness.module.notice.repository;

import com.gym.fitness.module.notice.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findTop50ByRecipientUserIdOrderByIdDesc(Long recipientUserId);
    List<NotificationEntity> findByRecipientUserIdAndReadAtIsNullOrderByIdDesc(Long recipientUserId);
    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);
    Optional<NotificationEntity> findByIdAndRecipientUserId(Long id, Long recipientUserId);
}
