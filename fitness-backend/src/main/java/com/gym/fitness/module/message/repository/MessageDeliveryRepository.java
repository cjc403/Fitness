package com.gym.fitness.module.message.repository;

import com.gym.fitness.module.message.entity.MessageDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageDeliveryRepository extends JpaRepository<MessageDeliveryEntity, Long> {
    List<MessageDeliveryEntity> findTop100ByOrderByIdDesc();

    /** 本门店的投递记录（门店隔离）。 */
    List<MessageDeliveryEntity> findTop100ByVenueIdOrderByIdDesc(Long venueId);

    List<MessageDeliveryEntity> findTop50ByMemberIdOrderByIdDesc(Long memberId);

    long countByChannelAndStatus(String channel, String status);
}
