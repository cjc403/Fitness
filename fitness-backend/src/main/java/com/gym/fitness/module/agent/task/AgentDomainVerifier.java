package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.agent.AgentBusiness;
import com.gym.fitness.module.workout.service.TrainingPlanService;
import com.gym.fitness.module.lead.service.LeadService;
import com.gym.fitness.module.member.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.util.regex.Pattern;

/** Fixed, permission-aware read adapters. No caller-controlled SQL or endpoint execution. */
@Service @RequiredArgsConstructor
public class AgentDomainVerifier {
 private final AgentBusiness business;private final TrainingPlanService training;private final LeadService leads;private final MemberService members;private final ObjectMapper json;
 private final com.gym.fitness.module.workout.service.TrainingPlanAccess access;
 private final com.gym.fitness.module.member.service.AdvisorService advisor;
 private final com.gym.fitness.module.course.service.CourseService courses;
 private final com.gym.fitness.module.member.service.MemberPortalService portal;
 @Transactional(readOnly=true)
 public JsonNode before(com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session me,String method,String path,JsonNode body){
  var out=json.createObjectNode();boolean self="MEMBER".equals(me.role());
  if(path.startsWith("/api/v1/admin/schedules")){
   if(!me.permissions().contains("SCHEDULE_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();out.put("kind","schedule");long id=pathId(path,"schedules");
   if(id>0){var s=courses.getScheduleForAgent(id);out.put("id",id).put("name",s.getCourse().getName()).put("venueId",s.getVenueId()).put("startTime",s.getScheduledStartTime().toString());}
   else{var c=courses.listCourses().stream().filter(course->course.getId()==body.path("courseId").asLong()).findFirst().orElseThrow(com.gym.fitness.module.agent.AgentIdentity::denied);out.put("name",c.getName()).put("courseId",c.getId()).put("venueId",c.getVenueId());}return out;
  }
  if(path.equals("/api/v1/admin/courses")){if(!me.permissions().contains("COURSE_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();return out.put("kind","course");}
  if(path.contains("/bookings")){
   if(!self&&!me.permissions().contains(path.endsWith("/check-in")||path.endsWith("/no-show")?"CHECKIN_WRITE":"BOOKING_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();
   out.put("kind","booking");long id=pathId(path,"bookings");
   if(id>0){var row=business.bookingEvidence(id,0);if(row.isMissingNode())throw com.gym.fitness.module.agent.AgentIdentity.denied();out.put("id",id);out.put("courseName",row.path("courseName").asText(row.path("schedule").path("course").path("name").asText()));out.put("memberName",row.path("member").path("realName").asText());return out;}
   if(!self){var m=members.get(body.path("memberId").asLong());out.put("memberId",m.getId()).put("memberName",m.getRealName());}
   long schedule=body.path("scheduleId").asLong();out.put("scheduleId",schedule);boolean found=false;
   if(self){for(var s:portal.schedules())if(s.getScheduleId()==schedule){out.put("courseName",s.getCourseName());out.put("startTime",s.getStartTime().toString());found=true;}}
   else for(var s:courses.listSchedules(java.time.LocalDate.now(),java.time.LocalDate.now().plusDays(92)))if(s.getId()==schedule){out.put("courseName",s.getCourse().getName());out.put("startTime",s.getScheduledStartTime().toString());out.put("venueId",s.getVenueId());found=true;}
   if(!found)throw com.gym.fitness.module.agent.AgentIdentity.denied();return out;
  }
  if(path.startsWith("/api/v1/leads")){
   if(!me.permissions().contains("LEAD_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();long id=pathId(path,"leads");out.put("kind","lead");
   if(id>0){var lead=json.valueToTree(leads.detail(id)).path("lead");out.put("id",id).put("name",lead.path("name").asText()).put("venueId",lead.path("venueId").asLong()).put("version",lead.path("version").asLong());}return out;
  }
  if(path.contains("/training-plans")){
   if(!self&&!me.permissions().contains(path.endsWith("/archive")?"TRAINING_PLAN_ARCHIVE":"TRAINING_PLAN_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();long id=pathId(path,"training-plans");out.put("kind","training");
   if(id>0){var plan=training.detail(id,1,20,self).plan();out.put("id",id).put("name",plan.title()).put("memberName",plan.memberName()).put("venueId",plan.venueId()).put("version",plan.version());}
   else{long memberId=body.path("memberId").asLong();if(!"COACH".equals(me.role())||!access.related(access.actor(),memberId))throw com.gym.fitness.module.agent.AgentIdentity.denied();var m=members.get(memberId);out.put("memberId",memberId).put("memberName",m.getRealName());}return out;
  }
  if(path.startsWith("/api/v1/admin/members")){
   if(!me.permissions().contains("MEMBER_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();long id=pathId(path,"members");out.put("kind","member");if(id>0){var m=members.get(id);out.put("id",id).put("name",m.getRealName()).put("venueId",m.getVenueId());}return out;
  }
  return out;
 }
 public void guard(String goal,JsonNode before,JsonNode body){
  String kind=before.path("kind").asText();
  if(before.path("id").asLong()>0&&!AgentGoalBinding.labelMatches(goal,before.path("name").asText(before.path("courseName").asText()),before.path("id").asLong(),kind))throw com.gym.fitness.module.agent.AgentIdentity.invalid("操作对象与任务不匹配，请补充准确名称或编号");
  if(!before.path("memberName").asText().isBlank()&&before.path("id").asLong()==0&&!AgentGoalBinding.labelMatches(goal,before.path("memberName").asText(),before.path("memberId").asLong(),"member"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("会员与任务要求不匹配");
  if("booking".equals(kind)&&before.path("id").asLong()==0&&!AgentGoalBinding.labelMatches(goal,before.path("courseName").asText(),before.path("scheduleId").asLong(),"schedule"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("排课与任务要求不匹配");
  if("schedule".equals(kind)&&before.path("id").asLong()==0&&!AgentGoalBinding.labelMatches(goal,before.path("name").asText(),before.path("courseId").asLong(),"course"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("排课课程与任务要求不匹配");
  if(body.hasNonNull("name")&&!goal.contains(body.path("name").asText()))throw com.gym.fitness.module.agent.AgentIdentity.invalid("拟创建名称与任务要求不匹配");
  if(body.hasNonNull("title")&&!goal.contains(body.path("title").asText()))throw com.gym.fitness.module.agent.AgentIdentity.invalid("拟创建标题与任务要求不匹配");
  if(body.hasNonNull("realName")&&!goal.contains(body.path("realName").asText()))throw com.gym.fitness.module.agent.AgentIdentity.invalid("拟登记会员姓名与任务要求不匹配");
 }
 @Transactional(readOnly=true)
 public boolean verify(String type,AgentMutationReceiptService.Receipt r,String goal,boolean member){
  try{
   JsonNode data=r.result().path("data"),actual;long id=data.path("id").asLong();
   if(r.before().path("id").asLong()>0&&!r.before().path("name").asText().isBlank()&&!AgentGoalBinding.labelMatches(goal,r.before().path("name").asText(),r.before().path("id").asLong(),type))return false;
   for(String key:new String[]{"name","title"})if(r.request().hasNonNull(key)&&!goal.contains(r.request().get(key).asText()))return false;
   switch(type){
    case "course":
     if(id==0)id=pathId(r.path(),"courses");long courseId=id;
     var course=courses.listCourses().stream().filter(c->c.getId()==courseId).findFirst();
     return "DELETE".equals(r.method())?course.isEmpty():course.isPresent()&&matches(data,json.valueToTree(course.get()),"id","name","type","fee","maxCapacity","durationMinutes","intensity");
    case "schedule":
     if(id==0)id=pathId(r.path(),"schedules");
     try{actual=json.valueToTree(courses.getScheduleForAgent(id));return !"DELETE".equals(r.method())&&matches(data,actual,"id","courseId","venueId","coachId","roomId","scheduledStartTime","scheduledEndTime","maxStudents");}
     catch(com.gym.fitness.common.exception.BusinessException missing){return "DELETE".equals(r.method())&&missing.getErrorCode()==com.gym.fitness.common.constant.ErrorCode.NOT_FOUND;}
    case "booking":
     if(id==0)id=pathId(r.path(),"bookings");
     actual=business.bookingEvidence(id,r.request().path("scheduleId").asLong());
     if(r.path().endsWith("/cancel")&&!"CANCELLED".equals(actual.path("status").asText()))return false;
     if(r.path().endsWith("/leave")&&!actual.hasNonNull("leaveRequestedAt"))return false;
     return !actual.isMissingNode()&&matches(data,actual,"id","status","memberId","scheduleId");
    case "training":
     id=data.path("plan").path("id").asLong(pathId(r.path(),"training-plans"));if(id<=0)return false;
     var detail=training.detail(id,1,20,member);actual=json.valueToTree(detail);
     if(r.path().endsWith("/feedback")){long sessionId=pathId(r.path(),"sessions");return detail.sessions().records().stream().anyMatch(s->s.id()==sessionId&&s.feedbackRating()!=null&&s.feedbackRating()==r.request().path("rating").asInt()&&hashMatches(r.request(),"contentHash",json.valueToTree(s.feedbackContent())));}
     if(r.path().endsWith("/void")){long sessionId=pathId(r.path(),"sessions");return detail.sessions().records().stream().anyMatch(s->s.id()==sessionId&&"VOIDED".equals(s.status()));}
     if(r.request().has("exercises")&&!r.path().endsWith("/sessions")){
      if(!matches(r.request(),actual.path("plan"),"title","memberId","goal","startDate","endDate"))return false;
      var expectedExercises=r.request().path("exercises");var savedExercises=actual.path("exercises");if(!savedExercises.isArray()||savedExercises.size()!=expectedExercises.size())return false;
      for(int i=0;i<expectedExercises.size();i++)if(!matches(expectedExercises.get(i),savedExercises.get(i),"name","mode","sets","reps","weightKg","minutes")||!hashMatches(expectedExercises.get(i),"noteHash",savedExercises.get(i).path("note")))return false;
     }
     return matches(data.path("plan"),actual.path("plan"),"id","title","memberId","status","version")&&hashMatches(r.request(),"noteHash",actual.path("plan").path("note"))&&(!r.path().endsWith("/publish")||"ACTIVE".equals(actual.path("plan").path("status").asText()))&&(!r.path().endsWith("/archive")||"ARCHIVED".equals(actual.path("plan").path("status").asText()));
    case "trial": case "lead":
     id=data.path("lead").path("id").asLong(pathId(r.path(),"leads"));if(id<=0)return false;
     actual=json.valueToTree(leads.detail(id));
     if(!matches(data.path("lead"),actual.path("lead"),"id","name","venueId"))return false;
     if("trial".equals(type)){
      for(var candidate:actual.path("trials"))if(matches(r.request(),candidate,"hostId","startsAt","endsAt","status")&&data.path("trials").findValues("id").stream().anyMatch(v->v.asLong()==candidate.path("id").asLong()))return true;
      return false;
     }return true;
    case "retention":
     id=pathId(r.path(),"members");if(id<=0)return false;long follow=data.path("id").asLong();return advisor.memberFollowUps(id).stream().anyMatch(f->f.id()==follow);
    case "member":
     if(id==0)id=pathId(r.path(),"members");if(id<=0)return false;actual=json.valueToTree(members.get(id));return actual.path("id").asLong()==id&&matches(data,actual,"realName","status");
    default:return false;
   }
  }catch(com.gym.fitness.common.exception.BusinessException denied){return false;}
 }
 private boolean hashMatches(JsonNode expected,String key,JsonNode actual){
  if(!expected.hasNonNull(key))return true;
  try{var normalized=actual.isMissingNode()||actual.isNull()||actual.isTextual()&&actual.asText().isBlank()?json.nullNode():actual;return expected.path(key).asText().equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(normalized))));}catch(Exception e){return false;}
 }
 private boolean matches(JsonNode expected,JsonNode actual,String...fields){if(actual.isMissingNode()||actual.isNull())return false;for(var field:fields)if(expected.hasNonNull(field)){var a=expected.get(field);var b=actual.path(field);if(a.isNumber()&&b.isNumber()){if(a.decimalValue().compareTo(b.decimalValue())!=0)return false;}else if(!a.asText().equals(b.asText()))return false;}return true;}
 private JsonNode find(JsonNode value,long id){if(id<=0)return com.fasterxml.jackson.databind.node.MissingNode.getInstance();if(value.isArray()){for(var v:value)if(v.path("id").asLong()==id)return v;}if(value.isObject()&&value.has("records"))return find(value.get("records"),id);return com.fasterxml.jackson.databind.node.MissingNode.getInstance();}
 private long pathId(String path,String resource){var m=Pattern.compile("/"+resource+"/(\\d+)(?:/|$)").matcher(path);return m.find()?Long.parseLong(m.group(1)):0;}
}
