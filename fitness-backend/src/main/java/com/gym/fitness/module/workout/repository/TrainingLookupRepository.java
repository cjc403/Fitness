package com.gym.fitness.module.workout.repository;
import com.gym.fitness.module.workout.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.booking.entity.BookingEntity;
public interface TrainingLookupRepository extends org.springframework.data.repository.Repository<MemberEntity,Long> {
 @Query("select count(m)>0 from MemberEntity m where m.id=:id and m.status='ACTIVE' and (m.blacklistFlag is null or m.blacklistFlag=false) and ((m.venueId=:venue and m.coachId=:coach) or exists(select b.id from BookingEntity b where b.memberId=m.id and b.schedule.coachId=:coach and b.schedule.venueId=:venue and b.status in ('BOOKED','ATTENDED') and b.schedule.scheduledStartTime>=:cutoff))")
 boolean related(@Param("id")Long id,@Param("venue")Long venue,@Param("coach")Long coach,@Param("cutoff")LocalDateTime cutoff);
 @Query("select m from MemberEntity m where m.status='ACTIVE' and (m.blacklistFlag is null or m.blacklistFlag=false) and ((m.venueId=:venue and m.coachId=:coach) or exists(select b.id from BookingEntity b where b.memberId=m.id and b.schedule.coachId=:coach and b.schedule.venueId=:venue and b.status in ('BOOKED','ATTENDED') and b.schedule.scheduledStartTime>=:cutoff)) and (:keyword='' or lower(m.realName) like lower(concat('%',:keyword,'%')) or lower(m.memberNo) like lower(concat('%',:keyword,'%'))) order by m.id desc")
 Page<MemberEntity> students(@Param("venue")Long venue,@Param("coach")Long coach,@Param("cutoff")LocalDateTime cutoff,@Param("keyword")String keyword,Pageable pageable);
 @Query("select b from BookingEntity b join fetch b.schedule s join fetch s.course where b.memberId=:member and s.coachId=:coach and s.venueId=:venue and b.status='ATTENDED' and s.scheduledStartTime>=:start and s.scheduledStartTime<:end order by s.scheduledStartTime desc")
 List<BookingEntity> options(@Param("member")Long member,@Param("coach")Long coach,@Param("venue")Long venue,@Param("start")LocalDateTime start,@Param("end")LocalDateTime end);
}
