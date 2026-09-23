package com.gym.fitness.module.review.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.entity.CourseEntity;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseRepository;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.review.dto.ReviewDtos.*;
import com.gym.fitness.module.review.entity.CourseReviewEntity;
import com.gym.fitness.module.review.repository.CourseReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程评价：会员对本人「已签到」的课评价一次；教练看自己的评分；店长看门店汇总与低分记录。
 *
 * <p>评价资格只认预约状态 {@code ATTENDED}（真实到课签到），并且一条预约只能评一次，
 * 既有服务层校验，也有数据库唯一约束兜底。</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewService {
    /** 低分阈值：3 星及以下进入门店低分关注列表。 */
    private static final int LOW_RATING = 3;
    private static final int RECENT_LIMIT = 20;

    private final CourseReviewRepository reviews;
    private final BookingRepository bookings;
    private final CourseScheduleRepository schedules;
    private final CourseRepository courses;
    private final MemberRepository members;
    private final SysUserRepository users;

    // ---------------------------------------------------------------- 会员端

    /** 本人已签到但还没评价的课次。 */
    @Transactional(readOnly = true)
    public List<PendingReview> pendingForMe() {
        MemberEntity me = currentMember();
        List<BookingEntity> attended = bookings.findByMemberIdOrderByBookedAtDesc(me.getId()).stream()
                .filter(b -> "ATTENDED".equals(b.getStatus())).toList();
        if (attended.isEmpty()) return List.of();
        Set<Long> reviewed = reviews.findByBookingIdIn(attended.stream().map(BookingEntity::getId).toList())
                .stream().map(CourseReviewEntity::getBookingId).collect(Collectors.toSet());
        return attended.stream().filter(b -> !reviewed.contains(b.getId())).map(this::toPending).toList();
    }

    @Transactional
    public ReviewView submit(ReviewRequest request) {
        MemberEntity me = currentMember();
        BookingEntity booking = bookings.findByIdAndMemberId(request.getBookingId(), me.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
        if (!"ATTENDED".equals(booking.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只有本人已签到的课程才能评价");
        }
        if (reviews.existsByBookingId(booking.getId())) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "这节课你已经评价过了");
        }
        CourseScheduleEntity schedule = booking.getSchedule();
        if (schedule == null) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该预约对应的排课已不存在，无法评价");
        }
        CourseReviewEntity entity = new CourseReviewEntity();
        entity.setBookingId(booking.getId());
        entity.setMemberId(me.getId());
        entity.setCourseId(schedule.getCourseId());
        entity.setScheduleId(schedule.getId());
        entity.setCoachId(schedule.getCoachId());
        entity.setVenueId(schedule.getVenueId());
        entity.setRating(request.getRating());
        entity.setTags(trimToNull(request.getTags()));
        entity.setContent(trimToNull(request.getContent()));
        return toView(reviews.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ReviewView> mine() {
        return reviews.findByMemberIdOrderByIdDesc(currentMember().getId()).stream().map(this::toView).toList();
    }

    // ---------------------------------------------------------------- 教练端

    /** 教练看本人收到的评价与平均分。 */
    @Transactional(readOnly = true)
    public RatingSummary coachSummary() {
        Long coachId = SecurityUtil.getCurrentUserId();
        return summarize(coachId, userName(coachId), reviews.findByCoachIdOrderByIdDesc(coachId));
    }

    // ---------------------------------------------------------------- 管理端

    /** 店长看本店、总部看全部：整体平均分、各教练平均分、低分记录。 */
    @Transactional(readOnly = true)
    public VenueReviewSummary venueSummary() {
        List<CourseReviewEntity> all = SecurityUtil.isSuperAdmin()
                ? reviews.findAllByOrderByIdDesc()
                : reviews.findByVenueIdOrderByIdDesc(requireVenue());
        List<CoachRating> coaches = new ArrayList<>();
        Map<Long, List<CourseReviewEntity>> byCoach = all.stream()
                .filter(r -> r.getCoachId() != null)
                .collect(Collectors.groupingBy(CourseReviewEntity::getCoachId));
        byCoach.forEach((coachId, list) -> coaches.add(new CoachRating(coachId, userName(coachId),
                list.size(), average(list))));
        coaches.sort(Comparator.comparing(CoachRating::averageRating,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<ReviewView> low = all.stream().filter(r -> r.getRating() != null && r.getRating() <= LOW_RATING)
                .limit(RECENT_LIMIT).map(this::toView).toList();
        return new VenueReviewSummary(all.size(), average(all), distribution(all), coaches, low);
    }

    // ---------------------------------------------------------------- 内部

    private RatingSummary summarize(Long targetId, String targetName, List<CourseReviewEntity> list) {
        List<ReviewView> recent = list.stream().limit(RECENT_LIMIT).map(this::toView).toList();
        return new RatingSummary(targetId, targetName, list.size(), average(list), distribution(list), recent);
    }

    private Double average(List<CourseReviewEntity> list) {
        if (list.isEmpty()) return null;
        double sum = list.stream().filter(r -> r.getRating() != null).mapToInt(CourseReviewEntity::getRating).sum();
        long count = list.stream().filter(r -> r.getRating() != null).count();
        if (count == 0) return null;
        return Math.round(sum / count * 100.0) / 100.0;
    }

    private Map<Integer, Long> distribution(List<CourseReviewEntity> list) {
        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            final int s = star;
            map.put(star, list.stream().filter(r -> r.getRating() != null && r.getRating() == s).count());
        }
        return map;
    }

    private PendingReview toPending(BookingEntity booking) {
        CourseScheduleEntity schedule = booking.getSchedule();
        if (schedule == null) {
            return new PendingReview(booking.getId(), null, null, null, null, null, null, booking.getCheckedInAt());
        }
        return new PendingReview(booking.getId(), schedule.getId(), schedule.getCourseId(),
                courseName(schedule.getCourseId()), schedule.getCoachId(), schedule.getCoachName(),
                schedule.getScheduledStartTime(), booking.getCheckedInAt());
    }

    private ReviewView toView(CourseReviewEntity entity) {
        CourseScheduleEntity schedule = schedules.findById(entity.getScheduleId()).orElse(null);
        MemberEntity member = members.findById(entity.getMemberId()).orElse(null);
        return new ReviewView(entity.getId(), entity.getBookingId(), entity.getMemberId(),
                member == null ? null : member.getRealName(), entity.getCourseId(),
                courseName(entity.getCourseId()), entity.getScheduleId(), entity.getCoachId(),
                entity.getCoachId() == null ? (schedule == null ? null : schedule.getCoachName())
                        : userName(entity.getCoachId()),
                schedule == null ? null : schedule.getScheduledStartTime(),
                entity.getRating(), entity.getTags(), entity.getContent(), entity.getCreatedAt());
    }

    private String courseName(Long courseId) {
        return courseId == null ? null : courses.findById(courseId).map(CourseEntity::getName).orElse(null);
    }

    private String userName(Long userId) {
        return userId == null ? null : users.findById(userId).map(SysUserEntity::getRealName).orElse(null);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MemberEntity currentMember() {
        return members.findByUserId(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Long requireVenue() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "缺少门店上下文");
        return venueId;
    }
}
