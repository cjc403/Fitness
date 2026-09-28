package com.gym.fitness.module.course.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.course.dto.RoomDtos.*;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.entity.RoomEntity;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.course.repository.RoomRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 教室/场地资源。
 *
 * <p>健身房有硬性资源约束：同一间教室在同一时段只能上一节课。此前排课只有
 * 课程 + 教练 + 时间 + 容量，没有"在哪个教室"的概念，于是可以同时段把两节团课排进同一个房间。</p>
 */
@Service
@RequiredArgsConstructor
public class RoomService {
    /** 占用率的分母：按每天 08:00–22:00 共 14 小时可排课时长计算。 */
    private static final long DAILY_WINDOW_MINUTES = 14 * 60;

    private final RoomRepository rooms;
    private final CourseScheduleRepository schedules;
    private final VenueRepository venues;

    @Transactional(readOnly = true)
    public List<RoomView> list() {
        List<RoomEntity> list = SecurityUtil.isSuperAdmin()
                ? rooms.findAllByOrderByIdAsc()
                : rooms.findByVenueIdOrderByIdAsc(requireVenue());
        return list.stream().map(this::toView).toList();
    }

    @Transactional
    public RoomView create(RoomRequest request) {
        Long venueId = resolveVenueId(request.getVenueId());
        if (!venues.existsById(venueId)) throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        String name = request.getName().trim();
        if (rooms.existsByVenueIdAndName(venueId, name)) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "本门店已有同名教室");
        }
        RoomEntity entity = new RoomEntity();
        entity.setVenueId(venueId);
        entity.setName(name);
        entity.setCapacity(request.getCapacity() == null ? 0 : request.getCapacity());
        entity.setStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus().trim().toUpperCase());
        entity.setNote(request.getNote());
        return toView(rooms.save(entity));
    }

    @Transactional
    public RoomView update(Long id, RoomRequest request) {
        RoomEntity entity = rooms.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "教室不存在"));
        assertVenue(entity.getVenueId());
        String name = request.getName().trim();
        if (rooms.existsByVenueIdAndNameAndIdNot(entity.getVenueId(), name, id)) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "本门店已有同名教室");
        }
        entity.setName(name);
        entity.setCapacity(request.getCapacity() == null ? entity.getCapacity() : request.getCapacity());
        if (request.getStatus() != null) entity.setStatus(request.getStatus().trim().toUpperCase());
        entity.setNote(request.getNote());
        return toView(rooms.save(entity));
    }

    /** 已被排课引用的教室不能删除（会破坏排课历史），只能停用。 */
    @Transactional
    public void delete(Long id) {
        RoomEntity entity = rooms.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "教室不存在"));
        assertVenue(entity.getVenueId());
        long used = schedules.countByRoomId(id);
        if (used > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该教室已被 " + used + " 条排课使用，请改为停用");
        }
        rooms.delete(entity);
    }

    /** 某天各教室的占用情况（默认今天）。 */
    @Transactional(readOnly = true)
    public List<RoomOccupancy> occupancy(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        LocalDateTime from = day.atTime(LocalTime.MIN);
        LocalDateTime to = day.plusDays(1).atTime(LocalTime.MIN);
        List<RoomOccupancy> out = new ArrayList<>();
        for (RoomView room : list()) {
            List<CourseScheduleEntity> daySchedules = schedules
                    .findByRoomIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(room.id(), from, to);
            List<RoomOccupancy.Session> sessions = new ArrayList<>();
            long used = 0;
            for (CourseScheduleEntity s : daySchedules) {
                if ("CANCELLED".equals(s.getStatus())) continue;
                long minutes = Math.max(0, Duration.between(s.getScheduledStartTime(), s.getScheduledEndTime()).toMinutes());
                used += minutes;
                sessions.add(new RoomOccupancy.Session(s.getId(), s.getCourse() == null ? null : s.getCourse().getName(),
                        s.getCoachName(), s.getScheduledStartTime(), s.getScheduledEndTime(),
                        s.getEnrolledCount(), s.getMaxStudents()));
            }
            sessions.sort(Comparator.comparing(RoomOccupancy.Session::start));
            int rate = (int) Math.min(100, Math.round(used * 100.0 / DAILY_WINDOW_MINUTES));
            out.add(new RoomOccupancy(room.id(), room.name(), room.capacity(), used, rate, sessions));
        }
        return out;
    }

    private RoomView toView(RoomEntity e) {
        return new RoomView(e.getId(), e.getVenueId(), e.getName(), e.getCapacity(), e.getStatus(), e.getNote());
    }

    private Long requireVenue() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "缺少门店上下文");
        return venueId;
    }

    private void assertVenue(Long venueId) {
        if (!SecurityUtil.isSuperAdmin() && !requireVenue().equals(venueId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他门店的数据");
        }
    }

    private Long resolveVenueId(Long requested) {
        if (SecurityUtil.isSuperAdmin() && requested != null) return requested;
        if (SecurityUtil.isSuperAdmin()) return requireVenue();
        return requireVenue();
    }
}
