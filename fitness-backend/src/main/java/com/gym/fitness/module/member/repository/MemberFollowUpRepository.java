package com.gym.fitness.module.member.repository;

import com.gym.fitness.module.member.entity.MemberFollowUpEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MemberFollowUpRepository extends JpaRepository<MemberFollowUpEntity, Long> {
    List<MemberFollowUpEntity> findByMemberIdOrderByIdDesc(Long memberId);

    List<MemberFollowUpEntity> findByMemberIdInOrderByIdDesc(Collection<Long> memberIds);

    List<MemberFollowUpEntity> findByStaffUserIdOrderByIdDesc(Long staffUserId);

    long countByStaffUserIdAndCreatedAtAfter(Long staffUserId, LocalDateTime after);

    /** 某顾问名下、约定时间已到的待跟进记录。 */
    List<MemberFollowUpEntity> findByStaffUserIdAndNextFollowAtLessThanEqualOrderByNextFollowAtAsc(
            Long staffUserId, LocalDateTime at);
}
