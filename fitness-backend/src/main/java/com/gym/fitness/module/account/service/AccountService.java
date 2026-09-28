package com.gym.fitness.module.account.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.response.PageResult;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.account.dto.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.entity.RoleEntity;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.RoleRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.security.RolePermissionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final List<String> STAFF_ROLES = List.of("SUPER_ADMIN", "MANAGER", "COACH", "RECEPTION", "SALES");
    private static final List<String> MANAGER_CREATABLE_ROLES = List.of("COACH", "RECEPTION");

    private final SysUserRepository users;
    private final MemberRepository members;
    private final RoleRepository roles;
    private final VenueRepository venues;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResult<EmployeeAccountResponse> listEmployees(String keyword, String roleCode, String status,
                                                              Long requestedVenueId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null ? 10 : Math.min(Math.max(size, 1), 100);
        Long venueId = isSuperAdmin() ? requestedVenueId : requireCurrentVenue();
        Page<SysUserEntity> result = users.searchEmployees(venueId, normalizeOptional(roleCode),
                normalizeOptional(status), normalizeOptional(keyword), PageRequest.of(safePage - 1, safeSize));
        return PageResult.of(result.getContent().stream().map(this::toEmployee).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public EmployeeAccountResponse createEmployee(EmployeeAccountRequest request) {
        String roleCode = request.getRoleCode().toUpperCase(Locale.ROOT);
        validateRoleCanBeCreated(roleCode);
        validateNewAccount(request.getUsername(), request.getPhone(), request.getEmail());
        ensureStrongPassword(request.getPassword());

        Long venueId = isSuperAdmin() ? request.getVenueId() : requireCurrentVenue();
        VenueEntity venue = requireActiveVenue(venueId);
        RoleEntity role = roles.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在"));

        SysUserEntity user = new SysUserEntity();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName().trim());
        user.setPhone(trimToNull(request.getPhone()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setRoleId(role.getId());
        user.setRole(role);
        user.setVenueId(venue.getId());
        user.setVenue(venue);
        user.setCoach("COACH".equals(roleCode));
        user.setStatus("ACTIVE");
        user.setMustChangePassword(true);
        user.setCreatedBy(SecurityUtil.getCurrentUserId());
        return toEmployee(users.save(user));
    }

    @Transactional
    public EmployeeAccountResponse updateEmployeeStatus(Long id, AccountStatusRequest request) {
        SysUserEntity user = findManageableEmployee(id);
        if (id.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "不能停用自己的账号");
        }
        user.setStatus(request.getStatus());
        return toEmployee(users.save(user));
    }

    @Transactional
    public EmployeeAccountResponse updateEmployee(Long id, EmployeeAccountUpdateRequest request) {
        SysUserEntity user = findManageableEmployee(id);
        String roleCode = request.getRoleCode().toUpperCase(Locale.ROOT);
        validateRoleCanBeCreated(roleCode);
        Long venueId = isSuperAdmin() ? request.getVenueId() : requireCurrentVenue();
        VenueEntity venue = requireActiveVenue(venueId);
        RoleEntity role = roles.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "角色不存在"));
        if (id.equals(SecurityUtil.getCurrentUserId())
                && (!roleCode.equals(user.getRole().getCode()) || !venueId.equals(user.getVenueId()))) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "不能修改自己的角色或所属场馆");
        }
        String phone = trimToNull(request.getPhone());
        String email = trimToNull(request.getEmail());
        if (phone != null && users.existsByPhoneAndIdNot(phone, id)) throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        if (email != null && users.existsByEmailIgnoreCaseAndIdNot(email, id)) throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        user.setRealName(request.getRealName().trim());
        user.setPhone(phone); user.setEmail(email); user.setRoleId(role.getId()); user.setRole(role);
        user.setVenueId(venueId); user.setVenue(venue); user.setCoach("COACH".equals(roleCode));
        return toEmployee(users.save(user));
    }

    @Transactional
    public EmployeeAccountResponse resetEmployeePassword(Long id, PasswordResetRequest request) {
        SysUserEntity user = findManageableEmployee(id);
        ensureStrongPassword(request.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(true);
        return toEmployee(users.save(user));
    }

    @Transactional
    public MemberAccountResponse registerMember(MemberRegistrationRequest request) {
        validateNewAccount(request.getUsername(), request.getPhone(), request.getEmail());
        if (members.existsByPhone(request.getPhone().trim())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        ensureStrongPassword(request.getPassword());
        VenueEntity venue = venues.findByCodeIgnoreCase(request.getVenueCode().trim())
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND, "请选择正常营业的场馆"));

        SysUserEntity user = createMemberUser(request.getUsername(), request.getPassword(), request.getRealName(),
                request.getPhone(), request.getEmail(), venue.getId(), false, null);
        MemberEntity member = new MemberEntity();
        member.setUserId(user.getId());
        member.setMemberNo("TMP-" + UUID.randomUUID());
        member.setRealName(request.getRealName().trim());
        member.setPhone(request.getPhone().trim());
        member.setEmail(trimToNull(request.getEmail()));
        member.setVenueId(venue.getId());
        member.setStatus("ACTIVE");
        member.setBlacklistFlag(false);
        member.setCreatedBy(user.getId());
        member = members.save(member);
        member.setMemberNo(generateMemberNo(member.getId()));
        return toMemberAccount(members.save(member), user);
    }

    @Transactional
    public MemberAccountResponse openMemberAccount(Long memberId, MemberAccountRequest request) {
        MemberEntity member = findAccessibleMember(memberId);
        if (member.getUserId() != null) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "该会员已经开通登录账号");
        }
        validateNewAccount(request.getUsername(), member.getPhone(), member.getEmail());
        ensureStrongPassword(request.getPassword());
        SysUserEntity user = createMemberUser(request.getUsername(), request.getPassword(), member.getRealName(),
                member.getPhone(), member.getEmail(), member.getVenueId(), true, SecurityUtil.getCurrentUserId());
        member.setUserId(user.getId());
        members.save(member);
        return toMemberAccount(member, user);
    }

    @Transactional
    public MemberAccountResponse resetMemberPassword(Long memberId, PasswordResetRequest request) {
        MemberEntity member = findAccessibleMember(memberId);
        SysUserEntity user = requireMemberUser(member);
        ensureStrongPassword(request.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(true);
        return toMemberAccount(member, users.save(user));
    }

    @Transactional
    public MemberAccountResponse updateMemberAccountStatus(Long memberId, AccountStatusRequest request) {
        MemberEntity member = findAccessibleMember(memberId);
        SysUserEntity user = requireMemberUser(member);
        user.setStatus(request.getStatus());
        return toMemberAccount(member, users.save(user));
    }

    @Transactional(readOnly = true)
    public List<VenueOption> publicVenues() {
        return venues.findByStatusOrderByNameAsc("ACTIVE").stream()
                .map(item -> new VenueOption(item.getCode(), item.getName()))
                .toList();
    }

    private SysUserEntity createMemberUser(String username, String password, String realName, String phone,
                                            String email, Long venueId, boolean mustChange, Long createdBy) {
        RoleEntity role = roles.findByCode("MEMBER")
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "会员角色未初始化"));
        SysUserEntity user = new SysUserEntity();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRealName(realName.trim());
        user.setPhone(trimToNull(phone));
        user.setEmail(trimToNull(email));
        user.setRoleId(role.getId());
        user.setVenueId(venueId);
        user.setCoach(false);
        user.setStatus("ACTIVE");
        user.setMustChangePassword(mustChange);
        user.setCreatedBy(createdBy);
        return users.save(user);
    }

    /** 可创建的角色选项（前端下拉由后端给出，避免两边各写一份白名单而脱节）。 */
    public record RoleOption(String code, String name) {
    }

    public List<RoleOption> creatableRoles() {
        String current = com.gym.fitness.common.util.SecurityUtil.getCurrentUserRole();
        List<String> codes = "SUPER_ADMIN".equals(current) ? STAFF_ROLES
                : "MANAGER".equals(current) ? MANAGER_CREATABLE_ROLES : List.of();
        return codes.stream()
                .map(code -> new RoleOption(code, com.gym.fitness.security.RolePermissionCatalog.roleName(code)))
                .toList();
    }

    private void validateRoleCanBeCreated(String roleCode) {
        if (!STAFF_ROLES.contains(roleCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的员工角色");
        }
        String currentRole = SecurityUtil.getCurrentUserRole();
        if ("SUPER_ADMIN".equals(currentRole)) return;
        if (!"MANAGER".equals(currentRole) || !MANAGER_CREATABLE_ROLES.contains(roleCode)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS, "店长只能创建本店教练和前台账号");
        }
    }

    private SysUserEntity findManageableEmployee(Long id) {
        SysUserEntity target = users.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String targetRole = target.getRole().getCode();
        if ("MEMBER".equals(targetRole)) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (isSuperAdmin()) return target;
        if (!"MANAGER".equals(SecurityUtil.getCurrentUserRole())
                || !requireCurrentVenue().equals(target.getVenueId())
                || !MANAGER_CREATABLE_ROLES.contains(targetRole)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSIONS);
        }
        return target;
    }

    private MemberEntity findAccessibleMember(Long memberId) {
        if (isSuperAdmin()) {
            return members.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        }
        return members.findByIdAndVenueId(memberId, requireCurrentVenue())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private SysUserEntity requireMemberUser(MemberEntity member) {
        if (member.getUserId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "该会员尚未开通登录账号");
        }
        return users.findById(member.getUserId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateNewAccount(String username, String phone, String email) {
        if (users.existsByUsernameIgnoreCase(username.trim())) throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        if (phone != null && !phone.isBlank() && users.existsByPhone(phone.trim())) throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        if (email != null && !email.isBlank() && users.existsByEmailIgnoreCase(email.trim())) throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    private void ensureStrongPassword(String password) {
        if (password == null || password.length() < 8
                || !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
                || !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码至少8位，且需包含大小写字母、数字和特殊字符");
        }
    }

    private VenueEntity requireActiveVenue(Long venueId) {
        if (venueId == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择所属场馆");
        return venues.findById(venueId).filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND));
    }

    private Long requireCurrentVenue() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return venueId;
    }

    private boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(SecurityUtil.getCurrentUserRole());
    }

    private String generateMemberNo(Long id) {
        return "FC" + LocalDate.now().getYear() + String.format("%06d", id);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private EmployeeAccountResponse toEmployee(SysUserEntity user) {
        return EmployeeAccountResponse.builder()
                .id(user.getId()).username(user.getUsername()).realName(user.getRealName())
                .phone(user.getPhone()).email(user.getEmail())
                .roleCode(user.getRole().getCode()).roleName(RolePermissionCatalog.roleName(user.getRole().getCode()))
                .venueId(user.getVenueId()).venueName(user.getVenue().getName()).status(user.getStatus())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .lastLoginAt(user.getLastLoginAt()).createdAt(user.getCreatedAt()).build();
    }

    private MemberAccountResponse toMemberAccount(MemberEntity member, SysUserEntity user) {
        return MemberAccountResponse.builder().userId(user.getId()).memberId(member.getId())
                .memberNo(member.getMemberNo()).username(user.getUsername()).accountStatus(user.getStatus())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword())).build();
    }

    public record VenueOption(String code, String name) {}
}
