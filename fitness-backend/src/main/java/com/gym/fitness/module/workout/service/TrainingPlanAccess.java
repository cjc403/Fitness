package com.gym.fitness.module.workout.service;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.workout.entity.TrainingPlanEntity;
import com.gym.fitness.module.workout.repository.TrainingLookupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
@Component @RequiredArgsConstructor
public class TrainingPlanAccess {
 private final SysUserRepository users;private final MemberRepository members;private final TrainingLookupRepository lookup;
 public SysUserEntity actor(){return users.findById(Optional.ofNullable(SecurityUtil.getCurrentUserId()).orElseThrow(TrainingPlanAccess::denied)).filter(u->"ACTIVE".equals(u.getStatus())).orElseThrow(TrainingPlanAccess::denied);}
 public String role(SysUserEntity a){return a.getRole().getCode();}
 public boolean coach(SysUserEntity a){return "COACH".equals(role(a));}
 public boolean member(SysUserEntity a){return "MEMBER".equals(role(a));}
 public Long memberId(SysUserEntity a){if(!member(a))throw denied();return members.findByUserId(a.getId()).orElseThrow(TrainingPlanAccess::denied).getId();}
 public LocalDateTime cutoff(){return LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(90).atStartOfDay();}
 public Long scope(SysUserEntity a,Long requested){if("SUPER_ADMIN".equals(role(a)))return requested;if(!List.of("MANAGER","COACH").contains(role(a)))throw denied();if(requested!=null&&!requested.equals(a.getVenueId()))throw denied();return a.getVenueId();}
 public boolean related(SysUserEntity a,Long member){return coach(a)&&lookup.related(member,a.getVenueId(),a.getId(),cutoff());}
 public void writable(SysUserEntity a,TrainingPlanEntity p){if(!coach(a)||!a.getId().equals(p.getCoachId())||!a.getVenueId().equals(p.getVenueId())||!related(a,p.getMemberId()))throw denied();}
 public boolean canArchive(SysUserEntity a,TrainingPlanEntity p){return canWrite(a,p)||"SUPER_ADMIN".equals(role(a))||("MANAGER".equals(role(a))&&a.getVenueId().equals(p.getVenueId()));}
 public void archivable(SysUserEntity a,TrainingPlanEntity p){if(!canArchive(a,p))throw denied();}
 public boolean canWrite(SysUserEntity a,TrainingPlanEntity p){return coach(a)&&a.getId().equals(p.getCoachId())&&a.getVenueId().equals(p.getVenueId())&&related(a,p.getMemberId());}
 public void readable(SysUserEntity a,TrainingPlanEntity p,boolean self){if(self){if(!memberId(a).equals(p.getMemberId())||"DRAFT".equals(p.getStatus()))throw denied();return;}if(member(a))throw denied();if("SUPER_ADMIN".equals(role(a)))return;if("MANAGER".equals(role(a))&&a.getVenueId().equals(p.getVenueId()))return;writable(a,p);}
 public static BusinessException denied(){return new BusinessException(ErrorCode.FORBIDDEN,"无权访问该训练计划或学员");}
}
