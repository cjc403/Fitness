package com.gym.fitness.module.message.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.message.channel.MessageChannel;
import com.gym.fitness.module.message.dto.MessageDtos.*;
import com.gym.fitness.module.message.entity.MessageDeliveryEntity;
import com.gym.fitness.module.message.repository.MessageDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 消息投递：把一次通知按通道发出去，并记录投递结果。
 *
 * <p><b>隐私纪律</b>：库里只存脱敏后的收件人（138****5678 / a***@qq.com），
 * 完整手机号与邮箱只在内存里传给通道实现，绝不落投递表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDispatchService {
    private final List<MessageChannel> channels;
    private final MessageDeliveryRepository deliveries;
    private final MemberRepository members;

    @Transactional(readOnly = true)
    public List<ChannelView> channelViews() {
        return channels.stream()
                .map(c -> new ChannelView(c.code(), c.name(), c.enabled(), c.mock(), c.provider(),
                        c.enabled() ? (c.mock() ? "模拟通道：只记录投递，不会真的发出" : "已接入真实供应商")
                                : "已关闭：投递会被跳过"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> recent() {
        return deliveries.findTop100ByOrderByIdDesc().stream().map(this::toView).toList();
    }

    /** 手动给某个会员发消息（演示/通知用）。 */
    @Transactional
    public List<DeliveryView> sendToMember(SendRequest request) {
        MemberEntity member = members.findById(request.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return dispatch(member, request.getCategory().trim().toUpperCase(), request.getSubject().trim(),
                request.getContent().trim(), request.getChannels());
    }

    /** 按通道发一条消息；channels 为空表示所有已启用通道。 */
    @Transactional
    public List<DeliveryView> dispatch(MemberEntity member, String category, String subject, String content,
                                       Collection<String> requestedChannels) {
        MessageChannel.Recipient recipient = new MessageChannel.Recipient(member.getUserId(), member.getId(),
                member.getPhone(), member.getEmail(), member.getRealName());
        Set<String> wanted = requestedChannels == null || requestedChannels.isEmpty()
                ? null
                : requestedChannels.stream().filter(Objects::nonNull).map(s -> s.trim().toUpperCase()).collect(java.util.stream.Collectors.toSet());
        List<DeliveryView> out = new ArrayList<>();
        for (MessageChannel channel : channels) {
            if (wanted != null && !wanted.contains(channel.code())) continue;
            if (!channel.enabled()) {
                out.add(toView(record(member, channel, category, subject, content, "SKIPPED", null, "通道未启用")));
                continue;
            }
            String recipientMasked = channel.recipientOf(recipient);
            if (recipientMasked == null) {
                out.add(toView(record(member, channel, category, subject, content, "SKIPPED", null,
                        "会员未填写" + channel.name() + "收件信息")));
                continue;
            }
            try {
                var result = channel.send(recipient, subject, content);
                out.add(toView(record(member, channel, category, subject, content,
                        result.success() ? "SENT" : "FAILED", result.providerMessageId(), result.error())));
            } catch (Exception e) {
                log.warn("消息投递失败 channel={} member={}", channel.code(), member.getId(), e);
                out.add(toView(record(member, channel, category, subject, content, "FAILED", null, e.getMessage())));
            }
        }
        return out;
    }

    /** 记录一条投递；收件人只存脱敏值。 */
    private MessageDeliveryEntity record(MemberEntity member, MessageChannel channel, String category,
                                         String subject, String content, String status, String providerMessageId,
                                         String error) {
        MessageDeliveryEntity entity = new MessageDeliveryEntity();
        entity.setChannel(channel.code());
        entity.setCategory(category);
        String masked = channel.recipientOf(new MessageChannel.Recipient(member.getUserId(), member.getId(),
                member.getPhone(), member.getEmail(), member.getRealName()));
        entity.setRecipientMasked(masked == null ? "—" : masked);
        entity.setRecipientUserId(member.getUserId());
        entity.setMemberId(member.getId());
        entity.setVenueId(member.getVenueId());
        entity.setSubject(subject);
        entity.setContent(content.length() > 500 ? content.substring(0, 500) : content);
        entity.setStatus(status);
        entity.setProvider("SKIPPED".equals(status) ? null : channel.provider());
        entity.setProviderMessageId(providerMessageId);
        entity.setError(error == null || error.isBlank() ? null
                : (error.length() > 300 ? error.substring(0, 300) : error));
        return deliveries.save(entity);
    }

    private DeliveryView toView(MessageDeliveryEntity e) {
        return new DeliveryView(e.getId(), e.getChannel(), e.getCategory(), e.getRecipientMasked(), e.getMemberId(),
                e.getVenueId(), e.getSubject(), e.getContent(), e.getStatus(), e.getProvider(),
                e.getProviderMessageId(), e.getError(), e.getCreatedAt());
    }

    /** 当前登录人的门店（供越权判断留痕）。 */
    public Long currentVenue() {
        return SecurityUtil.getCurrentOrganizationId();
    }
}
