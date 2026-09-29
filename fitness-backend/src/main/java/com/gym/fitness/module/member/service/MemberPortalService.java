package com.gym.fitness.module.member.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.booking.service.BookingService;
import com.gym.fitness.module.commercial.dto.CommercialDtos;
import com.gym.fitness.module.commercial.service.CommercialService;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.dto.MemberBookingResponse;
import com.gym.fitness.module.member.dto.MemberPortalProfileResponse;
import com.gym.fitness.module.member.dto.MemberScheduleResponse;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberPortalService {
    private final MemberRepository memberRepository;
    private final CourseScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final com.gym.fitness.module.commercial.service.CrossVenueService crossVenue;
    private final BookingService bookingService;
    private final CommercialService commercialService;

    @Transactional(readOnly = true)
    public MemberPortalProfileResponse profile() {
        MemberEntity member = currentMember();
        return MemberPortalProfileResponse.builder()
                .id(member.getId()).memberNo(member.getMemberNo()).realName(member.getRealName())
                .phone(member.getPhone()).email(member.getEmail()).venueId(member.getVenueId())
                .status(member.getStatus()).blacklistFlag(member.getBlacklistFlag()).build();
    }

    /**
     * 会员可自助购买的商品（与员工端同一套在售商品，含跨门店展示）。金额与支付渠道由服务端决定，见 CommercialService。
     */
    @Transactional(readOnly = true)
    public List<CommercialDtos.MemberProductView> products() {
        return commercialService.listMemberShopProducts();
    }

    /**
     * 会员自助购买：不接入真实支付，金额取商品标价、渠道固定为自助；幂等键由会员端生成。
     */
    @Transactional
    public CommercialDtos.OrderResponse purchase(Long productId, String idempotencyKey, Long memberCouponId) {
        return commercialService.createSelfServiceSale(currentMember().getId(), productId, idempotencyKey, memberCouponId);
    }

    @Transactional(readOnly = true)
    public List<MemberScheduleResponse> schedules() {
        MemberEntity member = currentMember();
        LocalDateTime now = LocalDateTime.now();
        List<BookingEntity> ownBookings = bookingRepository.findByMemberIdOrderByBookedAtDesc(member.getId());
        Map<Long, String> bookingStatuses = ownBookings.stream()
                .filter(booking -> !"CANCELLED".equals(booking.getStatus()))
                .collect(Collectors.toMap(BookingEntity::getScheduleId, BookingEntity::getStatus, (first, ignored) -> first));
        // 跨店通用：除了本人所属门店，还包括持有有效权益覆盖的门店
        java.util.Set<Long> venueIds = crossVenue.accessibleVenues(member.getId(), member.getVenueId());
        return venueIds.stream()
                .flatMap(venueId -> scheduleRepository
                        .findByVenueIdAndStatusAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
                                venueId, "SCHEDULED", now, now.plusDays(30)).stream())
                .sorted(java.util.Comparator.comparing(CourseScheduleEntity::getScheduledStartTime))
                .map(schedule -> toSchedule(schedule, bookingStatuses.get(schedule.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemberBookingResponse> bookings() {
        MemberEntity member = currentMember();
        return bookingRepository.findByMemberIdOrderByBookedAtDesc(member.getId()).stream()
                .map(this::toBooking)
                .toList();
    }

    @Transactional
    public void createBooking(Long scheduleId) {
        bookingService.createForCurrentMember(scheduleId);
    }

    @Transactional
    public void createBooking(Long scheduleId, Long entitlementId) {
        bookingService.createForCurrentMember(scheduleId, entitlementId);
    }

    @Transactional
    public void cancelBooking(Long bookingId) {
        bookingService.cancelForCurrentMember(bookingId);
    }

    @Transactional
    public void joinWaitlist(Long scheduleId) { bookingService.joinWaitlistForCurrentMember(scheduleId); }

    @Transactional
    public void requestLeave(Long bookingId, String reason) { bookingService.requestLeaveForCurrentMember(bookingId, reason); }

    private MemberEntity currentMember() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return memberRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "当前账号尚未绑定会员档案"));
    }

    private MemberScheduleResponse toSchedule(CourseScheduleEntity schedule, String bookingStatus) {
        return MemberScheduleResponse.builder()
                .scheduleId(schedule.getId()).courseId(schedule.getCourseId())
                .courseName(schedule.getCourse().getName()).courseType(schedule.getCourse().getType())
                .description(schedule.getCourse().getDescription())
                .durationMinutes(schedule.getCourse().getDurationMinutes())
                .intensity(schedule.getCourse().getIntensity()).fee(schedule.getCourse().getFee())
                .coachName(schedule.getCoachName()).startTime(schedule.getScheduledStartTime())
                .endTime(schedule.getScheduledEndTime()).maxStudents(schedule.getMaxStudents())
                .enrolledCount(schedule.getEnrolledCount()).booked(bookingStatus != null)
                .bookingStatus(bookingStatus)
                .full(schedule.getEnrolledCount() >= schedule.getMaxStudents()).build();
    }

    private MemberBookingResponse toBooking(BookingEntity booking) {
        LocalDateTime start = booking.getSchedule().getScheduledStartTime();
        boolean canCancel = List.of("BOOKED", "WAITLISTED").contains(booking.getStatus())
                && start.isAfter(LocalDateTime.now().plusHours(2));
        return MemberBookingResponse.builder()
                .id(booking.getId()).scheduleId(booking.getScheduleId())
                .courseName(booking.getSchedule().getCourse().getName())
                .coachName(booking.getSchedule().getCoachName())
                .startTime(start).endTime(booking.getSchedule().getScheduledEndTime())
                .status(booking.getStatus()).bookedAt(booking.getBookedAt())
                .checkedInAt(booking.getCheckedInAt()).cancelledAt(booking.getCancelledAt())
                .cancelReason(booking.getCancelReason()).waitlistPosition(booking.getWaitlistPosition())
                .leaveRequestedAt(booking.getLeaveRequestedAt())
                .noShowPenaltyPoints(booking.getNoShowPenaltyPoints())
                .cancellationAllowed(canCancel).build();
    }
}
