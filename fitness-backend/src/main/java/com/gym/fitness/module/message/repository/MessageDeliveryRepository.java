package com.gym.fitness.module.message.repository;

import com.gym.fitness.module.message.entity.MessageDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageDeliveryRepository extends JpaRepository<MessageDeliveryEntity, Long> {
    List<MessageDeliveryEntity> findTop100ByOrderByIdDesc();

    List<MessageDeliveryEntity> findTop50ByMemberIdOrderByIdDesc(Long memberId);

    long countByChannelAndStatus(String channel, String status);
}
