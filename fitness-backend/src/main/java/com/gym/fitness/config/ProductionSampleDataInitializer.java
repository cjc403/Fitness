package com.gym.fitness.config;

import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.entity.CourseEntity;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseRepository;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adds presentation-friendly business data to an existing PostgreSQL deployment.
 *
 * <p>The initializer is intentionally isolated behind the {@code sample} profile.
 * It never creates or changes login credentials. Re-running it is safe: records
 * are matched by stable venue codes, member numbers, course names and schedule
 * timestamps before insertion.</p>
 */
@Slf4j
@Component
@Profile("sample")
@Order(10)
@RequiredArgsConstructor
public class ProductionSampleDataInitializer implements ApplicationRunner {
    private static final String HEADQUARTERS = "HEADQUARTERS";
    private static final String BINJIANG = "BINJIANG";

    private final VenueRepository venues;
    private final SysUserRepository users;
    private final MemberRepository members;
    private final CourseRepository courses;
    private final CourseScheduleRepository schedules;
    private final BookingRepository bookings;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        VenueEntity headquarters = venues.findByCodeIgnoreCase(HEADQUARTERS)
                .orElseThrow(() -> new IllegalStateException("缺少总部场馆，无法初始化示例数据"));
        VenueEntity binjiang = ensureVenue("FitChain 滨江店", BINJIANG,
                "上海市浦东新区滨江大道126号", "021-66668888", headquarters.getId());

        SysUserEntity admin = requiredUser("admin");
        SysUserEntity coach = requiredUser("coach");
        SysUserEntity memberUser = requiredUser("member");
        MemberEntity portalMember = members.findByUserId(memberUser.getId())
                .orElseThrow(() -> new IllegalStateException("会员账号 member 尚未绑定会员档案"));

        seedMembers(headquarters, binjiang, admin, coach, portalMember);
        Map<String, CourseEntity> courseMap = seedCourses(headquarters, binjiang, admin);
        List<CourseScheduleEntity> scheduleList = seedSchedules(headquarters, binjiang, admin, coach, courseMap);
        seedBookings(scheduleList, portalMember);

        log.info("Sample data ready: {} venues, {} members, {} courses, {} schedules and {} bookings",
                venues.count(), members.count(), courses.count(), schedules.count(), bookings.count());
    }

    private VenueEntity ensureVenue(String name, String code, String address, String phone, Long parentId) {
        return venues.findByCodeIgnoreCase(code).orElseGet(() -> {
            VenueEntity venue = new VenueEntity();
            venue.setName(name);
            venue.setCode(code);
            venue.setAddress(address);
            venue.setContactPhone(phone);
            venue.setContactEmail(code.toLowerCase(Locale.ROOT) + "@fitchain.local");
            venue.setParentVenueId(parentId);
            venue.setStatus("ACTIVE");
            return venues.save(venue);
        });
    }

    private SysUserEntity requiredUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("缺少账号 " + username + "，请先完成五角色账号初始化"));
    }

    private void seedMembers(VenueEntity headquarters, VenueEntity binjiang, SysUserEntity admin,
                             SysUserEntity coach, MemberEntity portalMember) {
        List<MemberSeed> seeds = List.of(
                new MemberSeed("DEMO-2026-002", "周晨", "13910000002", "男", LocalDate.of(1993, 3, 12), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-003", "陈思远", "13910000003", "男", LocalDate.of(1990, 8, 22), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-004", "唐可心", "13910000004", "女", LocalDate.of(1997, 5, 16), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-005", "顾言", "13910000005", "男", LocalDate.of(1989, 11, 3), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-006", "苏沐", "13910000006", "女", LocalDate.of(1995, 1, 28), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-007", "陆景", "13910000007", "男", LocalDate.of(1992, 7, 9), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-008", "叶晴", "13910000008", "女", LocalDate.of(1998, 10, 19), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-009", "江辰", "13910000009", "男", LocalDate.of(1991, 4, 7), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-010", "许诺", "13910000010", "女", LocalDate.of(1996, 12, 11), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-011", "沈舟", "13910000011", "男", LocalDate.of(1988, 6, 25), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-012", "白露", "13910000012", "女", LocalDate.of(1994, 9, 14), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-013", "程野", "13910000013", "男", LocalDate.of(1999, 2, 20), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-014", "夏禾", "13910000014", "女", LocalDate.of(1993, 5, 8), headquarters.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-015", "温乔", "13910000015", "女", LocalDate.of(1997, 7, 17), binjiang.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-016", "方屿", "13910000016", "男", LocalDate.of(1990, 10, 2), binjiang.getId(), "PAUSED", false),
                new MemberSeed("DEMO-2026-017", "宁安", "13910000017", "女", LocalDate.of(1996, 1, 23), binjiang.getId(), "ACTIVE", false),
                new MemberSeed("DEMO-2026-018", "宋越", "13910000018", "男", LocalDate.of(1992, 12, 6), binjiang.getId(), "ACTIVE", true)
        );

        for (MemberSeed seed : seeds) {
            if (members.existsByMemberNoIgnoreCase(seed.memberNo())) continue;
            MemberEntity member = new MemberEntity();
            member.setMemberNo(seed.memberNo());
            member.setRealName(seed.realName());
            member.setPhone(seed.phone());
            member.setEmail(seed.memberNo().toLowerCase(Locale.ROOT) + "@example.com");
            member.setGender(seed.gender());
            member.setBirthday(seed.birthday());
            member.setAddress(seed.venueId().equals(headquarters.getId()) ? "上海市浦东新区" : "上海市滨江新区");
            member.setVenueId(seed.venueId());
            member.setCoachId(seed.venueId().equals(headquarters.getId()) ? coach.getId() : null);
            member.setStatus(seed.status());
            member.setBlacklistFlag(seed.blacklisted());
            member.setCreatedBy(admin.getId());
            members.save(member);
        }

        if (portalMember.getCoachId() == null) portalMember.setCoachId(coach.getId());
        if (portalMember.getBlacklistFlag() == null) portalMember.setBlacklistFlag(false);
        members.save(portalMember);
    }

    private Map<String, CourseEntity> seedCourses(VenueEntity headquarters, VenueEntity binjiang,
                                                   SysUserEntity admin) {
        List<CourseSeed> seeds = List.of(
                new CourseSeed("活力燃脂操", "GROUP", "节奏感强的全身有氧训练，适合减脂与提升心肺。", 45, "MEDIUM", 24, "68", headquarters.getId()),
                new CourseSeed("流瑜伽", "GROUP", "改善柔韧性与核心稳定，舒缓久坐压力。", 60, "LOW", 18, "88", headquarters.getId()),
                new CourseSeed("功能性力量", "REGULAR", "复合动作与器械训练，提升力量和运动表现。", 60, "HIGH", 12, "128", headquarters.getId()),
                new CourseSeed("动感单车", "GROUP", "高能音乐配合间歇骑行，快速激活全身。", 45, "HIGH", 20, "98", headquarters.getId()),
                new CourseSeed("普拉提塑形", "GROUP", "强化核心控制，改善体态与身体稳定性。", 55, "MEDIUM", 16, "108", headquarters.getId()),
                new CourseSeed("一对一体态评估", "PRIVATE", "体态筛查、动作评估与个性化训练建议。", 60, "MEDIUM", 1, "299", headquarters.getId()),
                new CourseSeed("滨江晨间瑜伽", "GROUP", "面向上班族的清晨舒展与呼吸练习。", 50, "LOW", 18, "78", binjiang.getId()),
                new CourseSeed("拳击燃脂", "GROUP", "拳击基础结合有氧循环，释放压力并高效燃脂。", 50, "HIGH", 16, "118", binjiang.getId()),
                new CourseSeed("核心训练营", "REGULAR", "循序渐进强化躯干力量与动作控制。", 45, "MEDIUM", 14, "99", binjiang.getId()),
                new CourseSeed("私教体验课", "PRIVATE", "一对一了解训练目标并制定入门方案。", 60, "MEDIUM", 1, "199", binjiang.getId())
        );

        Map<String, CourseEntity> result = new HashMap<>();
        List<CourseEntity> existing = courses.findAll();
        for (CourseSeed seed : seeds) {
            CourseEntity course = existing.stream()
                    .filter(item -> item.getVenueId().equals(seed.venueId()) && item.getName().equals(seed.name()))
                    .findFirst().orElseGet(() -> saveCourse(seed, admin.getId()));
            result.put(courseKey(seed.venueId(), seed.name()), course);
        }
        return result;
    }

    private CourseEntity saveCourse(CourseSeed seed, Long adminId) {
        CourseEntity course = new CourseEntity();
        course.setName(seed.name());
        course.setType(seed.type());
        course.setDescription(seed.description());
        course.setDurationMinutes(seed.duration());
        course.setIntensity(seed.intensity());
        course.setMaxCapacity(seed.capacity());
        course.setFee(new BigDecimal(seed.fee()));
        course.setVenueId(seed.venueId());
        course.setStatus("ACTIVE");
        course.setCreatedBy(adminId);
        return courses.save(course);
    }

    private List<CourseScheduleEntity> seedSchedules(VenueEntity headquarters, VenueEntity binjiang,
                                                       SysUserEntity admin, SysUserEntity coach,
                                                       Map<String, CourseEntity> courseMap) {
        LocalDate today = LocalDate.now();
        List<ScheduleSeed> seeds = new ArrayList<>();
        String[] hqNames = {"活力燃脂操", "流瑜伽", "功能性力量", "动感单车", "普拉提塑形"};
        for (int offset = -6; offset <= -1; offset++) {
            seeds.add(new ScheduleSeed(hqNames[Math.floorMod(offset, hqNames.length)], headquarters.getId(),
                    today.plusDays(offset).atTime(offset % 2 == 0 ? 10 : 19, 0), true, true));
        }
        seeds.add(new ScheduleSeed("活力燃脂操", headquarters.getId(), today.atTime(18, 30),
                today.atTime(18, 30).isBefore(LocalDateTime.now()), true));
        for (int offset = 1; offset <= 10; offset++) {
            seeds.add(new ScheduleSeed(hqNames[(offset - 1) % hqNames.length], headquarters.getId(),
                    today.plusDays(offset).atTime(offset % 2 == 0 ? 10 : 19, offset % 3 == 0 ? 30 : 0), false, true));
        }
        seeds.add(new ScheduleSeed("滨江晨间瑜伽", binjiang.getId(), today.atTime(7, 30), true, false));
        seeds.add(new ScheduleSeed("拳击燃脂", binjiang.getId(), today.plusDays(1).atTime(19, 30), false, false));
        seeds.add(new ScheduleSeed("核心训练营", binjiang.getId(), today.plusDays(3).atTime(18, 30), false, false));
        seeds.add(new ScheduleSeed("滨江晨间瑜伽", binjiang.getId(), today.plusDays(5).atTime(7, 30), false, false));
        seeds.add(new ScheduleSeed("私教体验课", binjiang.getId(), today.plusDays(7).atTime(14, 0), false, false));

        List<CourseScheduleEntity> existing = schedules.findAll();
        List<CourseScheduleEntity> result = new ArrayList<>();
        for (ScheduleSeed seed : seeds) {
            CourseEntity course = courseMap.get(courseKey(seed.venueId(), seed.courseName()));
            CourseScheduleEntity schedule = existing.stream()
                    .filter(item -> item.getCourseId().equals(course.getId())
                            && item.getScheduledStartTime().equals(seed.start()))
                    .findFirst().orElseGet(() -> saveSchedule(seed, course, admin.getId(), coach));
            result.add(schedule);
        }
        result.sort(Comparator.comparing(CourseScheduleEntity::getScheduledStartTime));
        return result;
    }

    private CourseScheduleEntity saveSchedule(ScheduleSeed seed, CourseEntity course, Long adminId,
                                                SysUserEntity coach) {
        CourseScheduleEntity schedule = new CourseScheduleEntity();
        schedule.setCourseId(course.getId());
        schedule.setCoachId(seed.ownedByDemoCoach() ? coach.getId() : null);
        schedule.setCoachName(seed.ownedByDemoCoach() ? coach.getRealName() : "Mia 教练");
        schedule.setVenueId(seed.venueId());
        schedule.setScheduledStartTime(seed.start());
        schedule.setScheduledEndTime(seed.start().plusMinutes(course.getDurationMinutes()));
        schedule.setMaxStudents(course.getMaxCapacity());
        schedule.setEnrolledCount(0);
        schedule.setStatus(seed.completed() || seed.start().isBefore(LocalDateTime.now()) ? "COMPLETED" : "SCHEDULED");
        schedule.setCreatedBy(adminId);
        return schedules.save(schedule);
    }

    private void seedBookings(List<CourseScheduleEntity> scheduleList, MemberEntity portalMember) {
        Map<Long, List<MemberEntity>> membersByVenue = new HashMap<>();
        for (MemberEntity member : members.findAll()) {
            if (!"ACTIVE".equals(member.getStatus()) || Boolean.TRUE.equals(member.getBlacklistFlag())) continue;
            membersByVenue.computeIfAbsent(member.getVenueId(), ignored -> new ArrayList<>()).add(member);
        }
        membersByVenue.values().forEach(list -> list.sort(Comparator.comparing(MemberEntity::getId)));

        int scheduleIndex = 0;
        for (CourseScheduleEntity schedule : scheduleList) {
            List<MemberEntity> venueMembers = membersByVenue.getOrDefault(schedule.getVenueId(), List.of());
            if (venueMembers.isEmpty()) continue;
            List<MemberEntity> selected = selectMembers(venueMembers, scheduleIndex, 4);
            if (schedule.getVenueId().equals(portalMember.getVenueId()) && (scheduleIndex == 0 || scheduleIndex == 7 || scheduleIndex == 9)) {
                selected.remove(portalMember);
                selected.add(0, portalMember);
            }

            int activeCount = 0;
            for (int i = 0; i < selected.size(); i++) {
                MemberEntity member = selected.get(i);
                BookingEntity booking = bookings.findByScheduleIdAndMemberId(schedule.getId(), member.getId())
                        .orElseGet(BookingEntity::new);
                if (booking.getId() != null) {
                    if (!"CANCELLED".equals(booking.getStatus())) activeCount++;
                    continue;
                }
                booking.setScheduleId(schedule.getId());
                booking.setMemberId(member.getId());
                booking.setBookedAt(schedule.getScheduledStartTime().minusDays(2).minusMinutes(i * 13L));
                if ("COMPLETED".equals(schedule.getStatus())) {
                    if (i == selected.size() - 1) {
                        booking.setStatus("CANCELLED");
                        booking.setCancelledAt(schedule.getScheduledStartTime().minusHours(5));
                        booking.setCancelReason("临时行程冲突");
                    } else {
                        booking.setStatus("ATTENDED");
                        booking.setCheckedInAt(schedule.getScheduledStartTime().minusMinutes(8 - i));
                        activeCount++;
                    }
                } else if (i == selected.size() - 1 && scheduleIndex % 3 == 0) {
                    booking.setStatus("CANCELLED");
                    booking.setCancelledAt(schedule.getScheduledStartTime().minusDays(1));
                    booking.setCancelReason("会员自助取消");
                } else {
                    booking.setStatus("BOOKED");
                    activeCount++;
                }
                bookings.save(booking);
            }
            schedule.setEnrolledCount(activeCount);
            schedules.save(schedule);
            scheduleIndex++;
        }
    }

    private List<MemberEntity> selectMembers(List<MemberEntity> source, int offset, int count) {
        List<MemberEntity> selected = new ArrayList<>();
        for (int index = 0; index < Math.min(count, source.size()); index++) {
            selected.add(source.get(Math.floorMod(offset * 2 + index, source.size())));
        }
        return selected;
    }

    private String courseKey(Long venueId, String name) {
        return venueId + ":" + name;
    }

    private record MemberSeed(String memberNo, String realName, String phone, String gender,
                              LocalDate birthday, Long venueId, String status, boolean blacklisted) {}

    private record CourseSeed(String name, String type, String description, int duration,
                              String intensity, int capacity, String fee, Long venueId) {}

    private record ScheduleSeed(String courseName, Long venueId, LocalDateTime start,
                                boolean completed, boolean ownedByDemoCoach) {}
}
