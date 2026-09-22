package com.gym.fitness.module.course.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.dto.CoachWorkbenchResponse;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoachWorkbenchService {
    private final CourseScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final com.gym.fitness.module.training.repository.PrivateLessonRepository privateLessons;

    @Transactional(readOnly = true)
    public CoachWorkbenchResponse workbench() {
        Long coachId = SecurityUtil.getCurrentUserId();
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (coachId == null || venueId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        LocalDateTime now = LocalDateTime.now();
        List<CourseScheduleEntity> schedules = scheduleRepository
                .findByCoachIdAndVenueIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
                        coachId, venueId, now.minusDays(7), now.plusDays(30));
        List<Long> scheduleIds = schedules.stream().map(CourseScheduleEntity::getId).toList();
        List<BookingEntity> bookings = scheduleIds.isEmpty()
                ? Collections.emptyList()
                : bookingRepository.findByScheduleIdInOrderByBookedAtAsc(scheduleIds);
        Map<Long, List<BookingEntity>> bookingsBySchedule = bookings.stream()
                .collect(Collectors.groupingBy(BookingEntity::getScheduleId));

        List<CoachWorkbenchResponse.CoachSchedule> scheduleItems = schedules.stream()
                .map(schedule -> toSchedule(schedule,
                        bookingsBySchedule.getOrDefault(schedule.getId(), Collections.emptyList())))
                .toList();
        LocalDate today = LocalDate.now();
        long todaySchedules = schedules.stream()
                .filter(schedule -> schedule.getScheduledStartTime().toLocalDate().equals(today)).count();
        long upcomingSchedules = schedules.stream()
                .filter(schedule -> "SCHEDULED".equals(schedule.getStatus()))
                .filter(schedule -> schedule.getScheduledStartTime().isAfter(now)).count();
        long bookedStudents = bookings.stream().filter(booking -> "BOOKED".equals(booking.getStatus())).count();
        long attendedStudents = bookings.stream().filter(booking -> "ATTENDED".equals(booking.getStatus())).count();
        long completedSessions = schedules.stream().filter(schedule -> "COMPLETED".equals(schedule.getStatus())).count();
        long deliveredMinutes = schedules.stream().filter(schedule -> "COMPLETED".equals(schedule.getStatus()))
                .mapToLong(schedule -> Duration.between(schedule.getScheduledStartTime(), schedule.getScheduledEndTime()).toMinutes())
                .sum();
        double deliveredHours = BigDecimal.valueOf(deliveredMinutes)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP).doubleValue();
        var confirmed=privateLessons.findByCoachIdOrderByReservedAtDesc(coachId).stream()
                .filter(l->venueId.equals(l.getVenueId())&&"CONFIRMED".equals(l.getStatus())).toList();
        BigDecimal serviceRevenue=confirmed.stream().map(com.gym.fitness.module.training.entity.PrivateLessonEntity::getUnitPrice).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
        BigDecimal estimatedCommission=confirmed.stream().map(com.gym.fitness.module.training.entity.PrivateLessonEntity::getCommissionAmount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);

        return CoachWorkbenchResponse.builder()
                .summary(CoachWorkbenchResponse.Summary.builder()
                        .todaySchedules(todaySchedules).upcomingSchedules(upcomingSchedules)
                        .bookedStudents(bookedStudents).attendedStudents(attendedStudents)
                        .completedSessions(completedSessions).deliveredHours(deliveredHours)
                        .serviceRevenue(serviceRevenue).estimatedCommission(estimatedCommission).build())
                .schedules(scheduleItems).build();
    }

    private CoachWorkbenchResponse.CoachSchedule toSchedule(
            CourseScheduleEntity schedule, List<BookingEntity> bookings) {
        List<CoachWorkbenchResponse.StudentBooking> students = bookings.stream()
                .filter(booking -> List.of("BOOKED", "ATTENDED", "NO_SHOW").contains(booking.getStatus()))
                .map(booking -> CoachWorkbenchResponse.StudentBooking.builder()
                        .bookingId(booking.getId()).memberId(booking.getMemberId())
                        .memberNo(booking.getMember().getMemberNo()).realName(booking.getMember().getRealName())
                        .phone(booking.getMember().getPhone()).status(booking.getStatus())
                        .checkedInAt(booking.getCheckedInAt()).build())
                .toList();
        return CoachWorkbenchResponse.CoachSchedule.builder()
                .id(schedule.getId()).courseName(schedule.getCourse().getName())
                .startTime(schedule.getScheduledStartTime()).endTime(schedule.getScheduledEndTime())
                .status(schedule.getStatus()).maxStudents(schedule.getMaxStudents())
                .enrolledCount(schedule.getEnrolledCount()).students(students).build();
    }
}
