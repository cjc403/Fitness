package com.gym.fitness.module.workout.repository;
import com.gym.fitness.module.workout.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;
public interface TrainingPlanRepository extends JpaRepository<TrainingPlanEntity,Long> {
 String SCOPE="from TrainingPlanEntity p, MemberEntity m where p.memberId=m.id and (:member is null or (p.memberId=:member and p.status<>'DRAFT')) and (:venue is null or p.venueId=:venue) and (:coach is null or (p.coachId=:coach and m.status='ACTIVE' and (m.blacklistFlag is null or m.blacklistFlag=false) and ((m.venueId=:venue and m.coachId=:coach) or exists(select b.id from BookingEntity b where b.memberId=m.id and b.schedule.coachId=:coach and b.schedule.venueId=:venue and b.status in ('BOOKED','ATTENDED') and b.schedule.scheduledStartTime>=:cutoff))))";
 @Query("select p "+SCOPE+" and (:status='ALL' or p.status=:status) and (:keyword='' or lower(p.title) like lower(concat('%',:keyword,'%')) or lower(m.realName) like lower(concat('%',:keyword,'%')) or lower(m.memberNo) like lower(concat('%',:keyword,'%'))) order by p.id desc")
 Page<TrainingPlanEntity> search(@Param("member")Long member,@Param("venue")Long venue,@Param("coach")Long coach,@Param("cutoff")LocalDateTime cutoff,@Param("status")String status,@Param("keyword")String keyword,Pageable pageable);
 @Query("select p.status,count(p) "+SCOPE+" group by p.status")
 List<Object[]> statusCounts(@Param("member")Long member,@Param("venue")Long venue,@Param("coach")Long coach,@Param("cutoff")LocalDateTime cutoff);
 @Query("select count(s) from TrainingSessionEntity s where s.status='COMPLETED' and s.performedOn>=:start and s.performedOn<:end and s.planId in (select p.id "+SCOPE+")")
 long monthCount(@Param("member")Long member,@Param("venue")Long venue,@Param("coach")Long coach,@Param("cutoff")LocalDateTime cutoff,@Param("start")LocalDate start,@Param("end")LocalDate end);
 boolean existsByActiveMemberKey(String key);
}
