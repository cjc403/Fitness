package com.gym.fitness.module.chat.repository;

import com.gym.fitness.module.chat.entity.DirectMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessageEntity, Long> {

    /** 与我有关的最近消息（收发都算），用于拼会话列表。 */
    List<DirectMessageEntity> findTop500BySenderUserIdOrRecipientUserIdOrderByIdDesc(Long sender, Long recipient);

    /**
     * 我与某个人的来往消息（升序，用于对话窗口）。
     *
     * <p>必须写成显式 @Query：派生方法名里属性重复出现（…AndRecipientUserIdOrRecipientUserIdAnd…）
     * 会被 Spring Data 解析成错误的 SQL，实测导致"发得出去、读不出对话"。</p>
     */
    @Query("select m from DirectMessageEntity m where "
            + "(m.senderUserId = :a and m.recipientUserId = :b) or (m.senderUserId = :b and m.recipientUserId = :a) "
            + "order by m.id asc")
    List<DirectMessageEntity> findThread(@Param("a") Long a, @Param("b") Long b);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    long countByRecipientUserIdAndSenderUserIdAndReadAtIsNull(Long recipientUserId, Long senderUserId);

    long countBySenderUserIdAndRecipientUserIdAndReadAtIsNull(Long senderUserId, Long recipientUserId);

    /** 打开对话即把对方发给我的未读标为已读。 */
    @Modifying
    @Query("update DirectMessageEntity m set m.readAt = :now where m.recipientUserId = :me "
            + "and m.senderUserId = :other and m.readAt is null")
    int markThreadRead(@Param("me") Long me, @Param("other") Long other, @Param("now") LocalDateTime now);
}
