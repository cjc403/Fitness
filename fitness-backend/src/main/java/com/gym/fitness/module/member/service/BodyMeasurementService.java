package com.gym.fitness.module.member.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.member.dto.BodyMeasurementDtos.*;
import com.gym.fitness.module.member.entity.BodyMeasurementEntity;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.BodyMeasurementRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 会员体测与训练档案。
 *
 * <p>数值范围在这里统一校验（后端是唯一可信来源，前端只做提示）；
 * 同一天重复提交视为修正当天记录，曲线不会出现重复点。</p>
 */
@Service
@RequiredArgsConstructor
public class BodyMeasurementService {
    private static final Set<String> GOALS = Set.of("FAT_LOSS", "MUSCLE_GAIN", "POSTURE", "ENDURANCE", "NONE");
    /** 最早可回溯的测量日期（避免误填成几十年前的日期）。 */
    private static final int MAX_BACKFILL_YEARS = 5;

    private final BodyMeasurementRepository measurements;
    private final MemberRepository members;
    private final SysUserRepository users;

    // ---------------------------------------------------------------- 会员自助

    /** 当前登录账号对应的会员 id（删除自己的记录用）。 */
    @Transactional(readOnly = true)
    public Long myMemberId() { return currentMember().getId(); }

    @Transactional(readOnly = true)
    public Trend myTrend() {
        return trend(currentMember());
    }

    @Transactional
    public MeasurementView saveMine(MeasurementRequest request) {
        return save(currentMember(), request);
    }

    @Transactional
    public Trend setMyGoal(GoalRequest request) {
        MemberEntity me = currentMember();
        applyGoal(me, request);
        return trend(me);
    }

    // ---------------------------------------------------------------- 员工侧

    @Transactional(readOnly = true)
    public Trend memberTrend(Long memberId) {
        return trend(requireAccessible(memberId));
    }

    @Transactional
    public MeasurementView saveForMember(Long memberId, MeasurementRequest request) {
        return save(requireAccessible(memberId), request);
    }

    @Transactional
    public Trend setGoalForMember(Long memberId, GoalRequest request) {
        MemberEntity member = requireAccessible(memberId);
        applyGoal(member, request);
        return trend(member);
    }

    /** 删除一条体测记录（会员删自己的，员工删可访问会员的）。 */
    @Transactional
    public void delete(Long memberId, Long measurementId) {
        MemberEntity member = requireAccessible(memberId);
        BodyMeasurementEntity entity = measurements.findById(measurementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "体测记录不存在"));
        if (!member.getId().equals(entity.getMemberId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该记录不属于这个会员");
        }
        measurements.delete(entity);
    }

    // ---------------------------------------------------------------- 内部

    private MeasurementView save(MemberEntity member, MeasurementRequest request) {
        validate(request);
        BodyMeasurementEntity entity = measurements
                .findByMemberIdAndMeasuredOn(member.getId(), request.getMeasuredOn())
                .orElseGet(() -> {
                    BodyMeasurementEntity fresh = new BodyMeasurementEntity();
                    fresh.setMemberId(member.getId());
                    fresh.setVenueId(member.getVenueId());
                    fresh.setMeasuredOn(request.getMeasuredOn());
                    return fresh;
                });
        boolean existing = entity.getId() != null;
        entity.setWeightKg(request.getWeightKg());
        entity.setBodyFatRate(request.getBodyFatRate());
        entity.setMuscleKg(request.getMuscleKg());
        entity.setChestCm(request.getChestCm());
        entity.setWaistCm(request.getWaistCm());
        entity.setHipCm(request.getHipCm());
        entity.setRestingHeartRate(request.getRestingHeartRate());
        entity.setNote(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim());
        entity.setRecordedBy(SecurityUtil.getCurrentUserId());
        if (existing) entity.setUpdatedAt(LocalDateTime.now());
        return toView(measurements.save(entity));
    }

    private void validate(MeasurementRequest request) {
        LocalDate day = request.getMeasuredOn();
        LocalDate today = LocalDate.now();
        if (day.isAfter(today)) throw new BusinessException(ErrorCode.PARAM_ERROR, "测量日期不能填未来");
        if (day.isBefore(today.minusYears(MAX_BACKFILL_YEARS))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "测量日期过于久远，请填写 " + MAX_BACKFILL_YEARS + " 年内的日期");
        }
        if (request.getWeightKg() == null && request.getBodyFatRate() == null && request.getMuscleKg() == null
                && request.getChestCm() == null && request.getWaistCm() == null && request.getHipCm() == null
                && request.getRestingHeartRate() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少填写一项测量数据");
        }
        range(request.getWeightKg(), "体重", 20, 300);
        range(request.getBodyFatRate(), "体脂率", 1, 70);
        range(request.getMuscleKg(), "肌肉量", 5, 200);
        range(request.getChestCm(), "胸围", 20, 250);
        range(request.getWaistCm(), "腰围", 20, 250);
        range(request.getHipCm(), "臀围", 20, 250);
        if (request.getRestingHeartRate() != null
                && (request.getRestingHeartRate() < 30 || request.getRestingHeartRate() > 220)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "静息心率应在 30–220 之间");
        }
    }

    private void range(BigDecimal value, String label, double min, double max) {
        if (value == null) return;
        if (value.doubleValue() < min || value.doubleValue() > max) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "应在 " + (int) min + "–" + (int) max + " 之间");
        }
    }

    private void applyGoal(MemberEntity member, GoalRequest request) {
        String goal = request.getTrainingGoal().trim().toUpperCase();
        if (!GOALS.contains(goal)) throw new BusinessException(ErrorCode.PARAM_ERROR, "训练目标不合法");
        member.setTrainingGoal("NONE".equals(goal) ? null : goal);
        member.setTrainingGoalNote(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim());
        members.save(member);
    }

    private Trend trend(MemberEntity member) {
        List<BodyMeasurementEntity> asc = measurements.findByMemberIdOrderByMeasuredOnAsc(member.getId());
        List<TrendPoint> points = asc.stream()
                .map(m -> new TrendPoint(m.getMeasuredOn(), m.getWeightKg(), m.getBodyFatRate(),
                        m.getMuscleKg(), m.getWaistCm()))
                .toList();
        List<Change> changes = new ArrayList<>();
        if (asc.size() >= 2) {
            BodyMeasurementEntity first = asc.get(0), last = asc.get(asc.size() - 1);
            addChange(changes, "体重", first.getWeightKg(), last.getWeightKg());
            addChange(changes, "体脂率", first.getBodyFatRate(), last.getBodyFatRate());
            addChange(changes, "肌肉量", first.getMuscleKg(), last.getMuscleKg());
            addChange(changes, "腰围", first.getWaistCm(), last.getWaistCm());
        }
        List<MeasurementView> views = measurements.findByMemberIdOrderByMeasuredOnDesc(member.getId())
                .stream().map(this::toView).toList();
        return new Trend(member.getId(), member.getRealName(), member.getTrainingGoal(),
                member.getTrainingGoalNote(), asc.size(), points, changes, views);
    }

    private void addChange(List<Change> out, String metric, BigDecimal from, BigDecimal to) {
        if (from == null || to == null) return;
        out.add(new Change(metric, from, to, to.subtract(from)));
    }

    /** 会员本人，或「会员本人所在门店的员工」；教练只允许访问自己的学员。 */
    private MemberEntity requireAccessible(Long memberId) {
        MemberEntity member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (SecurityUtil.isSuperAdmin()) return member;
        Long me = SecurityUtil.getCurrentUserId();
        if (SecurityUtil.hasRole("COACH")) {
            if (!Objects.equals(member.getCoachId(), me)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看归属自己的学员体测记录");
            }
            return member;
        }
        if (SecurityUtil.hasRole("MEMBER")) {
            MemberEntity mine = currentMember();
            if (!Objects.equals(mine.getId(), member.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本人的体测记录");
            }
            return member;
        }
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null || !venueId.equals(member.getVenueId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能查看其他门店会员的体测记录");
        }
        return member;
    }

    private MemberEntity currentMember() {
        return members.findByUserId(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "当前账号尚未绑定会员档案"));
    }

    private MeasurementView toView(BodyMeasurementEntity e) {
        String recorder = e.getRecordedBy() == null ? null
                : users.findById(e.getRecordedBy()).map(u -> u.getRealName()).orElse(null);
        return new MeasurementView(e.getId(), e.getMemberId(), e.getMeasuredOn(), e.getWeightKg(),
                e.getBodyFatRate(), e.getMuscleKg(), e.getChestCm(), e.getWaistCm(), e.getHipCm(),
                e.getRestingHeartRate(), e.getNote(), e.getRecordedBy(), recorder,
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
