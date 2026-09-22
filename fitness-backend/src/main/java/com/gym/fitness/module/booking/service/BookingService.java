package com.gym.fitness.module.booking.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.booking.dto.BookingRequest;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;
    private final CourseScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;
    private final com.gym.fitness.module.training.service.TrainingService training;
    private final com.gym.fitness.common.util.VenueMutationLock venueLock;
    private final com.gym.fitness.module.finance.service.EntitlementValueService values;

    @Transactional(readOnly = true)
    public List<BookingEntity> list() {
        return SecurityUtil.isSuperAdmin()
                ? bookingRepository.findAllByOrderByBookedAtDesc()
                : bookingRepository.findBySchedule_VenueIdOrderByBookedAtDesc(requireCurrentVenueId());
    }

    @Transactional
    public BookingEntity create(BookingRequest request) {
        MemberEntity member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!"ACTIVE".equals(member.getStatus()) || Boolean.TRUE.equals(member.getBlacklistFlag()))
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该会员当前不可预约");
        venueLock.acquire(member.getVenueId());
        values.assertBookableMember(member.getId());
        CourseScheduleEntity schedule = scheduleRepository.findByIdForUpdate(request.getScheduleId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        assertAccessibleVenue(member.getVenueId());
        assertAccessibleVenue(schedule.getVenueId());
        if (!member.getVenueId().equals(schedule.getVenueId())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "会员与排课必须属于同一场馆");
        }
        if (!"SCHEDULED".equals(schedule.getStatus()) || schedule.getScheduledStartTime().isBefore(LocalDateTime.now()))
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_BOOKABLE);
        var existing = bookingRepository.findByScheduleIdAndMemberId(schedule.getId(), member.getId());
        if (existing.isPresent() && !"CANCELLED".equals(existing.get().getStatus()))
            throw new BusinessException(ErrorCode.BOOKING_ALREADY_EXISTS);
        long enrolled = Optional.ofNullable(schedule.getEnrolledCount()).orElse(0);
        if (enrolled >= schedule.getMaxStudents()) throw new BusinessException(ErrorCode.SCHEDULE_FULL);
        BookingEntity entity = existing.orElseGet(BookingEntity::new);
        entity.setScheduleId(schedule.getId());
        entity.setMemberId(member.getId());
        entity.setStatus("BOOKED");
        entity.setBookedAt(LocalDateTime.now());
        entity.setCancelledAt(null);
        entity.setCancelReason(null);
        schedule.setEnrolledCount((int) enrolled + 1);
        scheduleRepository.save(schedule);
        BookingEntity saved = bookingRepository.saveAndFlush(entity);
        training.reserve(saved, schedule, request.getEntitlementId());
        return saved;
    }

    @Transactional
    public BookingEntity cancel(Long id, String reason) {
        BookingEntity entity = find(id);
        if (entity.getSchedule().getScheduledStartTime().isBefore(LocalDateTime.now().plusHours(2))) {
            if (!SecurityUtil.isSuperAdmin() && !SecurityUtil.hasRole("MANAGER"))
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "临时取消请由店长办理");
            if (reason == null || reason.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "临时取消必须填写原因");
        }
        return cancelEntity(entity, reason);
    }

    @Transactional
    public BookingEntity createForCurrentMember(Long scheduleId) {
        return createForCurrentMember(scheduleId, null);
    }

    @Transactional
    public BookingEntity createForCurrentMember(Long scheduleId, Long entitlementId) {
        MemberEntity member = currentMember();
        BookingRequest request = new BookingRequest();
        request.setMemberId(member.getId());
        request.setScheduleId(scheduleId);
        request.setEntitlementId(entitlementId);
        return create(request);
    }

    @Transactional
    public BookingEntity cancelForCurrentMember(Long id) {
        MemberEntity member = currentMember();
        BookingEntity entity = find(id);
        if (!member.getId().equals(entity.getMemberId())) throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        if (!"BOOKED".equals(entity.getStatus()) && !"WAITLISTED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只有已预约或候补中的记录可以取消");
        }
        if (entity.getSchedule().getScheduledStartTime().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BusinessException(ErrorCode.BOOKING_CANCEL_TOO_LATE, "开课前 2 小时内不能自助取消，请联系前台");
        }
        return cancelEntity(entity, "会员自助取消");
    }

    @Transactional
    public BookingEntity joinWaitlistForCurrentMember(Long scheduleId) {
        MemberEntity member = currentMember();
        venueLock.acquire(member.getVenueId());
        values.assertBookableMember(member.getId());
        CourseScheduleEntity schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        if (!member.getVenueId().equals(schedule.getVenueId()) || !"SCHEDULED".equals(schedule.getStatus())
                || schedule.getScheduledStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_BOOKABLE);
        }
        var existing = bookingRepository.findByScheduleIdAndMemberId(scheduleId, member.getId());
        if (existing.isPresent() && !"CANCELLED".equals(existing.get().getStatus())) {
            throw new BusinessException(ErrorCode.BOOKING_ALREADY_EXISTS);
        }
        long enrolled = Optional.ofNullable(schedule.getEnrolledCount()).orElse(0);
        if (enrolled < schedule.getMaxStudents()) return createForCurrentMember(scheduleId);
        BookingEntity booking = existing.orElseGet(BookingEntity::new);
        booking.setScheduleId(scheduleId);
        booking.setMemberId(member.getId());
        booking.setStatus("WAITLISTED");
        booking.setBookedAt(LocalDateTime.now());
        booking.setWaitlistPosition((int) bookingRepository.countByScheduleIdAndStatus(scheduleId, "WAITLISTED") + 1);
        booking.setCancelledAt(null);
        booking.setCancelReason(null);
        return bookingRepository.save(booking);
    }

    @Transactional
    public BookingEntity requestLeaveForCurrentMember(Long id, String reason) {
        MemberEntity member = currentMember();
        BookingEntity entity = find(id);
        if (!member.getId().equals(entity.getMemberId())) throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        if (!"BOOKED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只有待参加预约可以请假");
        }
        if (entity.getSchedule().getScheduledStartTime().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BusinessException(ErrorCode.BOOKING_CANCEL_TOO_LATE, "开课前 2 小时内不能自助请假");
        }
        entity.setStatus("LEAVE");
        entity.setLeaveRequestedAt(LocalDateTime.now());
        entity.setCancelReason(reason == null || reason.isBlank() ? "会员请假" : reason);
        training.release(id, entity.getCancelReason());
        CourseScheduleEntity schedule = entity.getSchedule();
        schedule.setEnrolledCount(Math.max(0, schedule.getEnrolledCount() - 1));
        scheduleRepository.save(schedule);
        BookingEntity saved = bookingRepository.save(entity);
        promoteWaitlist(schedule);
        return saved;
    }

    private BookingEntity cancelEntity(BookingEntity entity, String reason) {
        if ("CANCELLED".equals(entity.getStatus())) return entity;
        if (!List.of("BOOKED", "WAITLISTED").contains(entity.getStatus()))
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "已签到、已请假或爽约的记录不能直接取消");
        training.release(entity.getId(), reason == null || reason.isBlank() ? "取消预约" : reason);
        String oldStatus = entity.getStatus();
        entity.setStatus("CANCELLED");
        entity.setCancelledAt(LocalDateTime.now());
        entity.setCancelReason(reason == null || reason.isBlank() ? "后台取消" : reason);
        CourseScheduleEntity schedule = entity.getSchedule();
        if ("BOOKED".equals(oldStatus)) {
            schedule.setEnrolledCount(Math.max(0, schedule.getEnrolledCount() - 1));
            scheduleRepository.save(schedule);
        }
        BookingEntity saved = bookingRepository.save(entity);
        if ("BOOKED".equals(oldStatus)) promoteWaitlist(schedule);
        return saved;
    }

    @Transactional
    public BookingEntity checkIn(Long id) {
        BookingEntity entity = find(id);
        if (SecurityUtil.hasRole("COACH")
                && !SecurityUtil.getCurrentUserId().equals(entity.getSchedule().getCoachId())) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (!"BOOKED".equals(entity.getStatus()))
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只有已预约记录可以签到");
        entity.setStatus("ATTENDED");
        entity.setCheckedInAt(LocalDateTime.now());
        return bookingRepository.save(entity);
    }

    @Transactional
    public BookingEntity markNoShow(Long id) {
        BookingEntity entity = find(id);
        if (SecurityUtil.hasRole("COACH")
                && !SecurityUtil.getCurrentUserId().equals(entity.getSchedule().getCoachId())) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        if (!"BOOKED".equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只有待参加预约可以标记爽约");
        }
        if (entity.getSchedule().getScheduledStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "课程开始前不能标记爽约");
        }
        entity.setStatus("NO_SHOW");
        training.release(id, "爽约记录，释放预占，不自动扣课");
        entity.setNoShowPenaltyPoints(Math.max(1, entity.getNoShowPenaltyPoints() == null ? 1 : entity.getNoShowPenaltyPoints() + 1));
        return bookingRepository.save(entity);
    }

    private void promoteWaitlist(CourseScheduleEntity schedule) {
        if (!"SCHEDULED".equals(schedule.getStatus()) || schedule.getScheduledStartTime().isBefore(LocalDateTime.now())) return;
        while (schedule.getEnrolledCount() < schedule.getMaxStudents()) {
            var candidate=bookingRepository.findFirstByScheduleIdAndStatusOrderByWaitlistPositionAscBookedAtAsc(schedule.getId(), "WAITLISTED");
            if(candidate.isEmpty())return;
            var next=candidate.get();
            var member=memberRepository.findById(next.getMemberId()).orElseThrow();
            if(!"ACTIVE".equals(member.getStatus())||Boolean.TRUE.equals(member.getBlacklistFlag())||!java.util.Objects.equals(member.getVenueId(),schedule.getVenueId())||!training.tryReserveWaitlist(next,schedule)) {
                next.setStatus("CANCELLED");next.setCancelledAt(LocalDateTime.now());next.setCancelReason("候补失效：会员状态或私教课包不可用，请联系前台");next.setWaitlistPosition(null);bookingRepository.saveAndFlush(next);continue;
            }
                    next.setStatus("BOOKED");
                    next.setWaitlistPosition(null);
                    bookingRepository.save(next);
                    schedule.setEnrolledCount(schedule.getEnrolledCount() + 1);
                    scheduleRepository.save(schedule);
        }
    }

    private BookingEntity find(Long id) {
        Long venueId=bookingRepository.findVenueId(id).orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
        assertAccessibleVenue(venueId);
        venueLock.acquire(venueId);
        Long scheduleId = bookingRepository.findScheduleId(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
        scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        BookingEntity booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
        assertAccessibleVenue(booking.getSchedule().getVenueId());
        return booking;
    }

    private void assertAccessibleVenue(Long venueId) {
        if (!SecurityUtil.isSuperAdmin() && !venueId.equals(requireCurrentVenueId())) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
    }

    private Long requireCurrentVenueId() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return venueId;
    }

    private MemberEntity currentMember() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return memberRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "当前账号尚未绑定会员档案"));
    }
}
