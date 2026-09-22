package com.gym.fitness.module.member.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.assistant.service.AiRateLimiter;
import com.gym.fitness.module.assistant.service.DeepSeekClient;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.entity.CourseEntity;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.dto.CourseRecommendationResponse;
import com.gym.fitness.module.member.dto.RecommendationRequest;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CourseRecommendationService {
    private static final String SYSTEM_PROMPT = """
            你是 FitChain 的课程推荐解释助手。系统已用可审计规则完成课程排序。
            请根据候选课程、分数和理由，生成一段不超过160个汉字的中文推荐摘要。
            不得改变排名，不得编造课程，不提供医疗诊断；如目标涉及康复或疼痛，提醒咨询专业人员。
            """;

    private final MemberRepository members;
    private final CourseScheduleRepository schedules;
    private final BookingRepository bookings;
    private final DeepSeekClient deepSeekClient;
    private final AiRateLimiter rateLimiter;

    public CourseRecommendationService(MemberRepository members,
                                       CourseScheduleRepository schedules,
                                       BookingRepository bookings,
                                       DeepSeekClient deepSeekClient,
                                       AiRateLimiter rateLimiter) {
        this.members = members;
        this.schedules = schedules;
        this.bookings = bookings;
        this.deepSeekClient = deepSeekClient;
        this.rateLimiter = rateLimiter;
    }

    public CourseRecommendationResponse recommend(RecommendationRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        MemberEntity member = members.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "当前账号尚未绑定会员档案"));
        List<BookingEntity> history = bookings.findByMemberIdOrderByBookedAtDesc(member.getId());
        Preference preference = inferPreference(history);
        Set<Long> bookedScheduleIds = new HashSet<>();
        history.stream().filter(item -> !"CANCELLED".equals(item.getStatus()))
                .map(BookingEntity::getScheduleId).forEach(bookedScheduleIds::add);

        LocalDateTime now = LocalDateTime.now();
        List<CourseRecommendationResponse.Item> ranked = schedules
                .findByVenueIdAndStatusAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
                        member.getVenueId(), "SCHEDULED", now, now.plusDays(30)).stream()
                .filter(item -> !bookedScheduleIds.contains(item.getId()))
                .filter(item -> remaining(item) > 0)
                .map(item -> score(item, request, preference))
                .sorted(Comparator.comparingInt(CourseRecommendationResponse.Item::getScore).reversed()
                        .thenComparing(CourseRecommendationResponse.Item::getStartTime))
                .limit(5)
                .toList();

        String localSummary = ranked.isEmpty()
                ? "未来30天暂时没有符合条件且仍有名额的课程，请稍后再查看。"
                : "已综合你的训练目标、历史偏好、上课时段和剩余名额，为你筛选出 " + ranked.size() + " 个课程。";
        String source = "可解释规则引擎";
        String summary = localSummary;
        if (!ranked.isEmpty()) {
            rateLimiter.check(userId);
            Optional<DeepSeekClient.AiCompletion> ai = deepSeekClient.complete(
                    SYSTEM_PROMPT, recommendationPrompt(request, preference, ranked));
            if (ai.isPresent()) {
                summary = ai.get().content();
                source = "规则引擎 + DeepSeek · " + ai.get().model();
            } else if (deepSeekClient.isConfigured()) {
                source = "可解释规则引擎 · AI服务降级";
            }
        }
        return CourseRecommendationResponse.builder()
                .summary(summary)
                .source(source)
                .algorithmVersion("hybrid-rule-v1")
                .recommendations(ranked)
                .build();
    }

    private CourseRecommendationResponse.Item score(CourseScheduleEntity schedule,
                                                     RecommendationRequest request,
                                                     Preference preference) {
        CourseEntity course = schedule.getCourse();
        int score = 35;
        List<String> reasons = new ArrayList<>();
        String goal = normalize(request.getGoal());
        if (matchesGoal(course, goal)) {
            score += 30;
            reasons.add("符合“" + request.getGoal().trim() + "”训练目标");
        }
        if (preference.favoriteType() != null && preference.favoriteType().equals(course.getType())) {
            score += 15;
            reasons.add("与你经常参加的课程类型一致");
        }
        if (preference.favoriteIntensity() != null && preference.favoriteIntensity().equals(course.getIntensity())) {
            score += 8;
            reasons.add("训练强度符合历史偏好");
        }
        if (matchesTime(schedule.getScheduledStartTime().getHour(), request.getPreferredTime())) {
            score += 12;
            reasons.add("符合偏好上课时段");
        } else if (request.getPreferredTime() == null || "ANY".equals(request.getPreferredTime())) {
            score += 5;
        }
        int remaining = remaining(schedule);
        if (remaining >= Math.max(3, schedule.getMaxStudents() / 3)) {
            score += 8;
            reasons.add("当前名额较充足");
        }
        if (!preference.courseIds().contains(course.getId())) {
            score += 5;
            reasons.add("增加训练多样性");
        }
        if (reasons.isEmpty()) {
            reasons.add("近期可预约且仍有名额");
        }
        return CourseRecommendationResponse.Item.builder()
                .scheduleId(schedule.getId()).courseId(course.getId()).courseName(course.getName())
                .courseType(course.getType()).intensity(course.getIntensity()).coachName(schedule.getCoachName())
                .startTime(schedule.getScheduledStartTime()).remainingPlaces(remaining).fee(course.getFee())
                .score(Math.min(100, score)).reasons(reasons.stream().limit(3).toList()).build();
    }

    private Preference inferPreference(List<BookingEntity> history) {
        Map<String, Integer> typeCounts = new HashMap<>();
        Map<String, Integer> intensityCounts = new HashMap<>();
        Set<Long> courseIds = new HashSet<>();
        history.stream().filter(item -> !"CANCELLED".equals(item.getStatus())).forEach(item -> {
            CourseEntity course = item.getSchedule().getCourse();
            courseIds.add(course.getId());
            typeCounts.merge(course.getType(), 1, Integer::sum);
            intensityCounts.merge(course.getIntensity(), 1, Integer::sum);
        });
        return new Preference(mostFrequent(typeCounts), mostFrequent(intensityCounts), courseIds);
    }

    private String mostFrequent(Map<String, Integer> counts) {
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private boolean matchesGoal(CourseEntity course, String goal) {
        if (goal.isBlank()) return false;
        String text = normalize(course.getName() + " " + course.getDescription() + " " + course.getType());
        if (containsAny(goal, "减脂", "燃脂", "瘦身", "有氧")) return containsAny(text, "燃脂", "有氧", "操", "group");
        if (containsAny(goal, "增肌", "力量", "塑形")) return containsAny(text, "力量", "功能", "私教", "private");
        if (containsAny(goal, "柔韧", "瑜伽", "放松", "拉伸")) return containsAny(text, "瑜伽", "柔韧", "舒缓", "拉伸");
        if (containsAny(goal, "体态", "评估", "康复")) return containsAny(text, "体态", "评估", "私教", "private");
        return text.contains(goal);
    }

    private boolean matchesTime(int hour, String preferredTime) {
        if (preferredTime == null || "ANY".equals(preferredTime)) return false;
        return switch (preferredTime) {
            case "MORNING" -> hour < 12;
            case "AFTERNOON" -> hour >= 12 && hour < 18;
            case "EVENING" -> hour >= 18;
            default -> false;
        };
    }

    private int remaining(CourseScheduleEntity schedule) {
        int max = schedule.getMaxStudents() == null ? 0 : schedule.getMaxStudents();
        int enrolled = schedule.getEnrolledCount() == null ? 0 : schedule.getEnrolledCount();
        return Math.max(0, max - enrolled);
    }

    private String recommendationPrompt(RecommendationRequest request, Preference preference,
                                        List<CourseRecommendationResponse.Item> ranked) {
        String candidates = ranked.stream()
                .map(item -> item.getCourseName() + "(" + item.getScore() + "分，"
                        + String.join("、", item.getReasons()) + ")")
                .reduce((left, right) -> left + "；" + right).orElse("");
        return "训练目标：" + (normalize(request.getGoal()).isBlank() ? "未指定" : request.getGoal())
                + "；偏好时段：" + request.getPreferredTime()
                + "；历史偏好类型：" + preference.favoriteType()
                + "；候选课程（顺序不可改变）：" + candidates;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private record Preference(String favoriteType, String favoriteIntensity, Set<Long> courseIds) {
    }
}
