package com.gym.fitness.module.report.service;

import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.repository.CourseRepository;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final MemberRepository members;
    private final VenueRepository venues;
    private final CourseRepository courses;
    private final CourseScheduleRepository schedules;
    private final BookingRepository bookings;

    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();
        Long venueId = SecurityUtil.isSuperAdmin() ? null : requireCurrentVenueId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", venueId == null ? "ALL_VENUES" : "CURRENT_VENUE");
        result.put("totalMembers", venueId == null ? members.count() : members.countByVenueId(venueId));
        result.put("activeMembers", memberStatusCount(venueId, "ACTIVE"));
        result.put("pausedMembers", memberStatusCount(venueId, "PAUSED"));
        result.put("blacklistedMembers", venueId == null
                ? members.countByBlacklistFlagTrue() : members.countByVenueIdAndBlacklistFlagTrue(venueId));
        result.put("venues", venueId == null ? venues.count() : 1L);
        result.put("courses", venueId == null ? courses.count() : courses.countByVenueId(venueId));
        result.put("todaySchedules", scheduleCount(venueId, today));
        long booked = bookingStatusCount(venueId, "BOOKED");
        long attended = bookingStatusCount(venueId, "ATTENDED");
        long cancelled = bookingStatusCount(venueId, "CANCELLED");
        long noShow = bookingStatusCount(venueId, "NO_SHOW");
        long waitlisted = bookingStatusCount(venueId, "WAITLISTED");
        long leave = bookingStatusCount(venueId, "LEAVE");
        result.put("booked", booked);
        result.put("attended", attended);
        result.put("cancelled", cancelled);
        result.put("noShow", noShow);
        result.put("waitlisted", waitlisted);
        result.put("leave", leave);
        long completedReservations = attended + noShow;
        result.put("attendanceRate", completedReservations == 0 ? 0
                : Math.round(attended * 1000.0 / completedReservations) / 10.0);

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("schedules", scheduleCount(venueId, date));
            trend.add(point);
        }
        result.put("weeklyTrend", trend);
        result.put("generatedAt", java.time.LocalDateTime.now());
        return result;
    }

    private long memberStatusCount(Long venueId, String status) {
        return venueId == null ? members.countByStatus(status) : members.countByVenueIdAndStatus(venueId, status);
    }

    private long bookingStatusCount(Long venueId, String status) {
        return venueId == null ? bookings.countByStatus(status) : bookings.countBySchedule_VenueIdAndStatus(venueId, status);
    }

    private long scheduleCount(Long venueId, LocalDate date) {
        return venueId == null
                ? schedules.countByScheduledStartTimeBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                : schedules.countByVenueIdAndScheduledStartTimeBetween(
                        venueId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    private Long requireCurrentVenueId() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return venueId;
    }
}
