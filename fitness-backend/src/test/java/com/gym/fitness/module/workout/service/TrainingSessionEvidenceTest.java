package com.gym.fitness.module.workout.service;
import com.gym.fitness.module.workout.entity.*;
import com.gym.fitness.module.workout.repository.*;
import com.gym.fitness.module.workout.dto.TrainingPlanDtos.SessionView;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TrainingSessionEvidenceTest {
 @Test void exactHistoricalSessionStillUsesOriginalPlanAndMemberAccess()throws Exception{
  var service=mock(TrainingPlanService.class,CALLS_REAL_METHODS);var access=mock(TrainingPlanAccess.class);var plans=mock(TrainingPlanRepository.class);var sessions=mock(TrainingSessionRepository.class);var exercises=mock(TrainingSessionExerciseRepository.class);var members=mock(MemberRepository.class);var actor=new SysUserEntity();actor.setId(7L);var plan=new TrainingPlanEntity();plan.setId(2L);plan.setMemberId(1L);var session=new TrainingSessionEntity();session.setId(99L);session.setVersion(0L);session.setDurationMinutes(30);session.setPerformedOn(java.time.LocalDate.of(2026,10,1));session.setPlanId(2L);session.setStatus("COMPLETED");session.setFeedbackRating(5);var member=new MemberEntity();member.setStatus("ACTIVE");
  when(access.actor()).thenReturn(actor);when(plans.findById(2L)).thenReturn(Optional.of(plan));when(sessions.findById(99L)).thenReturn(Optional.of(session));when(members.findById(1L)).thenReturn(Optional.of(member));when(exercises.findBySessionIdInOrderByPositionAsc(List.of(99L))).thenReturn(List.of());
  ReflectionTestUtils.setField(service,"access",access);ReflectionTestUtils.setField(service,"plans",plans);ReflectionTestUtils.setField(service,"sessions",sessions);ReflectionTestUtils.setField(service,"sessionExercises",exercises);ReflectionTestUtils.setField(service,"members",members);ReflectionTestUtils.setField(service,"em",mock(jakarta.persistence.EntityManager.class));
  var method=TrainingPlanService.class.getMethod("sessionEvidence",Long.class,Long.class,boolean.class);var found=(SessionView)method.invoke(service,2L,99L,true);assertEquals(99L,found.id());assertEquals(5,found.feedbackRating());verify(access).readable(actor,plan,true);verify(sessions,never()).findByPlanIdOrderByPerformedOnDescIdDesc(any(),any());
  session.setPlanId(3L);assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(service,2L,99L,true));
 }
}
