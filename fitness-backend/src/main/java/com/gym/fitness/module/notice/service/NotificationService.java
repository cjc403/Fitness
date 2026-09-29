package com.gym.fitness.module.notice.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.notice.dto.NotificationDtos.NotificationView;
import com.gym.fitness.module.notice.entity.NotificationEntity;
import com.gym.fitness.module.notice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 站内通知。业务服务在关键变更后调用 {@code notifyMember / notifyMembers}，
 * 会员端与员工端共用同一套读写接口（按当前登录用户过滤）。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final MemberRepository members;

    @Transactional
    public void notifyUser(Long userId, Long memberId, Long venueId, String category,
                           String title, String body, String refType, Long refId) {
        if (userId == null) return;
        NotificationEntity item = new NotificationEntity();
        item.setRecipientUserId(userId);
        item.setMemberId(memberId);
        item.setVenueId(venueId);
        item.setCategory(category);
        item.setTitle(title);
        item.setBody(body == null ? null : body.length() > 1000 ? body.substring(0, 1000) : body);
        item.setRefType(refType);
        item.setRefId(refId);
        notifications.save(item);
    }

    /** 给单个会员发消息，自动解析他的登录账号；会员没有绑定账号时静默跳过。 */
    @Transactional
    public void notifyMember(Long memberId, Long venueId, String category, String title,
                             String body, String refType, Long refId) {
        if (memberId == null) return;
        members.findById(memberId).map(MemberEntity::getUserId)
                .ifPresent(userId -> notifyUser(userId, memberId, venueId, category, title, body, refType, refId));
    }

    /** 是否已给该会员发过某档提醒（用于定时提醒去重；会员没绑定账号时视为已发，避免反复重试）。 */
    @Transactional(readOnly = true)
    public boolean existsForMember(Long memberId, String refType, Long refId) {
        if (memberId == null) return true;
        return members.findById(memberId).map(MemberEntity::getUserId)
                .map(userId -> notifications.existsByRecipientUserIdAndRefTypeAndRefId(userId, refType, refId))
                .orElse(true);
    }

    /** 批量发消息（例如一条排课改了，通知所有已预约的会员）。 */
    @Transactional
    public void notifyMembers(Collection<Long> memberIds, Long venueId, String category, String title,
                              String body, String refType, Long refId) {
        if (memberIds == null || memberIds.isEmpty()) return;
        memberIds.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> notifyMember(id, venueId, category, title, body, refType, refId));
    }

    @Transactional(readOnly = true)
    public List<NotificationView> mine() {
        Long userId = requireUser();
        return notifications.findTop50ByRecipientUserIdOrderByIdDesc(userId).stream()
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByRecipientUserIdAndReadAtIsNull(requireUser());
    }

    @Transactional
    public void markRead(Long id) {
        Long userId = requireUser();
        NotificationEntity item = notifications.findByIdAndRecipientUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));
        if (item.getReadAt() == null) {
            item.setReadAt(LocalDateTime.now());
            notifications.save(item);
        }
    }

    @Transactional
    public int markAllRead() {
        Long userId = requireUser();
        List<NotificationEntity> unread = notifications.findByRecipientUserIdAndReadAtIsNullOrderByIdDesc(userId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(item -> item.setReadAt(now));
        notifications.saveAll(unread);
        return unread.size();
    }

    private Long requireUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return userId;
    }

    private NotificationView toView(NotificationEntity item) {
        return new NotificationView(item.getId(), item.getCategory(), item.getTitle(), item.getBody(),
                item.getRefType(), item.getRefId(), item.getCreatedAt(), item.getReadAt() != null);
    }
}
