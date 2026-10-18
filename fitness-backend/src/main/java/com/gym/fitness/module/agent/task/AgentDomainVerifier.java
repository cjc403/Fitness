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
 private final com.gym.fitness.module.organization.repository.VenueRepository venues;
 @Transactional(readOnly=true)
 public JsonNode before(com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session me,String method,String path,JsonNode body){
  var out=json.createObjectNode();boolean self="MEMBER".equals(me.role());
  if(path.startsWith("/api/v1/admin/schedules")){
   if(!me.permissions().contains("SCHEDULE_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();out.put("kind","schedule");long id=pathId(path,"schedules");
   if(id>0){var s=courses.getScheduleForAgent(id);out.put("id",id).put("name",s.getCourse().getName()).put("venueId",s.getVenueId()).put("startTime",s.getScheduledStartTime().toString());}
   else{var c=courses.listCourses().stream().filter(course->course.getId()==body.path("courseId").asLong()).findFirst().orElseThrow(com.gym.fitness.module.agent.AgentIdentity::denied);out.put("name",c.getName()).put("courseId",c.getId()).put("venueId",c.getVenueId());}return scoped(me,out,self);
  }
  if(path.equals("/api/v1/admin/courses")){if(!me.permissions().contains("COURSE_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();out.put("kind","course").put("venueId",body.path("venueId").asLong(me.venueId()==null?0:me.venueId()));return scoped(me,out,self);}
  if(path.contains("/bookings")){
   if(!self&&!me.permissions().contains(path.endsWith("/check-in")||path.endsWith("/no-show")?"CHECKIN_WRITE":"BOOKING_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();
   out.put("kind","booking");long id=pathId(path,"bookings");
   if(id>0){var row=business.bookingEvidence(id,0);if(row.isMissingNode())throw com.gym.fitness.module.agent.AgentIdentity.denied();out.put("id",id);out.put("courseName",row.path("courseName").asText(row.path("schedule").path("course").path("name").asText()));out.put("name",out.path("courseName").asText()).put("memberName",row.path("member").path("realName").asText()).put("memberId",row.path("memberId").asLong(row.path("member").path("id").asLong())).put("scheduleId",row.path("scheduleId").asLong(row.path("schedule").path("id").asLong())).put("venueId",row.path("venueId").asLong(row.path("schedule").path("venueId").asLong()));String time=row.path("startTime").asText(row.path("schedule").path("scheduledStartTime").asText());if(!time.isBlank())out.put("startTime",time);return scoped(me,out,self);}
   if(!self){var m=members.get(body.path("memberId").asLong());out.put("memberId",m.getId()).put("memberName",m.getRealName()).put("venueId",m.getVenueId());}
   long schedule=body.path("scheduleId").asLong();out.put("scheduleId",schedule);boolean found=false;
   if(self){for(var s:portal.schedules())if(s.getScheduleId()==schedule){out.put("courseName",s.getCourseName());out.put("startTime",s.getStartTime().toString());found=true;}}
   else for(var s:courses.listSchedules(java.time.LocalDate.now(),java.time.LocalDate.now().plusDays(92)))if(s.getId()==schedule){out.put("courseName",s.getCourse().getName());out.put("startTime",s.getScheduledStartTime().toString());out.put("venueId",s.getVenueId());found=true;}
   if(!found)throw com.gym.fitness.module.agent.AgentIdentity.denied();return scoped(me,out,self);
  }
  if(path.startsWith("/api/v1/leads")){
   if(!me.permissions().contains("LEAD_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();long id=pathId(path,"leads");out.put("kind","lead");
   if(id>0){var detail=json.valueToTree(leads.detail(id));var lead=detail.path("lead");out.put("eventHighWatermark",maxId(detail.path("events"))).put("trialHighWatermark",maxId(detail.path("trials")));out.put("id",id).put("name",lead.path("name").asText()).put("venueId",lead.path("venueId").asLong()).put("version",lead.path("version").asLong());}else out.put("venueId",body.path("venueId").asLong(me.venueId()==null?0:me.venueId()));return scoped(me,out,self);
  }
  if(path.contains("/training-plans")){
   if(!self&&!me.permissions().contains(path.endsWith("/archive")?"TRAINING_PLAN_ARCHIVE":"TRAINING_PLAN_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();long id=pathId(path,"training-plans");out.put("kind","training");
   if(id>0){var plan=training.detail(id,1,20,self).plan();out.put("id",id).put("name",plan.title()).put("memberName",plan.memberName()).put("venueId",plan.venueId()).put("version",plan.version());if(path.endsWith("/sessions"))out.put("sessionHighWatermark",training.sessionHighWatermark(id,self));}
   else{long memberId=body.path("memberId").asLong();if(!"COACH".equals(me.role())||!access.related(access.actor(),memberId))throw com.gym.fitness.module.agent.AgentIdentity.denied();var m=members.get(memberId);out.put("memberId",memberId).put("memberName",m.getRealName()).put("venueId",me.venueId()==null?0:me.venueId());}return scoped(me,out,self);
  }
  if(path.startsWith("/api/v1/admin/members")){
   if(!me.permissions().contains("MEMBER_WRITE"))throw com.gym.fitness.module.agent.AgentIdentity.denied();long id=pathId(path,"members");out.put("kind","member");if(id>0){var m=members.get(id);out.put("id",id).put("name",m.getRealName()).put("venueId",m.getVenueId());}else out.put("venueId",body.path("venueId").asLong(me.venueId()==null?0:me.venueId()));return scoped(me,out,self);
  }
  return scoped(me,out,self);
 }
 private JsonNode scoped(com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session me,com.fasterxml.jackson.databind.node.ObjectNode out,boolean self){
  long venue=out.path("venueId").asLong();if(venue>0)venues.findById(venue).ifPresent(v->out.put("venueName",v.getName()));
  String kind=out.path("kind").asText(),name=out.path("name").asText();
  if(out.path("id").asLong()>0&&!name.isBlank())out.put("sameNameCount",nameCount(kind,name,self,me.role()));
  if(out.path("id").asLong()==0&&out.path("memberId").asLong()>0&&!out.path("memberName").asText().isBlank())out.put("memberNameCount",nameCount("member",out.path("memberName").asText(),self,me.role()));
  if("booking".equals(kind)&&out.path("id").asLong()==0){
   long all=0,atTime=0;String course=out.path("courseName").asText(),time=out.path("startTime").asText();
   if(self){for(var row:portal.schedules())if(course.equals(row.getCourseName())){all++;if(time.equals(row.getStartTime().toString()))atTime++;}}
   else for(var row:courses.listSchedules(java.time.LocalDate.now(),java.time.LocalDate.now().plusDays(92)))if(row.getCourse()!=null&&course.equals(row.getCourse().getName())){all++;if(time.equals(row.getScheduledStartTime().toString()))atTime++;}
   out.put("sameScheduleNameCount",all).put("sameTimeCount",atTime);
  }return out;
 }
 private long nameCount(String kind,String name,boolean self,String role){
  if("booking".equals(kind))return 2; // Existing reservations need an explicit booking ID.
  if("schedule".equals(kind))return courses.listSchedules(java.time.LocalDate.now(),java.time.LocalDate.now().plusDays(92)).stream().filter(s->s.getCourse()!=null&&name.equals(s.getCourse().getName())).count();
  if(!java.util.Set.of("lead","member","training").contains(kind))return 1;
  long count=0;
  for(int page=1;page<=12;page++){
   JsonNode envelope,rows;String field;
   if("lead".equals(kind)){envelope=json.valueToTree(leads.workbench(null,"ALL","ALL",name,page,100)).path("leads");rows=envelope.path("records");field="name";}
   else if("training".equals(kind)){envelope=json.valueToTree(training.workbench(null,"ALL",name,page,100,self)).path("plans");rows=envelope.path("records");field="title";}
   else if("COACH".equals(role)){envelope=json.valueToTree(training.students(name,page,100));rows=envelope.path("records");field="realName";}
   else{envelope=json.valueToTree(members.list(name,page,100,null));rows=envelope.path("items");field="realName";}
   for(var row:rows)if(name.equals(row.path(field).asText()))count++;
   if(count>1)return count;
   if(envelope.path("total").asLong()<=page*100L)return count==1?1:2;
  }return 2; // A bounded query cannot certify uniqueness beyond its known range.
 }
 public void guard(String goal,JsonNode before,JsonNode body){
  String kind=before.path("kind").asText();
  if("booking".equals(kind)&&before.path("memberId").asLong()>0&&goal.matches("(?s).*(?:会员|学员)(?:编号)?\\s*#?\\s*\\d+.*")&&!AgentGoalBinding.labelMatches(goal,before.path("memberName").asText(),before.path("memberId").asLong(),"member"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("预约不属于用户指定的会员");
  if("lead".equals(kind)&&!leadGoalValues(goal,body))throw com.gym.fitness.module.agent.AgentIdentity.invalid("拟办理的潜客字段与当前目标不一致");
  if(before.path("sameNameCount").asLong()>1&&!AgentGoalBinding.explicitId(goal,before.path("id").asLong(),kind))throw com.gym.fitness.module.agent.AgentIdentity.invalid("存在同名业务对象，请补充准确对象编号后再办理");
  if(before.path("memberNameCount").asLong()>1&&!AgentGoalBinding.explicitId(goal,before.path("memberId").asLong(),"member"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("存在同名会员或学员，请补充准确会员编号");
  guardVenue(goal,before.path("venueId").asLong());
  if("booking".equals(kind)&&before.hasNonNull("startTime")){
   var time=Pattern.compile("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?").matcher(goal);
   if(time.find()&&!before.path("startTime").asText().replace('T',' ').startsWith(time.group().replace('T',' ')))throw com.gym.fitness.module.agent.AgentIdentity.invalid("排课时间与用户要求不一致");
   long count=before.path(time.reset().find()?"sameTimeCount":"sameScheduleNameCount").asLong();
   if(count>1&&!AgentGoalBinding.explicitId(goal,before.path("scheduleId").asLong(),"schedule"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("存在多节同名排课，请补充准确排课编号及时间");
  }
  if(before.path("id").asLong()>0&&!AgentGoalBinding.labelMatches(goal,before.path("name").asText(before.path("courseName").asText()),before.path("id").asLong(),kind))throw com.gym.fitness.module.agent.AgentIdentity.invalid("操作对象与任务不匹配，请补充准确名称或编号");
  if(!before.path("memberName").asText().isBlank()&&before.path("id").asLong()==0&&!AgentGoalBinding.labelMatches(goal,before.path("memberName").asText(),before.path("memberId").asLong(),"member"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("会员与任务要求不匹配");
  if("booking".equals(kind)&&before.path("id").asLong()==0&&!AgentGoalBinding.labelMatches(goal,before.path("courseName").asText(),before.path("scheduleId").asLong(),"schedule"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("排课与任务要求不匹配");
  if("schedule".equals(kind)&&before.path("id").asLong()==0&&!AgentGoalBinding.labelMatches(goal,before.path("name").asText(),before.path("courseId").asLong(),"course"))throw com.gym.fitness.module.agent.AgentIdentity.invalid("排课课程与任务要求不匹配");
  if(body.hasNonNull("name")&&!body.path("name").asText().equals(before.path("name").asText())&&!goal.contains(body.path("name").asText()))throw com.gym.fitness.module.agent.AgentIdentity.invalid("拟创建名称与任务要求不匹配");
  if(body.hasNonNull("title")&&!body.path("title").asText().equals(before.path("name").asText())&&!goal.contains(body.path("title").asText()))throw com.gym.fitness.module.agent.AgentIdentity.invalid("拟创建标题与任务要求不匹配");
  if(body.hasNonNull("realName")&&!goal.contains(body.path("realName").asText()))throw com.gym.fitness.module.agent.AgentIdentity.invalid("拟登记会员姓名与任务要求不匹配");
 }
 public void guardVenue(String goal,long actualVenue){
  String header=AgentOperationIntent.unquoted(goal).split("内容|备注",2)[0];
  if(actualVenue>0)for(var v:venues.findAll()){
   String name=v.getName(),label=name==null?"":name.replaceFirst("(?i)^FitChain\\s*","");
   if(name!=null&&name.length()>1&&(header.contains(name)||label.length()>1&&header.contains(label))&&v.getId()!=actualVenue)throw com.gym.fitness.module.agent.AgentIdentity.invalid("操作对象的门店与目标不一致，请核对门店和编号");
  }
 }
 public void guardContactVenue(String goal,String actualVenue){
  String header=AgentOperationIntent.unquoted(goal).split("内容|备注|发送|发消息",2)[0];
  String actual=actualVenue==null?"":actualVenue.replaceFirst("(?i)^FitChain\\s*","");
  for(var venue:venues.findAll()){String name=venue.getName(),label=name==null?"":name.replaceFirst("(?i)^FitChain\\s*","");if(label.length()>1&&(header.contains(label)||name!=null&&header.contains(name))&&!label.equals(actual))throw com.gym.fitness.module.agent.AgentIdentity.invalid("收件人的门店与用户指定门店不一致");}
 }
 @Transactional(readOnly=true)
 public boolean verify(String type,AgentMutationReceiptService.Receipt r,String goal,boolean member){
  try{
   if(!AgentGoalBinding.nestedMatches(goal,r.path()))return false;
   if(!AgentOperationIntent.permits(goal,r.method(),r.path()))return false;
   guard(goal,r.before(),r.request());
   JsonNode data=r.result().path("data"),actual;long id=data.path("id").asLong();
   if(r.before().path("id").asLong()>0&&!r.before().path("name").asText().isBlank()&&!AgentGoalBinding.labelMatches(goal,r.before().path("name").asText(),r.before().path("id").asLong(),r.before().path("kind").asText(type)))return false;
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
     if(r.path().endsWith("/feedback")){long sessionId=pathId(r.path(),"sessions");var s=training.sessionEvidence(id,sessionId,member);return s.id()==sessionId&&s.feedbackRating()!=null&&s.feedbackRating()==r.request().path("rating").asInt()&&hashMatches(r.request(),"contentHash",json.valueToTree(s.feedbackContent()));}
     if(r.path().endsWith("/void")){long sessionId=pathId(r.path(),"sessions");var s=training.sessionEvidence(id,sessionId,member);return s.id()==sessionId&&"VOIDED".equals(s.status());}
     if(r.path().endsWith("/sessions")){
      if(!r.before().has("sessionHighWatermark")||!r.request().path("exercises").isArray())return false;
      long recorded=data.path("recordedSessionId").asLong();if(recorded<=r.before().path("sessionHighWatermark").asLong())return false;
      for(var s:java.util.List.of(training.sessionEvidence(id,recorded,member))){
       if(s.id()!=recorded||!"COMPLETED".equals(s.status()))continue;
       var saved=json.valueToTree(s);if(!matches(r.request(),saved,"performedOn","durationMinutes","effort","bookingId")||!hashMatches(r.request(),"noteHash",saved.path("note")))continue;
       var requested=r.request().path("exercises");if(requested.size()!=s.exercises().size())continue;
       var ids=new java.util.HashSet<Long>();boolean equal=true;
       for(var e:requested){long exerciseId=e.path("exerciseId").asLong();JsonNode found=null;for(var a:saved.path("exercises"))if(a.path("exerciseId").asLong()==exerciseId)found=a;
        if(exerciseId<=0||!ids.add(exerciseId)||found==null||!matches(e,found,"exerciseId","status","sets","reps","weightKg","minutes")||!hashMatches(e,"noteHash",found.path("note"))){equal=false;break;}}
       if(equal)return true;
      }return false;
     }
     if(r.request().has("exercises")&&!r.path().endsWith("/sessions")){
      if(!matches(r.request(),actual.path("plan"),"title","memberId","goal","startDate","endDate"))return false;
      var expectedExercises=r.request().path("exercises");var savedExercises=actual.path("exercises");if(!savedExercises.isArray()||savedExercises.size()!=expectedExercises.size())return false;
      for(int i=0;i<expectedExercises.size();i++)if(!matches(expectedExercises.get(i),savedExercises.get(i),"name","mode","sets","reps","weightKg","minutes")||!hashMatches(expectedExercises.get(i),"noteHash",savedExercises.get(i).path("note")))return false;
     }
     return matches(data.path("plan"),actual.path("plan"),"id","title","memberId","status","version")&&hashMatches(r.request(),"noteHash",actual.path("plan").path("note"))&&(!r.path().endsWith("/publish")||"ACTIVE".equals(actual.path("plan").path("status").asText()))&&(!r.path().endsWith("/archive")||"ARCHIVED".equals(actual.path("plan").path("status").asText()));
    case "trial": case "lead":
     id=data.path("lead").path("id").asLong(pathId(r.path(),"leads"));if(id<=0)return false;
     actual=json.valueToTree(leads.detail(id));
     if(!matches(data.path("lead"),actual.path("lead"),"id","name","venueId")||!leadGoalValues(goal,r.request()))return false;
     if(r.path().endsWith("/follow-ups")){
      if(!r.before().has("eventHighWatermark")||!r.request().has("contentHash")||!r.request().has("method")||!r.request().has("outcome"))return false;
      for(var event:actual.path("events")){
       if(event.path("id").asLong()<=r.before().path("eventHighWatermark").asLong()||find(data.path("events"),event.path("id").asLong()).isMissingNode()||!"FOLLOW_UP".equals(event.path("type").asText())||!matches(r.request(),event,"outcome","nextFollowAt"))continue;
       String prefix=r.request().path("method").asText()+"：",content=event.path("content").asText();
       if(content.startsWith(prefix)&&hashMatches(r.request(),"contentHash",json.valueToTree(content.substring(prefix.length()).trim())))return true;
      }return false;
     }
     if(r.path().matches("/api/v1/leads/\\d+/trials(?:/\\d+/result)?")){
      long trialId=pathId(r.path(),"trials");
      if(trialId==0&&!r.before().has("trialHighWatermark"))return false;
      for(var candidate:actual.path("trials")){
       long candidateId=candidate.path("id").asLong();
       if(trialId>0?candidateId!=trialId:candidateId<=r.before().path("trialHighWatermark").asLong())continue;
       if(find(data.path("trials"),candidateId).isMissingNode()||!matches(r.request(),candidate,"hostId","startsAt","endsAt","status"))continue;
       if(trialId>0){if(hashMatches(r.request(),"noteHash",candidate.path("resultNote")))return true;}
       else if(hashMatches(r.request(),"contentHash",candidate.path("content")))return true;
      }return false;
     }
     if(!(r.path().equals("/api/v1/leads")||r.path().endsWith("/profile")))return false;
     return matches(r.request(),actual.path("lead"),"name","intent","source","ownerId","venueId","nextFollowAt")&&hashMatches(r.request(),"noteHash",actual.path("lead").path("note"))&&hashMatches(r.request(),"phoneHash",actual.path("lead").path("phone"));
    case "retention":
     id=pathId(r.path(),"members");if(id<=0)return false;long follow=data.path("id").asLong();return advisor.memberFollowUps(id).stream().anyMatch(f->f.id()==follow);
    case "member":
     if(id==0)id=pathId(r.path(),"members");if(id<=0)return false;actual=json.valueToTree(members.get(id));return actual.path("id").asLong()==id&&matches(data,actual,"realName","status");
    default:return false;
   }
  }catch(com.gym.fitness.common.exception.BusinessException denied){return false;}
 }
 private long maxId(JsonNode rows){long id=0;for(var row:rows)id=Math.max(id,row.path("id").asLong());return id;}
 private boolean leadGoalValues(String goal,JsonNode request){
  for(String part:goal.split("[，,]")){
   part=part.trim();if(part.startsWith("内容")||part.startsWith("备注"))continue;
   var intent=Pattern.compile("意向(?:改为|改成|调整为|设为|为|是|[:：])\\s*(高|中|低)").matcher(part);String value=intent.find()?intent.group(1):part.matches("[高中低]意向")?part.substring(0,1):null;
   if(value!=null&&!java.util.Map.of("高","HIGH","中","MEDIUM","低","LOW").get(value).equals(request.path("intent").asText()))return false;
   if(part.startsWith("结果")){String outcome=part.replaceFirst("^结果(?:为|是)?[：: ]*","");String expected=java.util.Map.of("愿意体验","INTERESTED","有意向","INTERESTED","已联系","CONTACTED","未联系上","NO_CONTACT","已到店","VISITED","无意向","DECLINED").get(outcome);if(expected==null||!expected.equals(request.path("outcome").asText()))return false;}
  }
  if(request.has("method")&&goal.split("内容|备注",2)[0].contains("电话")&&!"CALL".equals(request.path("method").asText()))return false;
  for(String field:java.util.List.of("content","note")){
   String label=field.equals("content")?"(?:跟进内容|体验内容|内容)":"备注";
   var text=Pattern.compile("(?:^|[，,])\\s*"+label+"(?:为|是)?[：: ]*(.*?)(?=[，,](?:结果|接待顾问|下次|时间|评分|其他|不要)|$)").matcher(goal);
   if(text.find()){String expected=text.group(1).trim();if(expected.length()>1){char a=expected.charAt(0),b=expected.charAt(expected.length()-1);if(a==0x22&&b==0x22||a==0x201C&&b==0x201D||a==0x300C&&b==0x300D)expected=expected.substring(1,expected.length()-1);}if(request.has(field)){if(!expected.equals(request.path(field).asText().trim()))return false;}else if(!request.has(field+"Hash")||!hashMatches(request,field+"Hash",json.valueToTree(expected)))return false;}
  }return true;
 }
 private boolean hashMatches(JsonNode expected,String key,JsonNode actual){
  if(!expected.hasNonNull(key))return true;
  try{var normalized=actual.isMissingNode()||actual.isNull()||actual.isTextual()&&actual.asText().isBlank()?json.nullNode():actual;return expected.path(key).asText().equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(normalized))));}catch(Exception e){return false;}
 }
 private boolean matches(JsonNode expected,JsonNode actual,String...fields){if(actual.isMissingNode()||actual.isNull())return false;for(var field:fields)if(expected.hasNonNull(field)){var a=expected.get(field);var b=actual.path(field);if(a.isNumber()&&b.isNumber()){if(a.decimalValue().compareTo(b.decimalValue())!=0)return false;}else if(!a.asText().equals(b.asText()))return false;}return true;}
 private JsonNode find(JsonNode value,long id){if(id<=0)return com.fasterxml.jackson.databind.node.MissingNode.getInstance();if(value.isArray()){for(var v:value)if(v.path("id").asLong()==id)return v;}if(value.isObject()&&value.has("records"))return find(value.get("records"),id);return com.fasterxml.jackson.databind.node.MissingNode.getInstance();}
 private long pathId(String path,String resource){var m=Pattern.compile("/"+resource+"/(\\d+)(?:/|$)").matcher(path);return m.find()?Long.parseLong(m.group(1)):0;}
}
