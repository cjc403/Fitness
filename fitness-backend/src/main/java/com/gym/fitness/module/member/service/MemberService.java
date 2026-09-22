package com.gym.fitness.module.member.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.response.PageResult;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.member.dto.MemberRequest;
import com.gym.fitness.module.member.dto.MemberResponse;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final VenueRepository venueRepository;
    private final SysUserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResult<MemberResponse> list(String keyword, Integer page, Integer size, Long requestedVenueId) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 10 : Math.min(Math.max(size, 1), 100);
        Long venueId = resolveVenueId(requestedVenueId);
        Page<MemberEntity> result = memberRepository.search(
                venueId,
                keyword == null ? null : keyword.trim(),
                PageRequest.of(safePage - 1, safeSize));
        return PageResult.of(result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public MemberResponse get(Long id) {
        return toResponse(findAccessibleMember(id));
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        String memberNo = request.getMemberNo().trim().toUpperCase();
        if (memberRepository.existsByMemberNoIgnoreCase(memberNo)) {
            throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS, "会员编号已存在");
        }

        Long venueId = resolveVenueId(request.getVenueId());
        if (venueId == null || !venueRepository.existsById(venueId)) {
            throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        }

        MemberEntity member = new MemberEntity();
        member.setMemberNo(memberNo);
        member.setVenueId(venueId);
        member.setCreatedBy(SecurityUtil.getCurrentUserId());
        applyRequest(member, request);
        return toResponse(memberRepository.save(member));
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        MemberEntity member = findAccessibleMember(id);
        String memberNo = request.getMemberNo().trim().toUpperCase();
        if (memberRepository.existsByMemberNoIgnoreCaseAndIdNot(memberNo, id)) {
            throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS, "会员编号已存在");
        }

        member.setMemberNo(memberNo);
        applyRequest(member, request);
        if (isSuperAdmin() && request.getVenueId() != null) {
            if (!venueRepository.existsById(request.getVenueId())) {
                throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
            }
            member.setVenueId(request.getVenueId());
        }
        return toResponse(memberRepository.save(member));
    }

    @Transactional
    public MemberResponse pause(Long id) {
        MemberEntity member = findAccessibleMember(id);
        member.setStatus("PAUSED");
        return toResponse(memberRepository.save(member));
    }

    @Transactional
    public MemberResponse renew(Long id) {
        MemberEntity member = findAccessibleMember(id);
        member.setStatus("ACTIVE");
        return toResponse(memberRepository.save(member));
    }

    private void applyRequest(MemberEntity member, MemberRequest request) {
        member.setRealName(request.getRealName().trim());
        member.setPhone(request.getPhone().trim());
        member.setEmail(request.getEmail());
        member.setIdCard(request.getIdCard());
        member.setGender(request.getGender());
        member.setBirthday(request.getBirthday());
        member.setAddress(request.getAddress());
        member.setSalesUserId(request.getSalesUserId());
        member.setCoachId(request.getCoachId());
        member.setStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus().toUpperCase());
        member.setBlacklistFlag(Boolean.TRUE.equals(request.getBlacklistFlag()));
    }

    private MemberEntity findAccessibleMember(Long id) {
        if (isSuperAdmin()) {
            return memberRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        }
        Long venueId = requireCurrentVenueId();
        return memberRepository.findByIdAndVenueId(id, venueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Long resolveVenueId(Long requestedVenueId) {
        if (isSuperAdmin()) {
            return requestedVenueId;
        }
        return requireCurrentVenueId();
    }

    private boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(SecurityUtil.getCurrentUserRole());
    }

    private Long requireCurrentVenueId() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return venueId;
    }

    private MemberResponse toResponse(MemberEntity member) {
        var account = member.getUserId() == null ? null : userRepository.findById(member.getUserId()).orElse(null);
        return MemberResponse.builder()
                .id(member.getId())
                .memberNo(member.getMemberNo())
                .realName(member.getRealName())
                .phone(member.getPhone())
                .email(member.getEmail())
                .idCard(member.getIdCard())
                .gender(member.getGender())
                .birthday(member.getBirthday())
                .address(member.getAddress())
                .salesUserId(member.getSalesUserId())
                .coachId(member.getCoachId())
                .venueId(member.getVenueId())
                .status(member.getStatus())
                .blacklistFlag(member.getBlacklistFlag())
                .accountOpened(account != null)
                .accountUsername(account == null ? null : account.getUsername())
                .accountStatus(account == null ? null : account.getStatus())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }
}
