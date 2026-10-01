package com.gym.fitness.module.training.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseRepository;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.training.dto.TrainingDtos.*;
import com.gym.fitness.module.training.entity.*;
import com.gym.fitness.module.training.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
// 由 Lombok 生成构造注入：跨店分摊记账（见 CrossVenueSettlementService）
public class TrainingService {
    private final com.gym.fitness.module.commercial.service.CrossVenueSettlementService crossVenueSettlement;
    private final PrivateLessonRepository lessons;
    private final CommissionRuleRepository rules;
    private final CoachSettlementRepository settlements;
    private final MemberEntitlementRepository entitlements;
    private final MembershipProductRepository products;
    private final SalesOrderRepository orders;
    private final EntitlementOperationRepository operations;
    private final BookingRepository bookings;
    private final CourseScheduleRepository schedules;
    private final CourseRepository courses;
    private final MemberRepository members;
    private final SysUserRepository users;
    private final com.gym.fitness.common.util.VenueMutationLock venueLock;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.gym.fitness.module.finance.service.EntitlementValueService values;

    /** Lock order: venue -> schedule -> coach (when needed) -> entitlement. Venue lock serializes cross-package waitlist swaps. */
    @Transactional
    public void reserve(BookingEntity booking,CourseScheduleEntity schedule,Long requestedId) {
        values.assertBookableMember(booking.getMemberId());
        if (!"PRIVATE".equals(schedule.getCourse().getType())) return;
        var coach=users.findById(schedule.getCoachId()==null?-1L:schedule.getCoachId()).orElseThrow(()->failure("私教课程尚未分配教练"));
        if (!"COACH".equals(coach.getRole().getCode()) || !"ACTIVE".equals(coach.getStatus()) || !Objects.equals(coach.getVenueId(),schedule.getVenueId())) throw failure("私教课程的教练账号不可用");
        LocalDate date=schedule.getScheduledStartTime().toLocalDate();
        Long selected=requestedId;
        if(selected==null) selected=entitlements.findByMemberIdAndStatusOrderByValidUntilAsc(booking.getMemberId(),"ACTIVE").stream()
                .filter(e->validPackage(e,booking,schedule,date)).filter(e->available(e)>0).map(MemberEntitlementEntity::getId).findFirst().orElseThrow(()->failure("请先购买有效私教课包，再预约私教"));
        var credit=lockedCredit(selected);
        if(!validPackage(credit,booking,schedule,date) || available(credit)<1) throw failure("所选私教课包无可用课次，或有效期未覆盖上课日期");
        var lesson=lessons.findByBookingId(booking.getId()).orElseGet(PrivateLessonEntity::new);
        if(lesson.getId()!=null&&!"RELEASED".equals(lesson.getStatus())) throw failure("该预约已有消课记录，不能再次占用");
        // All business validation must precede mutation: waitlist eligibility may return false without rolling back a legitimate cancellation.
        BigDecimal price=unitPrice(credit);
        credit.setReservedUses(credit.getReservedUses()+1);
        lesson.setBookingId(booking.getId());lesson.setScheduleId(schedule.getId());lesson.setEntitlementId(credit.getId());lesson.setMemberId(booking.getMemberId());lesson.setCoachId(schedule.getCoachId());lesson.setVenueId(schedule.getVenueId());lesson.setStatus("RESERVED");lesson.setUnitPrice(price);lesson.setReservedAt(LocalDateTime.now());lesson.setSubmittedAt(null);lesson.setConfirmedAt(null);lesson.setReleasedAt(null);lesson.setNote(null);
        entitlements.save(credit);lessons.save(lesson);audit(lesson,"PT_RESERVE","预占一节私教课，预约ID="+booking.getId());
    }
    @Transactional
    public boolean tryReserveWaitlist(BookingEntity booking,CourseScheduleEntity schedule) {
        // A read-only eligibility rejection must not cross a transactional proxy as an exception:
        // that would mark the unrelated cancellation's outer transaction rollback-only.
        if(values.memberHasActiveRefund(booking.getMemberId()))return false;
        // Direct same-instance call, not a nested transactional proxy: an ineligible candidate never marks the caller rollback-only.
        try {reserve(booking,schedule,null);return true;}catch(BusinessException ineligible){return false;}
    }
    private boolean validPackage(MemberEntitlementEntity e,BookingEntity b,CourseScheduleEntity s,LocalDate date) {
        return Objects.equals(e.getMemberId(),b.getMemberId())&&Objects.equals(e.getVenueId(),s.getVenueId())&&"ACTIVE".equals(e.getStatus())&&e.getRemainingUses()!=null&&!date.isBefore(e.getValidFrom())&&!date.isAfter(e.getValidUntil())&&"PRIVATE_PACKAGE".equals(values.productType(e));
    }
    private int available(MemberEntitlementEntity e) {return e.getRemainingUses()==null?0:e.getRemainingUses()-e.getReservedUses();}
    private BigDecimal unitPrice(MemberEntitlementEntity e) {
        if(e.getLessonUnitPrice()==null) {
            var order=orders.findById(e.getSourceOrderId()).orElseThrow(()->failure("课包来源订单不存在"));
            if(e.getTotalUses()==null||e.getTotalUses()<1) throw failure("课包总次数无效");
            e.setLessonUnitPrice(order.getPaidAmount().divide(BigDecimal.valueOf(e.getTotalUses()),2,RoundingMode.HALF_UP));
        }
        return e.getLessonUnitPrice();
    }
    @Transactional
    public void release(Long bookingId,String reason) {
        lessons.findByBookingId(bookingId).ifPresent(l->{
            if("RELEASED".equals(l.getStatus()))return;
            if(!"RESERVED".equals(l.getStatus()))throw failure("授课已提交或签课已确认，请走消课纠错流程");
            var e=lockedCredit(l.getEntitlementId());
            if(e.getReservedUses()<1)throw failure("课包预占记录不一致，请联系管理员");
            e.setReservedUses(e.getReservedUses()-1);entitlements.save(e);l.setStatus("RELEASED");l.setReleasedAt(LocalDateTime.now());l.setNote(reason);lessons.save(l);audit(l,"PT_RELEASE",reason);
        });
    }
    @Transactional
    public LessonResponse submit(Long bookingId) {
        var booking=lockedBooking(bookingId);var l=requiredLesson(bookingId);assertScope(l);
        if(!SecurityUtil.hasRole("COACH")||!Objects.equals(SecurityUtil.getCurrentUserId(),l.getCoachId()))throw failure("仅授课教练本人可以提交授课");
        if("PENDING".equals(l.getStatus())||"CONFIRMED".equals(l.getStatus()))return response(l);
        if(!"RESERVED".equals(l.getStatus())||!"ATTENDED".equals(booking.getStatus())||booking.getSchedule().getScheduledEndTime().isAfter(LocalDateTime.now()))throw failure("课程结束并完成到课签到后，才能提交授课");
        lockedCredit(l.getEntitlementId());
        l.setStatus("PENDING");l.setSubmittedAt(LocalDateTime.now());lessons.save(l);audit(l,"PT_SUBMIT","教练提交授课，等待会员签课");return response(l);
    }
    @Transactional
    public LessonResponse confirm(Long bookingId) {
        lockedBooking(bookingId);var l=requiredLesson(bookingId);assertScope(l);
        if(!SecurityUtil.hasRole("MEMBER"))throw failure("仅会员本人可以签课");
        if("CONFIRMED".equals(l.getStatus()))return response(l);
        if(!"PENDING".equals(l.getStatus()))throw failure("请等待教练提交授课后再签课");
        coachLock(l.getCoachId());
        var member=members.findById(l.getMemberId()).orElseThrow();
        if(!"ACTIVE".equals(member.getStatus())||Boolean.TRUE.equals(member.getBlacklistFlag()))throw failure("会员状态异常，请联系店长核实签课");
        var e=lockedCredit(l.getEntitlementId());
        if(!Objects.equals(e.getMemberId(),l.getMemberId())||e.getReservedUses()<1||e.getRemainingUses()==null||e.getRemainingUses()<1||!List.of("ACTIVE","EXPIRED").contains(e.getStatus()))throw failure("课包权益异常，请联系店长");
        l.setUnitPrice(values.consume(e,"LESSON:"+l.getId(),unitPrice(e)));
        // 跨店服务分摊：卡是别店卖的、课在本店上的，记一笔供内部结算（只记账，不动钱）
        crossVenueSettlement.recordLesson(l,e,l.getUnitPrice());
        l.setCommissionRate(rules.findByVenueIdAndCoachId(l.getVenueId(),l.getCoachId()).map(CommissionRuleEntity::getRate).orElse(new BigDecimal("40.00")));
        l.setCommissionAmount(l.getUnitPrice().multiply(l.getCommissionRate()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP));
        l.setStatus("CONFIRMED");l.setConfirmedAt(LocalDateTime.now());
        e.setReservedUses(e.getReservedUses()-1);e.setRemainingUses(e.getRemainingUses()-1);if(e.getRemainingUses()==0)e.setStatus("USED_UP");
        entitlements.save(e);lessons.save(l);audit(l,"PT_CONSUME","会员确认签课，正式消课一节");return response(l);
    }
    @Transactional
    public LessonResponse reverse(Long bookingId,String reason) {
        managerOnly();lockedBooking(bookingId);var l=requiredLesson(bookingId);assertScope(l);coachLock(l.getCoachId());entityManager.refresh(l);
        if("REVERSED".equals(l.getStatus()))return response(l);
        if(l.getSettlementId()!=null)throw failure("消课已进入结算单，不允许直接冲正");
        if(!List.of("CONFIRMED","PENDING").contains(l.getStatus()))throw failure("仅已提交或已签课记录可以纠错");
        if(reason==null||reason.isBlank()||reason.length()>200)throw failure("纠错必须填写200字以内的原因");
        var e=lockedCredit(l.getEntitlementId());
        if("PENDING".equals(l.getStatus()))e.setReservedUses(e.getReservedUses()-1);else {
            values.reverse(e,"LESSON:"+l.getId(),"REVERSE_LESSON:"+l.getId());
            int remaining=e.getRemainingUses();
            e.setLessonUnitPrice(unitPrice(e).multiply(BigDecimal.valueOf(remaining)).add(l.getUnitPrice()).divide(BigDecimal.valueOf(remaining+1),2,RoundingMode.HALF_UP));
            e.setRemainingUses(remaining+1);if("USED_UP".equals(e.getStatus()))e.setStatus(e.getValidUntil().isBefore(LocalDate.now())?"EXPIRED":"ACTIVE");
        }
        l.setStatus("REVERSED");l.setReversedAt(LocalDateTime.now());l.setNote(reason);
        // 冲正后服务并未发生，把分摊记录一并撤销
        crossVenueSettlement.voidLesson(l.getId());entitlements.save(e);lessons.save(l);audit(l,"PT_REVERSE",reason);return response(l);
    }
    @Transactional(readOnly=true)
    public List<LessonResponse> listLessons() {
        List<PrivateLessonEntity> list;
        if(SecurityUtil.isSuperAdmin())list=lessons.findAllByOrderByReservedAtDesc();
        else if(SecurityUtil.hasRole("MEMBER"))list=lessons.findByMemberIdOrderByReservedAtDesc(currentMemberId());
        else if(SecurityUtil.hasRole("COACH"))list=lessons.findByCoachIdOrderByReservedAtDesc(SecurityUtil.getCurrentUserId()).stream().filter(l->Objects.equals(l.getVenueId(),SecurityUtil.getCurrentOrganizationId())).toList();
        else list=lessons.findByVenueIdOrderByReservedAtDesc(SecurityUtil.getCurrentOrganizationId());
        return list.stream().map(this::response).toList();
    }
    /**
     * 私教消课自检：这条链路要产生记录，必须同时具备"私教课程 → 已排课 → 排课绑定了可用教练账号 → 会员有可用课包"。
     * 缺任何一环，两端面板都会是空的，所以直接把缺哪一环算出来给界面展示。
     */
    @Transactional(readOnly=true)
    public PrivateReadiness readiness() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) throw failure("缺少门店上下文");
        long privateCourses = courses.findAllByVenueIdOrderByIdDesc(venueId).stream()
                .filter(c -> "PRIVATE".equals(c.getType())).filter(c -> !"INACTIVE".equals(c.getStatus())).count();
        LocalDateTime now = LocalDateTime.now();
        List<CourseScheduleEntity> upcoming = schedules
                .findByVenueIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(venueId, now, now.plusDays(365)).stream()
                .filter(s -> "PRIVATE".equals(s.getCourse().getType()))
                .filter(s -> "SCHEDULED".equals(s.getStatus()))
                .toList();
        long withCoach = upcoming.stream().filter(s -> s.getCoachId() != null).filter(s -> users.findById(s.getCoachId())
                .filter(u -> "COACH".equals(u.getRole().getCode()) && "ACTIVE".equals(u.getStatus())
                        && Objects.equals(u.getVenueId(), venueId)).isPresent()).count();
        long usable = entitlements.findByVenueIdOrderByIdDesc(venueId).stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .filter(e -> "PRIVATE_PACKAGE".equals(values.productType(e)))
                .filter(e -> available(e) > 0)
                .count();
        return new PrivateReadiness((int) privateCourses, upcoming.size(), (int) withCoach, (int) usable);
    }
    @Transactional(readOnly=true)
    public List<RuleResponse> listRules() {
        return users.findAll().stream().filter(u->"COACH".equals(u.getRole().getCode())).filter(u->SecurityUtil.isSuperAdmin()||Objects.equals(u.getVenueId(),SecurityUtil.getCurrentOrganizationId())).filter(u->!SecurityUtil.hasRole("COACH")||Objects.equals(u.getId(),SecurityUtil.getCurrentUserId())).map(u->{var r=rules.findByVenueIdAndCoachId(u.getVenueId(),u.getId());return new RuleResponse(u.getId(),u.getRealName(),u.getVenueId(),r.map(CommissionRuleEntity::getRate).orElse(new BigDecimal("40.00")),r.isEmpty(),r.map(CommissionRuleEntity::getUpdatedAt).orElse(null));}).toList();
    }
    @Transactional
    public RuleResponse updateRule(RuleRequest request) {
        managerOnly();var coach=coachLock(request.getCoachId());assertVenue(coach.getVenueId());
        var r=rules.findByVenueIdAndCoachId(coach.getVenueId(),coach.getId()).orElseGet(CommissionRuleEntity::new);r.setCoachId(coach.getId());r.setVenueId(coach.getVenueId());r.setRate(request.getRate());r.setUpdatedBy(SecurityUtil.getCurrentUserId());r.setUpdatedAt(LocalDateTime.now());rules.save(r);return new RuleResponse(coach.getId(),coach.getRealName(),coach.getVenueId(),r.getRate(),false,r.getUpdatedAt());
    }
    @Transactional
    public SettlementResponse generate(SettlementRequest request) {
        managerOnly();var coach=coachLock(request.getCoachId());assertVenue(coach.getVenueId());
        YearMonth month;try{month=YearMonth.parse(request.getMonth());}catch(Exception ex){throw failure("结算月份格式必须为YYYY-MM");}
        if(month.isAfter(YearMonth.now()))throw failure("不能生成未来月份结算单");
        var existing=settlements.findByVenueIdAndCoachIdAndMonth(coach.getVenueId(),coach.getId(),month.toString());
        if(existing.isPresent()){var sheet=existing.get();if("DRAFT".equals(sheet.getStatus()))appendUnsettled(sheet);return settlementResponse(sheet);}
        var rows=lessons.findByCoachIdAndVenueIdAndStatusAndSettlementIdIsNullAndConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(coach.getId(),coach.getVenueId(),"CONFIRMED",month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay());
        if(rows.isEmpty())throw failure("该月份没有待结算的已签课记录");
        var sheet=new CoachSettlementEntity();sheet.setCoachId(coach.getId());sheet.setVenueId(coach.getVenueId());sheet.setMonth(month.toString());sheet.setLessonCount(rows.size());sheet.setServiceAmount(rows.stream().map(PrivateLessonEntity::getUnitPrice).reduce(BigDecimal.ZERO,BigDecimal::add));sheet.setCommissionAmount(rows.stream().map(PrivateLessonEntity::getCommissionAmount).reduce(BigDecimal.ZERO,BigDecimal::add));sheet.setStatus("DRAFT");sheet.setCreatedBy(SecurityUtil.getCurrentUserId());sheet.setCreatedAt(LocalDateTime.now());settlements.saveAndFlush(sheet);
        rows.forEach(l->{l.setSettlementId(sheet.getId());lessons.save(l);audit(l,"PT_SETTLEMENT","纳入月结单ID="+sheet.getId());});return settlementResponse(sheet);
    }
    @Transactional
    public SettlementResponse approve(Long id) {
        managerOnly();var sheet=settlements.findById(id).orElseThrow(()->failure("结算单不存在"));assertVenue(sheet.getVenueId());coachLock(sheet.getCoachId());
        entityManager.refresh(sheet);
        if(!YearMonth.parse(sheet.getMonth()).isBefore(YearMonth.now()))throw failure("当前月份尚未结束，可更新草稿，次月才能审核月结");
        if(Objects.equals(sheet.getCoachId(),SecurityUtil.getCurrentUserId()))throw failure("不能审核本人的结算单");
        if(!"APPROVED".equals(sheet.getStatus())){appendUnsettled(sheet);sheet.setStatus("APPROVED");sheet.setApprovedBy(SecurityUtil.getCurrentUserId());sheet.setApprovedAt(LocalDateTime.now());settlements.save(sheet);lessons.findBySettlementId(id).forEach(l->audit(l,"PT_APPROVE","审核月结单ID="+id));}return settlementResponse(sheet);
    }
    private void appendUnsettled(CoachSettlementEntity sheet) {
        var month=YearMonth.parse(sheet.getMonth());
        var extra=lessons.findByCoachIdAndVenueIdAndStatusAndSettlementIdIsNullAndConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(sheet.getCoachId(),sheet.getVenueId(),"CONFIRMED",month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay());
        for(var l:extra){l.setSettlementId(sheet.getId());lessons.save(l);audit(l,"PT_SETTLEMENT","纳入月结单ID="+sheet.getId());}
        if(!extra.isEmpty()){sheet.setLessonCount(sheet.getLessonCount()+extra.size());sheet.setServiceAmount(sheet.getServiceAmount().add(extra.stream().map(PrivateLessonEntity::getUnitPrice).reduce(BigDecimal.ZERO,BigDecimal::add)));sheet.setCommissionAmount(sheet.getCommissionAmount().add(extra.stream().map(PrivateLessonEntity::getCommissionAmount).reduce(BigDecimal.ZERO,BigDecimal::add)));settlements.save(sheet);}
    }
    @Transactional(readOnly=true)
    public List<SettlementResponse> listSettlements() {
        if(SecurityUtil.hasRole("MEMBER"))throw failure("会员不可访问员工结算信息");
        var rows=SecurityUtil.isSuperAdmin()?settlements.findAllByOrderByCreatedAtDesc():SecurityUtil.hasRole("COACH")?settlements.findByCoachIdOrderByCreatedAtDesc(SecurityUtil.getCurrentUserId()):settlements.findByVenueIdOrderByCreatedAtDesc(SecurityUtil.getCurrentOrganizationId());
        return rows.stream().filter(s->SecurityUtil.isSuperAdmin()||Objects.equals(s.getVenueId(),SecurityUtil.getCurrentOrganizationId())).map(this::settlementResponse).toList();
    }
    private BookingEntity lockedBooking(Long id) {
        Long venueId=bookings.findVenueId(id).orElseThrow(()->failure("预约不存在"));assertVenue(venueId);venueLock.acquire(venueId);
        Long sid=bookings.findScheduleId(id).orElseThrow(()->failure("预约不存在"));schedules.findByIdForUpdate(sid).orElseThrow(()->failure("排课不存在"));return bookings.findById(id).orElseThrow();
    }
    private PrivateLessonEntity requiredLesson(Long bookingId){return lessons.findByBookingId(bookingId).orElseThrow(()->failure("该预约是历史未关联记录，不会自动消课"));}
    private MemberEntitlementEntity lockedCredit(Long id){var e=entitlements.findByIdForUpdate(id).orElseThrow(()->failure("课包不存在"));entityManager.refresh(e);values.assertMutable(e);return e;}
    private SysUserEntity coachLock(Long id){var u=users.findByIdForUpdate(id).orElseThrow(()->failure("教练不存在"));if(!"COACH".equals(u.getRole().getCode()))throw failure("所选账号不是教练");return u;}
    private Long currentMemberId(){return members.findByUserId(SecurityUtil.getCurrentUserId()).orElseThrow(()->failure("账号未绑定会员")).getId();}
    private void assertScope(PrivateLessonEntity l){assertVenue(l.getVenueId());if(SecurityUtil.hasRole("MEMBER")&&!Objects.equals(currentMemberId(),l.getMemberId()))throw failure("消课记录不存在");if(SecurityUtil.hasRole("COACH")&&!Objects.equals(SecurityUtil.getCurrentUserId(),l.getCoachId()))throw failure("消课记录不存在");}
    private void assertVenue(Long id){if(!SecurityUtil.isSuperAdmin()&&!Objects.equals(id,SecurityUtil.getCurrentOrganizationId()))throw failure("记录不存在或不属于本门店");}
    private void managerOnly(){if(!SecurityUtil.isSuperAdmin()&&!SecurityUtil.hasRole("MANAGER"))throw failure("仅总部管理员和店长可办理此操作");}
    private BusinessException failure(String message){return new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,message);}
    private void audit(PrivateLessonEntity l,String type,String note){var a=new EntitlementOperationEntity();a.setEntitlementId(l.getEntitlementId());a.setOperationType(type);a.setAfterStatus(l.getStatus());a.setAmount(l.getUnitPrice());a.setOperatorId(SecurityUtil.getCurrentUserId());a.setNote("预约ID="+l.getBookingId()+"；"+note);operations.save(a);}
    private LessonResponse response(PrivateLessonEntity l){return toResponses(List.of(l)).get(0);}
    /**
     * 批量装配签课记录。此前逐行 findById（排课/会员/教练各一次）在列表页是最典型的 N+1，
     * 这里改成"先批量捞、再用 Map 组装"，查询次数从 3N 降到常数级。
     */
    private List<LessonResponse> toResponses(List<PrivateLessonEntity> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, CourseScheduleEntity> scheduleMap = schedules
                .findAllById(rows.stream().map(PrivateLessonEntity::getScheduleId).distinct().toList()).stream()
                .collect(Collectors.toMap(CourseScheduleEntity::getId, s -> s));
        Map<Long, MemberEntity> memberMap = members
                .findAllById(rows.stream().map(PrivateLessonEntity::getMemberId).distinct().toList()).stream()
                .collect(Collectors.toMap(MemberEntity::getId, m -> m));
        List<Long> coachIds = rows.stream().map(PrivateLessonEntity::getCoachId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUserEntity> coachMap = coachIds.isEmpty() ? Map.of()
                : users.findAllById(coachIds).stream().collect(Collectors.toMap(SysUserEntity::getId, u -> u));
        boolean memberView = SecurityUtil.hasRole("MEMBER");
        return rows.stream().map(l -> {
            CourseScheduleEntity s = scheduleMap.get(l.getScheduleId());
            MemberEntity m = memberMap.get(l.getMemberId());
            SysUserEntity u = coachMap.get(l.getCoachId());
            return new LessonResponse(l.getId(), l.getBookingId(), l.getEntitlementId(), l.getMemberId(),
                    m == null ? null : m.getRealName(), l.getCoachId(), u == null ? null : u.getRealName(),
                    l.getVenueId(), s == null ? null : s.getCourse().getName(),
                    s == null ? null : s.getScheduledStartTime(), s == null ? null : s.getScheduledEndTime(),
                    l.getStatus(), l.getUnitPrice(), memberView ? null : l.getCommissionRate(),
                    memberView ? null : l.getCommissionAmount(), memberView ? null : l.getSettlementId(),
                    l.getSubmittedAt(), l.getConfirmedAt(), l.getNote());
        }).toList();
    }
    private SettlementResponse settlementResponse(CoachSettlementEntity s){return new SettlementResponse(s.getId(),s.getCoachId(),users.findById(s.getCoachId()).orElseThrow().getRealName(),s.getVenueId(),s.getMonth(),s.getLessonCount(),s.getServiceAmount(),s.getCommissionAmount(),s.getStatus(),s.getCreatedAt(),s.getApprovedAt());}
}
