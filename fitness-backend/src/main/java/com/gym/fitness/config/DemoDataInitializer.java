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
import com.gym.fitness.module.organization.entity.RoleEntity;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.RoleRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component
@Profile("!prod | demo")
@Order(10)
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {
    private final RoleRepository roles;
    private final VenueRepository venues;
    private final SysUserRepository users;
    private final MemberRepository members;
    private final CourseRepository courses;
    private final CourseScheduleRepository schedules;
    private final BookingRepository bookings;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, RoleEntity> roleMap = new LinkedHashMap<>();
        roleMap.put("SUPER_ADMIN", ensureRole("总部管理员", "SUPER_ADMIN"));
        roleMap.put("MANAGER", ensureRole("店长", "MANAGER"));
        roleMap.put("COACH", ensureRole("教练", "COACH"));
        roleMap.put("RECEPTION", ensureRole("前台", "RECEPTION"));
        roleMap.put("MEMBER", ensureRole("会员", "MEMBER"));
        // 必须一起"确保"SALES 角色：早期建的库里没有这一行（V1 的种子之后才加了会籍顾问），
        // 缺了它新建会籍顾问账号会报 1001「角色不存在」。
        roleMap.put("SALES", ensureRole("会籍顾问", "SALES"));
        VenueEntity headquarters = venues.findAllByOrderByIdAsc().stream().findFirst()
                .orElseGet(() -> venue("FitChain 旗舰中心", "HEADQUARTERS", "上海市浦东新区健身路88号", "021-88886666"));
        if (venues.count() == 1) venue("FitChain 滨江店", "BINJIANG", "上海市滨江大道126号", "021-66668888");

        SysUserEntity admin = ensureUser("admin", "系统管理员", "admin@fitchain.local",
                roleMap.get("SUPER_ADMIN"), headquarters, false);
        ensureUser("manager", "旗舰店店长", "manager@fitchain.local",
                roleMap.get("MANAGER"), headquarters, false);
        SysUserEntity coach = ensureUser("coach", "Alex 教练", "coach@fitchain.local",
                roleMap.get("COACH"), headquarters, true);
        // 每个门店都要有教练账号：私教排课要求"教练账号门店 = 排课门店"，
        // 滨江店没有教练账号时，该店的私教排课无人可绑。
        VenueEntity binjiang = venues.findAllByOrderByIdAsc().stream()
                .filter(v -> !v.getId().equals(headquarters.getId())).findFirst().orElse(headquarters);
        ensureUser("coach_binjiang", "滨江店教练", "coach.binjiang@fitchain.local",
                roleMap.get("COACH"), binjiang, true);
        ensureUser("reception", "前台小林", "reception@fitchain.local",
                roleMap.get("RECEPTION"), headquarters, false);
        SysUserEntity memberUser = ensureUser("member", "林晓雨", "member@fitchain.local",
                roleMap.get("MEMBER"), headquarters, false);
        ensureUser("advisor", "会籍顾问小周", "advisor@fitchain.local",
                roleMap.get("SALES"), headquarters, false);

        if (members.count() == 0) seedMembers(headquarters.getId(), admin.getId(), coach.getId());
        linkDemoMember(memberUser.getId());
        if (courses.count() == 0) seedCourses(headquarters.getId(), admin.getId());
        if (schedules.count() == 0) seedSchedules(headquarters.getId(), admin.getId(), coach.getId());
        linkDemoCoach(coach.getId());
        if (bookings.count() == 0) seedBookings();
    }

    private RoleEntity ensureRole(String name, String code) {
        return roles.findByCode(code).orElseGet(() -> role(name, code));
    }

    private RoleEntity role(String name, String code) {
        RoleEntity entity = new RoleEntity(); entity.setName(name); entity.setCode(code); return roles.save(entity);
    }

    private SysUserEntity ensureUser(String username, String realName, String email,
                                     RoleEntity role, VenueEntity venue, boolean coach) {
        SysUserEntity user = users.findByUsername(username).orElseGet(SysUserEntity::new);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setRealName(realName);
        user.setEmail(email);
        user.setRoleId(role.getId());
        user.setVenueId(venue.getId());
        user.setStatus("ACTIVE");
        user.setMustChangePassword(false);
        user.setCoach(coach);
        return users.save(user);
    }

    private void linkDemoMember(Long userId) {
        if (members.findByUserId(userId).isPresent()) return;
        members.findAll().stream().filter(member -> member.getUserId() == null).findFirst().ifPresent(member -> {
            member.setUserId(userId);
            members.save(member);
        });
    }

    private void linkDemoCoach(Long coachId) {
        schedules.findAll().stream().filter(schedule -> schedule.getCoachId() == null).forEach(schedule -> {
            schedule.setCoachId(coachId);
            schedules.save(schedule);
        });
    }

    private VenueEntity venue(String name, String code, String address, String phone) {
        VenueEntity entity = new VenueEntity();
        entity.setName(name); entity.setCode(code); entity.setAddress(address); entity.setContactPhone(phone);
        entity.setContactEmail(code.toLowerCase(Locale.ROOT) + "@fitchain.local"); entity.setStatus("ACTIVE");
        return venues.save(entity);
    }

    private void seedMembers(Long venueId, Long adminId, Long coachId) {
        String[][] people = {
                {"林晓雨","13800138001","女"},{"周晨","13800138002","男"},{"陈思远","13800138003","男"},
                {"唐可心","13800138004","女"},{"顾言","13800138005","男"},{"苏沐","13800138006","女"},
                {"陆景","13800138007","男"},{"叶晴","13800138008","女"},{"江辰","13800138009","男"},
                {"许诺","13800138010","女"},{"沈舟","13800138011","男"},{"白露","13800138012","女"},
                {"程野","13800138013","男"},{"夏禾","13800138014","女"},{"温乔","13800138015","女"},
                {"方屿","13800138016","男"},{"宁安","13800138017","女"},{"宋越","13800138018","男"}
        };
        for (int i = 0; i < people.length; i++) {
            MemberEntity m = new MemberEntity();
            m.setMemberNo("FC" + LocalDate.now().getYear() + String.format("%04d", i + 1));
            m.setRealName(people[i][0]); m.setPhone(people[i][1]); m.setGender(people[i][2]);
            m.setEmail("member" + (i + 1) + "@example.com"); m.setBirthday(LocalDate.of(1990 + i % 10, i % 12 + 1, i % 25 + 1));
            m.setVenueId(venueId); m.setCreatedBy(adminId); m.setCoachId(coachId); m.setStatus(i == 15 ? "PAUSED" : "ACTIVE");
            m.setBlacklistFlag(i == 17); members.save(m);
        }
    }

    private void seedCourses(Long venueId, Long adminId) {
        course("活力燃脂操", "GROUP", "节奏感强的全身有氧训练，适合所有基础。", 45, "MEDIUM", 24, "68", venueId, adminId);
        course("流瑜伽", "GROUP", "改善柔韧性与核心稳定，舒缓压力。", 60, "LOW", 18, "88", venueId, adminId);
        course("功能性力量", "REGULAR", "复合动作训练，提升力量与运动表现。", 60, "HIGH", 12, "128", venueId, adminId);
        course("一对一体态评估", "PRIVATE", "教练专属评估与个性化训练建议。", 60, "MEDIUM", 1, "299", venueId, adminId);
    }

    private void course(String name, String type, String desc, int duration, String intensity, int capacity, String fee, Long venueId, Long adminId) {
        CourseEntity c = new CourseEntity(); c.setName(name); c.setType(type); c.setDescription(desc);
        c.setDurationMinutes(duration); c.setIntensity(intensity); c.setMaxCapacity(capacity);
        c.setFee(new BigDecimal(fee)); c.setVenueId(venueId); c.setStatus("ACTIVE"); c.setCreatedBy(adminId); courses.save(c);
    }

    private void seedSchedules(Long venueId, Long adminId, Long coachId) {
        List<CourseEntity> all = courses.findAllByOrderByIdDesc();
        String[] coaches = {"Alex 教练", "Mia 教练", "Leo 教练", "Nina 教练"};
        for (int day = -3; day <= 5; day++) {
            CourseEntity course = all.get(Math.floorMod(day, all.size()));
            int hour = day % 2 == 0 ? 10 : 19;
            LocalDateTime start = LocalDate.now().plusDays(day).atTime(hour, 0);
            CourseScheduleEntity s = new CourseScheduleEntity();
            s.setCourseId(course.getId()); s.setCoachId(coachId); s.setCoachName(coaches[Math.floorMod(day, coaches.length)]);
            s.setVenueId(venueId); s.setScheduledStartTime(start); s.setScheduledEndTime(start.plusMinutes(course.getDurationMinutes()));
            s.setMaxStudents(course.getMaxCapacity()); s.setEnrolledCount(0);
            s.setStatus(start.isBefore(LocalDateTime.now()) ? "COMPLETED" : "SCHEDULED"); s.setCreatedBy(adminId);
            schedules.save(s);
        }
    }

    private void seedBookings() {
        List<MemberEntity> allMembers = members.findAll();
        List<CourseScheduleEntity> allSchedules = schedules.findAll();
        int index = 0;
        for (CourseScheduleEntity schedule : allSchedules) {
            int seededCount = Math.min(Math.min(3, allMembers.size()), schedule.getMaxStudents());
            for (int i = 0; i < seededCount; i++) {
                BookingEntity b = new BookingEntity();
                b.setScheduleId(schedule.getId()); b.setMemberId(allMembers.get((index + i) % allMembers.size()).getId());
                b.setBookedAt(schedule.getScheduledStartTime().minusDays(2));
                if (schedule.getScheduledStartTime().isBefore(LocalDateTime.now())) {
                    b.setStatus(i == 2 ? "CANCELLED" : "ATTENDED");
                    if (i == 2) { b.setCancelledAt(schedule.getScheduledStartTime().minusHours(4)); b.setCancelReason("行程冲突"); }
                    else b.setCheckedInAt(schedule.getScheduledStartTime().minusMinutes(5));
                } else b.setStatus("BOOKED");
                bookings.save(b);
            }
            schedule.setEnrolledCount(seededCount); schedules.save(schedule); index += 2;
        }
    }
}
