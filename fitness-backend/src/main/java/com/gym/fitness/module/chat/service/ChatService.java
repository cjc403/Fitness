package com.gym.fitness.module.chat.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.organization.entity.RoleEntity;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.organization.repository.RoleRepository;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.chat.dto.ChatDtos.*;
import com.gym.fitness.module.chat.entity.DirectMessageEntity;
import com.gym.fitness.module.chat.repository.DirectMessageRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 站内私信：各角色账号之间真实互发，双方都能在自己的消息界面看到。
 *
 * <p><b>谁能发给谁</b>（服务层强校验，前端只做展示）：<br>
 * ① 总部管理员：可发给任何人；<br>
 * ② 员工（店长/教练/前台/会籍顾问）：可发给<b>本门店</b>的员工与会员；<br>
 * ③ 会员：只能发给<b>本门店</b>的员工（会员之间不互发，避免打扰与隐私问题）；<br>
 * ④ 不能发给自己，也不能发给已停用的账号。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatService {
    private final DirectMessageRepository messages;
    private final SysUserRepository users;
    private final MemberRepository members;
    private final RoleRepository roles;
    private final VenueRepository venues;

    // ---------------------------------------------------------------- 联系人

    @Transactional(readOnly = true)
    public ContactGroups contacts() {
        SysUserEntity me = me();
        Long myVenue = me.getVenueId();
        Map<Long, String> venueNames = venues.findAll().stream()
                .collect(Collectors.toMap(VenueEntity::getId, venueName -> venueName.getName(), (a, b) -> a));
        Map<Long, Long> unreadBySender = unreadByCounterpart(me.getId());

        boolean iAmMember = isMember(me);
        List<SysUserEntity> visible = SecurityUtil.isSuperAdmin()
                ? users.findAll()
                : users.findAll().stream().filter(u -> Objects.equals(u.getVenueId(), myVenue)).toList();

        List<Contact> staff = visible.stream()
                .filter(u -> !Objects.equals(u.getId(), me.getId()))
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .filter(u -> !isMember(u))
                .map(u -> toContact(u, venueNames, false, unreadBySender))
                .sorted(Comparator.comparing(Contact::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<Contact> memberContacts = iAmMember ? List.of() : membersInScope(myVenue).stream()
                .filter(m -> m.getUserId() != null && !Objects.equals(m.getUserId(), me.getId()))
                .map(m -> {
                    SysUserEntity u = users.findById(m.getUserId()).orElse(null);
                    if (u == null || !"ACTIVE".equals(u.getStatus())) return null;
                    return toContact(u, venueNames, true, unreadBySender);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Contact::name, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new ContactGroups(staff, memberContacts);
    }

    // ---------------------------------------------------------------- 会话

    @Transactional(readOnly = true)
    public List<Conversation> conversations() {
        SysUserEntity me = me();
        List<DirectMessageEntity> all = messages
                .findTop500BySenderUserIdOrRecipientUserIdOrderByIdDesc(me.getId(), me.getId());
        Map<Long, DirectMessageEntity> latest = new LinkedHashMap<>();
        for (DirectMessageEntity m : all) {
            Long other = Objects.equals(m.getSenderUserId(), me.getId()) ? m.getRecipientUserId() : m.getSenderUserId();
            latest.putIfAbsent(other, m);
        }
        List<Conversation> out = new ArrayList<>();
        for (Map.Entry<Long, DirectMessageEntity> e : latest.entrySet()) {
            DirectMessageEntity last = e.getValue();
            SysUserEntity other = users.findById(e.getKey()).orElse(null);
            long unread = messages.countByRecipientUserIdAndSenderUserIdAndReadAtIsNull(me.getId(), e.getKey());
            out.add(new Conversation(e.getKey(),
                    other == null ? last.getSenderName() : other.getRealName(),
                    other == null ? null : roleName(other),
                    other == null ? null : roleCode(other),
                    last.getContent(), last.getCreatedAt(), unread));
        }
        out.sort(Comparator.comparing(Conversation::lastAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Long me = SecurityUtil.getCurrentUserId();
        return new Summary(messages.countByRecipientUserIdAndReadAtIsNull(me), conversations().size());
    }

    // ---------------------------------------------------------------- 对话与发送

    /** 打开与某人的对话：顺带把对方发来的未读标为已读。 */
    @Transactional
    public ChatThread thread(Long otherUserId) {
        SysUserEntity me = me();
        SysUserEntity other = users.findById(otherUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "对方账号不存在"));
        List<DirectMessageEntity> history = messages.findThread(me.getId(), otherUserId);
        // A participant keeps access to their own historical conversation after
        // a counterpart transfers/leaves. An arbitrary ID without a history
        // must satisfy the same contact boundary as starting a conversation.
        if (history.isEmpty()) assertCanMessage(me, other);
        messages.markThreadRead(me.getId(), otherUserId, LocalDateTime.now());
        List<Message> list = history.stream()
                .map(m -> new Message(m.getId(), Objects.equals(m.getSenderUserId(), me.getId()),
                        m.getSenderName(), m.getContent(), m.getCreatedAt()))
                .toList();
        return new ChatThread(other.getId(), other.getRealName(), roleName(other), roleCode(other), list);
    }

    @Transactional
    public Message send(SendRequest request) {
        SysUserEntity me = me();
        SysUserEntity other = users.findById(request.getRecipientUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收件人不存在"));
        assertCanMessage(me, other);
        String content = request.getContent().trim();
        if (content.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "消息内容不能为空");

        DirectMessageEntity entity = new DirectMessageEntity();
        entity.setSenderUserId(me.getId());
        entity.setRecipientUserId(other.getId());
        entity.setSenderName(displayName(me));
        entity.setRecipientName(displayName(other));
        entity.setVenueId(me.getVenueId());
        entity.setContent(content);
        DirectMessageEntity saved = messages.save(entity);
        return new Message(saved.getId(), true, saved.getSenderName(), saved.getContent(), saved.getCreatedAt());
    }

    // ---------------------------------------------------------------- 内部

    private void assertCanMessage(SysUserEntity me, SysUserEntity other) {
        if (Objects.equals(me.getId(), other.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能给自己发消息");
        }
        if (!"ACTIVE".equals(other.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "对方账号已停用，无法接收消息");
        }
        boolean iAmMember = isMember(me);
        boolean otherIsMember = isMember(other);
        if (iAmMember && otherIsMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "会员之间暂不支持互发消息，可联系门店工作人员");
        }
        if (!SecurityUtil.isSuperAdmin() && !Objects.equals(me.getVenueId(), other.getVenueId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能给本门店的人发消息");
        }
    }

    private List<MemberEntity> membersInScope(Long myVenue) {
        return SecurityUtil.isSuperAdmin() ? members.findAll() : members.findByVenueIdOrderByIdDesc(myVenue);
    }

    private Map<Long, Long> unreadByCounterpart(Long meId) {
        Map<Long, Long> out = new HashMap<>();
        for (DirectMessageEntity m : messages.findTop500BySenderUserIdOrRecipientUserIdOrderByIdDesc(meId, meId)) {
            if (m.getReadAt() != null || !Objects.equals(m.getRecipientUserId(), meId)) continue;
            out.merge(m.getSenderUserId(), 1L, Long::sum);
        }
        return out;
    }

    private Contact toContact(SysUserEntity u, Map<Long, String> venueNames, boolean member,
                              Map<Long, Long> unreadBySender) {
        return new Contact(u.getId(), displayName(u), roleCode(u), roleName(u),
                venueNames.get(u.getVenueId()), member, unreadBySender.getOrDefault(u.getId(), 0L));
    }

    private String displayName(SysUserEntity u) {
        return u.getRealName() == null || u.getRealName().isBlank() ? u.getUsername() : u.getRealName();
    }

    private boolean isMember(SysUserEntity u) {
        return "MEMBER".equals(roleCode(u));
    }

    private RoleEntity roleOf(SysUserEntity u) {
        return u.getRoleId() == null ? null : roles.findById(u.getRoleId()).orElse(null);
    }

    private String roleCode(SysUserEntity u) {
        RoleEntity role = roleOf(u);
        return role == null ? null : role.getCode();
    }

    private String roleName(SysUserEntity u) {
        RoleEntity role = roleOf(u);
        return role == null ? null : (role.getName() == null ? role.getCode() : role.getName());
    }

    private SysUserEntity me() {
        Long id = SecurityUtil.getCurrentUserId();
        return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "登录状态无效"));
    }
}
