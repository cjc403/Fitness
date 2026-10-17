package com.gym.fitness.module.course.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.dto.CourseRequest;
import com.gym.fitness.module.course.dto.ScheduleBatchRequest;
import com.gym.fitness.module.course.dto.ScheduleBatchResult;
import com.gym.fitness.module.course.dto.ScheduleRequest;
import com.gym.fitness.module.course.dto.ScheduleUpdateRequest;
import com.gym.fitness.module.course.entity.CourseEntity;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseRepository;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.notice.service.NotificationService;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.module.training.repository.PrivateLessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseService {
    /** 冲突提示里用的时间格式。 */
    private static final DateTimeFormatter SCHEDULE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter SCHEDULE_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] WEEKDAY_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    /** 单次批量排课的日期跨度上限，避免一次点出几年的课表。 */
    private static final long MAX_BATCH_DAYS = 180;

    private final CourseRepository courseRepository;
    private final CourseScheduleRepository scheduleRepository;
    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final PrivateLessonRepository privateLessonRepository;
    private final SysUserRepository sysUserRepository;
    private final NotificationService notificationService;
    private final com.gym.fitness.module.course.repository.RoomRepository roomRepository;

    /**
     * 排课表单用的教练选项：只有绑定到真实教练账号，教练工作台才能按 coachId 查到这条排课。
     */
    public record CoachOption(Long id, String name, Long venueId) {
    }

    @Transactional(readOnly = true)
    public List<CoachOption> listCoaches() {
        Long venueId = SecurityUtil.isSuperAdmin() ? null : requireCurrentVenueId();
        return sysUserRepository.searchEmployees(venueId, "COACH", "ACTIVE", null, PageRequest.of(0, 200))
                .getContent().stream()
                .map(user -> new CoachOption(user.getId(),
                        user.getRealName() == null || user.getRealName().isBlank()
                                ? user.getUsername() : user.getRealName(),
                        user.getVenueId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseEntity> listCourses() {
        return SecurityUtil.isSuperAdmin()
                ? courseRepository.findAllByOrderByIdDesc()
                : courseRepository.findAllByVenueIdOrderByIdDesc(requireCurrentVenueId());
    }

    @Transactional
    public CourseEntity createCourse(CourseRequest request) {
        CourseEntity entity = new CourseEntity();
        applyCourse(entity, request);
        entity.setCreatedBy(SecurityUtil.getCurrentUserId());
        return courseRepository.save(entity);
    }

    @Transactional
    public CourseEntity updateCourse(Long id, CourseRequest request) {
        CourseEntity entity = findAccessibleCourse(id);
        Long targetVenueId = resolveVenueId(entity, request);
        if (!targetVenueId.equals(entity.getVenueId()) && scheduleRepository.countByCourseId(id) > 0) {
            // 门店变更不会带着已有排课一起搬，会造成"课程在这家店、排课在另一家店"的错位。
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该课程已有排课，不能变更所属场馆");
        }
        applyCourse(entity, request);
        return courseRepository.save(entity);
    }

    @Transactional
    public void deleteCourse(Long id) {
        CourseEntity entity = findAccessibleCourse(id);
        long schedules = scheduleRepository.countByCourseId(id);
        if (schedules > 0) {
            // course_schedule.course_id 是 NO ACTION 外键，且排课被 booking / private_lesson 引用，
            // 因此这里在业务层先拦下，给出可读原因，而不是让数据库抛约束错误。
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该课程还有 " + schedules + " 条排课，不能删除；请先处理相关排课");
        }
        courseRepository.delete(entity);
    }

    /**
     * 编辑排课。允许纠正建错的门店（通过更换课程，排课门店始终跟随课程门店）、教练、时间与容量。
     *
     * <p>两条安全边界：已有预约记录时不允许更换课程；已有有效预约（未取消）时不允许调整时间或容量——
     * 否则会员按原时间到店却发现课被挪走。教练变更允许保留预约，但会向已预约会员发站内消息。</p>
     */
    @Transactional
    public CourseScheduleEntity updateSchedule(Long id, ScheduleUpdateRequest request) {
        CourseScheduleEntity schedule = findAccessibleSchedule(id);
        CourseEntity course = findAccessibleCourse(request.getCourseId());
        if (!request.getScheduledEndTime().isAfter(request.getScheduledStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }

        long reservations = bookingRepository.countByScheduleId(id);
        if (!course.getId().equals(schedule.getCourseId()) && reservations > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该排课已有 " + reservations + " 条预约记录，不能更换课程");
        }

        int currentMax = schedule.getMaxStudents() == null ? 0 : schedule.getMaxStudents();
        int maxStudents = request.getMaxStudents() == null ? currentMax : request.getMaxStudents();
        boolean timingChanged = !Objects.equals(schedule.getScheduledStartTime(), request.getScheduledStartTime())
                || !Objects.equals(schedule.getScheduledEndTime(), request.getScheduledEndTime());
        long activeReservations = bookingRepository.countByScheduleIdAndStatusNot(id, "CANCELLED");
        if ((timingChanged || maxStudents != currentMax) && activeReservations > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该排课还有 " + activeReservations + " 条有效预约，不能调整时间或容量");
        }
        int enrolled = schedule.getEnrolledCount() == null ? 0 : schedule.getEnrolledCount();
        if (maxStudents < enrolled) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "容量不能小于已报名人数 " + enrolled);
        }

        String previousCoach = schedule.getCoachName();
        LocalDateTime previousStart = schedule.getScheduledStartTime();
        assertCoachAvailable(request.getCoachId(), request.getScheduledStartTime(),
                request.getScheduledEndTime(), id);
        assertRoomAvailable(request.getRoomId(), schedule.getVenueId(), request.getScheduledStartTime(),
                request.getScheduledEndTime(),
                request.getMaxStudents() == null ? schedule.getMaxStudents() : request.getMaxStudents(), id);
        schedule.setCourseId(course.getId());
        schedule.setRoomId(request.getRoomId());
        schedule.setVenueId(course.getVenueId());
        schedule.setCoachId(request.getCoachId());
        schedule.setCoachName(request.getCoachName().trim());
        schedule.setScheduledStartTime(request.getScheduledStartTime());
        schedule.setScheduledEndTime(request.getScheduledEndTime());
        schedule.setMaxStudents(maxStudents);
        CourseScheduleEntity saved = scheduleRepository.save(schedule);

        // 时间或教练变了，已预约的会员必须收到站内消息，否则会按旧信息到店。
        boolean coachChanged = !Objects.equals(previousCoach, saved.getCoachName());
        if (timingChanged || coachChanged) {
            List<Long> memberIds = bookingRepository.findByScheduleIdInOrderByBookedAtAsc(List.of(id)).stream()
                    .filter(b -> "BOOKED".equals(b.getStatus()) || "WAITLISTED".equals(b.getStatus()))
                    .map(b -> b.getMemberId()).toList();
            if (!memberIds.isEmpty()) {
                StringBuilder detail = new StringBuilder("课程：" + course.getName());
                if (timingChanged) {
                    detail.append("；时间由 ").append(previousStart).append(" 调整为 ").append(saved.getScheduledStartTime());
                }
                if (coachChanged) {
                    detail.append("；教练由 ").append(previousCoach == null ? "—" : previousCoach)
                            .append(" 调整为 ").append(saved.getCoachName());
                }
                detail.append("。如有冲突请提前取消预约。");
                notificationService.notifyMembers(memberIds, saved.getVenueId(), "SCHEDULE_CHANGED",
                        "你预约的课程有变动", detail.toString(), "SCHEDULE", id);
            }
        }
        return saved;
    }

    /**
     * 删除排课。booking 与 private_lesson 都以 NO ACTION 外键引用排课，
     * 为了让会员预约和教练授课历史保持完整，这里在业务层拒绝而不是交给数据库报错。
     */
    @Transactional
    public void deleteSchedule(Long id) {
        CourseScheduleEntity schedule = findAccessibleSchedule(id);
        long reservations = bookingRepository.countByScheduleId(id);
        if (reservations > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该排课已有 " + reservations + " 条预约记录（含已取消），不能删除；可改为调整时间或教练");
        }
        long lessons = privateLessonRepository.countByScheduleId(id);
        if (lessons > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该排课已关联 " + lessons + " 条私教授课记录，不能删除");
        }
        scheduleRepository.delete(schedule);
    }

    @Transactional(readOnly = true)
    public List<CourseScheduleEntity> listSchedules(LocalDate from, LocalDate to) {
        LocalDate safeFrom = from == null ? LocalDate.now().minusDays(7) : from;
        LocalDate safeTo = to == null ? LocalDate.now().plusDays(30) : to;
        if (safeTo.isBefore(safeFrom)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
        if (SecurityUtil.isSuperAdmin()) {
            return scheduleRepository.findByScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
                    safeFrom.atStartOfDay(), safeTo.plusDays(1).atStartOfDay());
        }
        return scheduleRepository.findByVenueIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
                requireCurrentVenueId(), safeFrom.atStartOfDay(), safeTo.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public CourseScheduleEntity getScheduleForAgent(Long id) { return findAccessibleSchedule(id); }

    @Transactional
    public CourseScheduleEntity createSchedule(ScheduleRequest request) {
        if (!request.getScheduledEndTime().isAfter(request.getScheduledStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        CourseEntity course = findAccessibleCourse(request.getCourseId());
        Long venueId = request.getVenueId() == null ? course.getVenueId() : request.getVenueId();
        if (!venueRepository.existsById(venueId)) throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        if (!course.getVenueId().equals(venueId)) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "排课场馆必须与课程所属场馆一致");
        }
        assertCoachAvailable(request.getCoachId(), request.getScheduledStartTime(),
                request.getScheduledEndTime(), null);
        // 容量校验必须用"生效值"：请求不传时后端会回落到课程容量，直接传 null 会漏掉容量检查
        int effectiveSeats = request.getMaxStudents() == null ? course.getMaxCapacity() : request.getMaxStudents();
        assertRoomAvailable(request.getRoomId(), venueId, request.getScheduledStartTime(),
                request.getScheduledEndTime(), effectiveSeats, null);
        CourseScheduleEntity entity = new CourseScheduleEntity();
        entity.setCourseId(course.getId());
        entity.setRoomId(request.getRoomId());
        entity.setCoachId(request.getCoachId());
        entity.setCoachName(request.getCoachName().trim());
        entity.setVenueId(venueId);
        entity.setScheduledStartTime(request.getScheduledStartTime());
        entity.setScheduledEndTime(request.getScheduledEndTime());
        entity.setMaxStudents(request.getMaxStudents() == null ? course.getMaxCapacity() : request.getMaxStudents());
        entity.setEnrolledCount(0);
        entity.setStatus("SCHEDULED");
        entity.setCreatedBy(SecurityUtil.getCurrentUserId());
        return scheduleRepository.save(entity);
    }

    /**
     * 同一教练在同一时段只能有一节课。
     *
     * <p>此前没有任何校验：管理员可以把一个教练排到两个重叠时段，会员两边都能预约成功，
     * 到店才发现教练只有一个。这里统一在校验期拦住，并把冲突的那节课报给操作者。</p>
     *
     * @return 冲突原因；无冲突返回 {@code null}（批量排课用它决定跳过哪几节）
     */
    private String coachConflictReason(Long coachId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (coachId == null) return null;
        List<CourseScheduleEntity> clashes = scheduleRepository.findCoachOverlaps(
                coachId, start, end, excludeId == null ? 0L : excludeId);
        if (clashes.isEmpty()) return null;
        CourseScheduleEntity first = clashes.get(0);
        String courseName = courseRepository.findById(first.getCourseId())
                .map(CourseEntity::getName).orElse("课程");
        return "该教练 " + SCHEDULE_TIME.format(first.getScheduledStartTime()) + "~"
                + SCHEDULE_TIME.format(first.getScheduledEndTime()) + " 已有排课《" + courseName + "》";
    }

    /** 校验教室可用（归属门店、启用、容量足够）；未指定教室则直接通过。 */
    private String assertRoomUsable(Long roomId, Long venueId, Integer maxStudents) {
        if (roomId == null) return null;
        var room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "教室不存在"));
        if (!room.getVenueId().equals(venueId)) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "教室必须与排课门店一致");
        }
        if (!"ACTIVE".equals(room.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该教室已停用，不能排课");
        }
        if (room.getCapacity() != null && room.getCapacity() > 0 && maxStudents != null
                && maxStudents > room.getCapacity()) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "教室「" + room.getName() + "」最多容纳 " + room.getCapacity() + " 人，小于本课容量 " + maxStudents);
        }
        return room.getName();
    }

    /** 同一教室在同一时段是否已被占用（返回原因，无冲突返回 null）。 */
    private String roomConflictReason(Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (roomId == null) return null;
        List<CourseScheduleEntity> clashes = scheduleRepository.findRoomOverlaps(
                roomId, start, end, excludeId == null ? 0L : excludeId);
        if (clashes.isEmpty()) return null;
        CourseScheduleEntity first = clashes.get(0);
        String courseName = courseRepository.findById(first.getCourseId()).map(CourseEntity::getName).orElse("课程");
        String roomName = roomRepository.findById(roomId).map(r -> r.getName()).orElse("教室");
        return "教室「" + roomName + "」在 " + SCHEDULE_TIME.format(first.getScheduledStartTime()) + "~"
                + SCHEDULE_TIME.format(first.getScheduledEndTime()) + " 已被《" + courseName + "》占用";
    }

    private void assertRoomAvailable(Long roomId, Long venueId, LocalDateTime start, LocalDateTime end,
                                     Integer maxStudents, Long excludeId) {
        assertRoomUsable(roomId, venueId, maxStudents);
        String reason = roomConflictReason(roomId, start, end, excludeId);
        if (reason != null) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, reason + "，同一教室同一时段不能重复排课");
        }
    }

    private void assertCoachAvailable(Long coachId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        String reason = coachConflictReason(coachId, start, end, excludeId);
        if (reason != null) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, reason + "，同一时段不能重复排课");
        }
    }

    /**
     * 周期性批量排课。逐日展开日期区间，命中选定周几的日期生成一节课；
     * 已过去的时段、以及与该教练其它排课冲突的时段会被跳过并说明原因，不阻断整批。
     */
    @Transactional
    public ScheduleBatchResult createBatchSchedule(ScheduleBatchRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) > MAX_BATCH_DAYS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "单次批量排课最长 " + MAX_BATCH_DAYS + " 天");
        }
        CourseEntity course = findAccessibleCourse(request.getCourseId());
        Long venueId = request.getVenueId() == null ? course.getVenueId() : request.getVenueId();
        if (!venueRepository.existsById(venueId)) throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        if (!course.getVenueId().equals(venueId)) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "排课场馆必须与课程所属场馆一致");
        }
        List<Integer> weekdays = request.getWeekdays().stream().distinct().sorted().toList();
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        int maxStudents = request.getMaxStudents() == null ? course.getMaxCapacity() : request.getMaxStudents();
        assertRoomUsable(request.getRoomId(), venueId, maxStudents);
        LocalDateTime now = LocalDateTime.now();
        List<ScheduleBatchResult.Slot> created = new ArrayList<>();
        List<ScheduleBatchResult.Slot> skipped = new ArrayList<>();
        for (LocalDate day = request.getFromDate(); !day.isAfter(request.getToDate()); day = day.plusDays(1)) {
            if (!weekdays.contains(day.getDayOfWeek().getValue())) continue;
            LocalDateTime start = LocalDateTime.of(day, request.getStartTime());
            LocalDateTime end = LocalDateTime.of(day, request.getEndTime());
            String label = SCHEDULE_DAY.format(start) + "（"
                    + WEEKDAY_NAMES[day.getDayOfWeek().getValue() - 1] + "）";
            if (start.isBefore(now)) {
                skipped.add(new ScheduleBatchResult.Slot(label, "时间已过去"));
                continue;
            }
            String conflict = coachConflictReason(request.getCoachId(), start, end, null);
            if (conflict == null) conflict = roomConflictReason(request.getRoomId(), start, end, null);
            if (conflict != null) {
                skipped.add(new ScheduleBatchResult.Slot(label, conflict));
                continue;
            }
            if (!dryRun) {
                CourseScheduleEntity entity = new CourseScheduleEntity();
                entity.setCourseId(course.getId());
                entity.setRoomId(request.getRoomId());
                entity.setCoachId(request.getCoachId());
                entity.setCoachName(request.getCoachName().trim());
                entity.setVenueId(venueId);
                entity.setScheduledStartTime(start);
                entity.setScheduledEndTime(end);
                entity.setMaxStudents(maxStudents);
                entity.setEnrolledCount(0);
                entity.setStatus("SCHEDULED");
                entity.setCreatedBy(SecurityUtil.getCurrentUserId());
                scheduleRepository.save(entity);
            }
            created.add(new ScheduleBatchResult.Slot(label, dryRun ? "可生成" : "已生成"));
        }
        return new ScheduleBatchResult(created, skipped, dryRun);
    }

    private void applyCourse(CourseEntity entity, CourseRequest request) {
        Long venueId = resolveVenueId(entity, request);
        entity.setName(request.getName().trim());
        entity.setType(request.getType().toUpperCase());
        entity.setDescription(request.getDescription());
        entity.setDurationMinutes(request.getDurationMinutes() == null ? 60 : request.getDurationMinutes());
        entity.setIntensity(request.getIntensity() == null ? "MEDIUM" : request.getIntensity().toUpperCase());
        entity.setMaxCapacity(request.getMaxCapacity() == null ? 20 : request.getMaxCapacity());
        entity.setFee(request.getFee() == null ? BigDecimal.ZERO : request.getFee());
        entity.setVenueId(venueId);
        entity.setStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus().toUpperCase());
    }

    /**
     * 解析课程应归属的场馆：超级管理员可按请求指定，其余角色一律锁定为本人所属场馆。
     * 只做计算不做赋值，便于在写入前做一致性校验。
     */
    private Long resolveVenueId(CourseEntity entity, CourseRequest request) {
        Long venueId;
        if (SecurityUtil.isSuperAdmin()) {
            venueId = request.getVenueId() == null
                    ? (entity.getVenueId() == null ? SecurityUtil.getCurrentOrganizationId() : entity.getVenueId())
                    : request.getVenueId();
        } else {
            venueId = requireCurrentVenueId();
        }
        if (venueId == null || !venueRepository.existsById(venueId)) {
            throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        }
        return venueId;
    }

    private CourseScheduleEntity findAccessibleSchedule(Long id) {
        CourseScheduleEntity schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        if (!SecurityUtil.isSuperAdmin() && !schedule.getVenueId().equals(requireCurrentVenueId())) {
            // Do not reveal whether a record from another venue exists.
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return schedule;
    }

    private CourseEntity findAccessibleCourse(Long id) {
        CourseEntity course = courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!SecurityUtil.isSuperAdmin() && !course.getVenueId().equals(requireCurrentVenueId())) {
            // Do not reveal whether a record from another venue exists.
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    private Long requireCurrentVenueId() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return venueId;
    }
}
