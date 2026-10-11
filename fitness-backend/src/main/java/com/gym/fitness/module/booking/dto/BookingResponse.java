package com.gym.fitness.module.booking.dto;

import com.gym.fitness.module.booking.entity.BookingEntity;
import java.time.LocalDateTime;

/** 预约响应仅含显示和操作所需字段，避免序列化会员隐私档案。 */
public record BookingResponse(Long id, Long scheduleId, Long memberId, MemberSummary member,
        ScheduleSummary schedule, String status, LocalDateTime bookedAt, LocalDateTime cancelledAt,
        String cancelReason, LocalDateTime checkedInAt, Integer waitlistPosition,
        LocalDateTime leaveRequestedAt, Integer noShowPenaltyPoints) {
    public record MemberSummary(Long id, String realName, String memberNo) {}
    public record CourseSummary(Long id, String name, String type) {}
    public record ScheduleSummary(Long id, Long courseId, CourseSummary course, Long coachId,
            String coachName, Long venueId, LocalDateTime scheduledStartTime, LocalDateTime scheduledEndTime,
            Integer maxStudents, Integer enrolledCount, String status) {}
    public static BookingResponse from(BookingEntity b) {
        var m=b.getMember();var s=b.getSchedule();var c=s==null?null:s.getCourse();
        return new BookingResponse(b.getId(),b.getScheduleId(),b.getMemberId(),
                m==null?null:new MemberSummary(m.getId(),m.getRealName(),m.getMemberNo()),
                s==null?null:new ScheduleSummary(s.getId(),s.getCourseId(),
                        c==null?null:new CourseSummary(c.getId(),c.getName(),c.getType()),
                        s.getCoachId(),s.getCoachName(),s.getVenueId(),s.getScheduledStartTime(),s.getScheduledEndTime(),
                        s.getMaxStudents(),s.getEnrolledCount(),s.getStatus()),
                b.getStatus(),b.getBookedAt(),b.getCancelledAt(),b.getCancelReason(),b.getCheckedInAt(),
                b.getWaitlistPosition(),b.getLeaveRequestedAt(),b.getNoShowPenaltyPoints());
    }
}
