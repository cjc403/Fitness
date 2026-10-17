package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.dto.*;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.booking.service.BookingService;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.course.service.CourseService;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.member.service.*;
import com.gym.fitness.module.lead.dto.LeadDtos;
import com.gym.fitness.module.lead.service.LeadService;
import com.gym.fitness.module.workout.dto.TrainingPlanDtos;
import com.gym.fitness.module.workout.service.*;
import com.gym.fitness.module.analytics.service.AnalyticsService;
import com.gym.fitness.module.cashshift.service.CashShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.gym.fitness.module.agent.AgentDtos.*;
import static com.gym.fitness.module.agent.AgentIdentity.*;

/** Narrow projections and existing domain services keep the Agent out of persistence/business rules. */
@Service @RequiredArgsConstructor
public class AgentBusiness {
 private final AgentIdentity identity;private final AgentTools tools;private final ObjectMapper json;
 private final BookingService bookings;private final BookingRepository bookingRepository;private final CourseService courses;
 private final CourseScheduleRepository schedules;private final MemberRepository members;private final MemberService memberService;
 private final MemberPortalService portal;private final TrainingPlanService training;private final TrainingPlanAccess trainingAccess;
 private final LeadService leads;private final RetentionService retention;private final CashShiftService cash;private final AnalyticsService analytics;
 private final SysUserRepository users;
 private final com.gym.fitness.module.finance.service.EntitlementValueService values;
 private final com.gym.fitness.common.util.VenueMutationLock venueLock;
 private final jakarta.persistence.EntityManager em;

 @Transactional(readOnly=true)
 public ToolResult query(String name,JsonNode args){
  var tool=tools.validate(name,args,false);var actor=identity.actor();String role=actor.getRole().getCode();
  LocalDate from=day(args,"from",LocalDate.now()),to=day(args,"to",LocalDate.now().plusDays(7));
  if(from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>92)throw invalid("查询周期须在93天内且开始日期不晚于结束日期");
  String keyword=args.path("keyword").asText("");int page=args.path("page").asInt(1);
  Object value=switch(name){
   case "profile" -> Map.of("userId",actor.getId(),"name",Objects.toString(actor.getRealName(),""),"role",role,"venueId",identity.contextVenue());
   case "courses" -> "MEMBER".equals(role)?portal.schedules().stream().filter(s->!s.getStartTime().toLocalDate().isBefore(from)&&!s.getStartTime().toLocalDate().isAfter(to)&&match(s.getCourseName(),keyword)).toList():courses.listSchedules(from,to).stream().filter(s->!"COACH".equals(role)||actor.getId().equals(s.getCoachId())).filter(s->match(s.getCourse()==null?"":s.getCourse().getName(),keyword)).map(this::scheduleView).toList();
   case "bookings" -> "MEMBER".equals(role)?portal.bookings().stream().filter(b->match(b.getCourseName(),keyword)).toList():bookings.list().stream().map(BookingResponse::from).filter(b->match(b.member()==null?"":b.member().realName(),keyword)||match(b.schedule()==null||b.schedule().course()==null?"":b.schedule().course().name(),keyword)).toList();
   case "members" -> memberService.list(keyword,page,20,null);
   case "training_plans" -> training.workbench(null,"ALL",keyword,page,20,"MEMBER".equals(role));
   case "students" -> training.students(keyword,page,20);
   case "leads" -> args.has("leadId")?leads.detail(args.get("leadId").asLong()):leads.workbench(null,"ALL","ALL",keyword,page,20);
   case "lead_options" -> leads.options(null);
   case "retention" -> retention.workbench("ALL",keyword);
   case "cash_summary" -> cash.workbench(null,day(args,"day",LocalDate.now()),"ALL",page,20);
   case "analytics" -> analytics.overview(from,to);
   default -> throw denied();
  };
  var slice=AgentQueryPaging.slice(name,json.valueToTree(value),page);boolean[] cut={false};var data=project(slice.data(),cut,new int[]{24000},0,false);var pagination=slice.pagination()==null?null:slice.pagination().contentTruncated(cut[0]);return new ToolResult(name,tool.label(),data,"FitChain 本地业务数据库",LocalDateTime.now(),cut[0]||pagination!=null&&pagination.total()>pagination.returned(),pagination);
 }
 private static boolean match(String s,String keyword){return keyword.isEmpty()||(s!=null&&s.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));}
 /** A fixed scoped lookup for receipt verification, independent of display paging. */
 @Transactional(readOnly=true)
 public JsonNode bookingEvidence(long id,long scheduleId){
  tools.validate("bookings",json.createObjectNode(),false);var actor=identity.actor();
  Object values="MEMBER".equals(actor.getRole().getCode())?portal.bookings():bookings.list().stream().map(BookingResponse::from).toList();
  for(var row:json.valueToTree(values)){
   long schedule=row.path("scheduleId").asLong(row.path("schedule").path("id").asLong());
   if(id>0?row.path("id").asLong()==id:scheduleId>0&&schedule==scheduleId&&Set.of("BOOKED","WAITLISTED").contains(row.path("status").asText()))return clean(row).data();
  }
  return MissingNode.getInstance();
 }
 private static LocalDate day(JsonNode a,String key,LocalDate fallback){return a.has(key)?LocalDate.parse(a.get(key).asText()):fallback;}
 private Map<String,Object> scheduleView(CourseScheduleEntity s){var v=new LinkedHashMap<String,Object>();v.put("scheduleId",s.getId());v.put("courseName",s.getCourse()==null?"课程":s.getCourse().getName());v.put("type",s.getCourse()==null?null:s.getCourse().getType());v.put("coachId",s.getCoachId());v.put("coachName",s.getCoachName());v.put("venueId",s.getVenueId());v.put("startTime",s.getScheduledStartTime());v.put("endTime",s.getScheduledEndTime());v.put("maxStudents",s.getMaxStudents());v.put("enrolledCount",s.getEnrolledCount());v.put("status",s.getStatus());return v;}

 /** A live business precondition snapshot; confirm uses the domain's member -> venue -> schedule lock order. */
 public JsonNode snapshot(String tool,JsonNode a,boolean lock){
  var actor=identity.actor();
  if(Set.of("book_course","cancel_booking").contains(tool)){
   com.gym.fitness.module.booking.entity.BookingEntity b=null;
   Long memberId;Long scheduleId;
   if("cancel_booking".equals(tool)){b=bookingRepository.findById(a.path("bookingId").asLong()).orElseThrow(AgentIdentity::denied);memberId=b.getMemberId();scheduleId=b.getScheduleId();}
   else{memberId="MEMBER".equals(identity.role())?members.findByUserId(actor.getId()).orElseThrow(AgentIdentity::denied).getId():a.path("memberId").asLong();scheduleId=a.path("scheduleId").asLong();}
   if(lock){var member=values.lockMemberServices(memberId);venueLock.acquire(member.getVenueId());var schedule=schedules.findByIdForUpdate(scheduleId).orElseThrow(AgentIdentity::denied);em.refresh(schedule,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(schedule.getCourse()!=null)em.refresh(schedule.getCourse(),jakarta.persistence.LockModeType.PESSIMISTIC_READ);if(b!=null)em.refresh(b,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);}
   var s=schedules.findById(scheduleId).orElseThrow(AgentIdentity::denied);var snapshot=new LinkedHashMap<>(scheduleView(s));snapshot.remove("enrolledCount");snapshot.put("memberId",memberId);snapshot.put("fee",s.getCourse()==null?null:s.getCourse().getFee());
   if(b!=null){snapshot.put("bookingId",b.getId());snapshot.put("bookedAt",b.getBookedAt());snapshot.put("bookingStatus",b.getStatus());}
   return json.valueToTree(snapshot);
  }
  // Trial writes carry a domain version; training inputs are immutable and its service locks/rechecks the relation.
  return json.valueToTree(Map.of("tool",tool,"actorId",actor.getId(),"venueId",identity.contextVenue()));
 }

 @Transactional(readOnly=true)
 public String preview(String name,JsonNode a){
  tools.validate(name,a,true);var actor=identity.actor();String role=actor.getRole().getCode();
  return switch(name){
   case "book_course" -> {
    var s=schedules.findById(a.path("scheduleId").asLong()).orElseThrow(AgentIdentity::denied);
    var m="MEMBER".equals(role)?members.findByUserId(actor.getId()).orElseThrow(AgentIdentity::denied):members.findById(a.path("memberId").asLong()).orElseThrow(AgentIdentity::denied);
    if("MEMBER".equals(role)){
     if(portal.schedules().stream().noneMatch(x->x.getScheduleId().equals(s.getId())))throw denied();
    }else if(!"SUPER_ADMIN".equals(role)&&(!actor.getVenueId().equals(m.getVenueId())||!actor.getVenueId().equals(s.getVenueId())))throw denied();
    if(!"ACTIVE".equals(m.getStatus())||Boolean.TRUE.equals(m.getBlacklistFlag()))throw invalid("会员当前不可预约");
    if(!"SCHEDULED".equals(s.getStatus())||!s.getScheduledStartTime().isAfter(LocalDateTime.now()))throw invalid("该课程当前不可预约");
    if(s.getEnrolledCount()!=null&&s.getEnrolledCount()>=s.getMaxStudents())throw invalid("课程名额已满");
    yield "为「"+m.getRealName()+"」预约「"+(s.getCourse()==null?"课程":s.getCourse().getName())+"」，时间："+s.getScheduledStartTime()+"，门店ID："+s.getVenueId()+"。确认时再次核验名额和权益；付费课程沿用原预约权益预占规则。";
   }
   case "cancel_booking" -> {
    var b=bookingRepository.findById(a.path("bookingId").asLong()).orElseThrow(AgentIdentity::denied);
    if("MEMBER".equals(role)){var m=members.findByUserId(actor.getId()).orElseThrow(AgentIdentity::denied);if(!m.getId().equals(b.getMemberId()))throw denied();}
    else if(!"SUPER_ADMIN".equals(role)&&!actor.getVenueId().equals(b.getSchedule().getVenueId()))throw denied();
    if(!List.of("BOOKED","WAITLISTED").contains(b.getStatus()))throw invalid("该预约当前不可取消");
    if(b.getSchedule().getScheduledStartTime().isBefore(LocalDateTime.now().plusHours(2))&&(!List.of("MANAGER","SUPER_ADMIN").contains(role)||!a.hasNonNull("reason")))throw invalid("不足两小时的临时取消须由店长填写原因办理");
    yield "取消预约 #"+b.getId()+"，课程时间："+b.getSchedule().getScheduledStartTime()+"。"+(a.has("reason")?"原因："+a.get("reason").asText():"");
   }
   case "book_trial" -> {
    var detail=json.valueToTree(leads.detail(a.path("leadId").asLong()));var lead=detail.path("lead");
    if(lead.path("version").asLong()!=a.path("expectedVersion").asLong())throw invalid("潜客资料已更新，请重新查询版本");
    if(List.of("CONVERTED","LOST").contains(lead.path("stage").asText()))throw invalid("该潜客当前不可安排体验");
    var host=users.findById(a.path("hostId").asLong()).filter(u->"ACTIVE".equals(u.getStatus())&&u.getRole()!=null&&"SALES".equals(u.getRole().getCode())&&u.getVenueId().equals(lead.path("venueId").asLong())).orElseThrow(AgentIdentity::denied);
    var start=LocalDateTime.parse(a.path("startsAt").asText());var end=LocalDateTime.parse(a.path("endsAt").asText());var duration=Duration.between(start,end);
    if(!start.isAfter(LocalDateTime.now())||duration.compareTo(Duration.ofMinutes(15))<0||duration.compareTo(Duration.ofMinutes(180))>0)throw invalid("体验须安排在未来，时长15至180分钟");
    yield "为「"+lead.path("name").asText()+"」安排体验，接待："+host.getRealName()+"，时间："+start+" 至 "+end+"；内容："+a.path("content").asText();
   }
   case "training_draft" -> {
    long member=a.path("memberId").asLong();if(!trainingAccess.related(actor,member))throw denied();
    var start=LocalDate.parse(a.path("startDate").asText());var end=LocalDate.parse(a.path("endDate").asText());
    if(start.isAfter(end)||ChronoUnit.DAYS.between(start,end)>365||start.isBefore(LocalDate.now().minusDays(365))||end.isAfter(LocalDate.now().plusDays(365)))throw invalid("训练周期不正确");
    for(var e:a.path("exercises")){
     if("STRENGTH".equals(e.path("mode").asText())){if(!e.hasNonNull("sets")||!e.hasNonNull("reps")||e.has("minutes"))throw invalid("力量动作须填写组数和次数，不能填写分钟");if(e.has("weightKg")&&Math.max(0,e.get("weightKg").decimalValue().stripTrailingZeros().scale())>2)throw invalid("重量最多两位小数");}
     else if(!e.hasNonNull("minutes")||e.has("sets")||e.has("reps")||e.has("weightKg"))throw invalid("时长动作须填写分钟，不能填写力量数值");
    }
    yield "为学员 #"+member+" 创建「"+a.path("title").asText()+"」训练草稿，周期："+start+" 至 "+end+"，共 "+a.path("exercises").size()+" 个动作。仅保存草稿，后续由教练审核发布。";
   }
   default -> throw denied();
  };
 }
 /** Called only within the action's locked transaction. Existing services recheck live domain constraints. */
 public JsonNode execute(String tool,JsonNode a,String requestKey){
  tools.validate(tool,a,true);identity.actor();boolean self="MEMBER".equals(identity.role());
  Object result=switch(tool){
   case "book_course" -> {var request=new BookingRequest();request.setScheduleId(a.path("scheduleId").asLong());request.setEntitlementId(a.has("entitlementId")?a.get("entitlementId").asLong():null);if(self)yield BookingResponse.from(bookings.createForCurrentMember(request.getScheduleId(),request.getEntitlementId()));request.setMemberId(a.path("memberId").asLong());yield BookingResponse.from(bookings.create(request));}
   case "cancel_booking" -> BookingResponse.from(self?bookings.cancelForCurrentMember(a.path("bookingId").asLong()):bookings.cancel(a.path("bookingId").asLong(),a.has("reason")?a.get("reason").asText():null));
   case "book_trial" -> leads.bookTrial(a.path("leadId").asLong(),new LeadDtos.TrialRequest(a.path("expectedVersion").asLong(),a.path("hostId").asLong(),LocalDateTime.parse(a.path("startsAt").asText()),LocalDateTime.parse(a.path("endsAt").asText()),a.path("content").asText(),requestKey));
   case "training_draft" -> {var input=((ObjectNode)a).deepCopy();input.put("idempotencyKey",requestKey);yield training.create(json.convertValue(input,TrainingPlanDtos.Create.class));}
   default -> throw denied();
  };
  return clean(result).data();
 }
 public record Projection(JsonNode data,boolean truncated) {}
 private static final Set<String> MODEL_PRIVATE=Set.of("name","realname","membername","coachname","ownername","actorname","hostname","memberno","note","content","description","reason","cancelreason","resultnote","feedbackcontent","traininggoalnote","lastfollowcontent","events");
 public Object modelContext(ToolResult result){var minimal=withoutPersonalContext(result.data());boolean[] truncated={result.truncated()};var data=project(minimal,truncated,new int[]{6000},0);var context=new LinkedHashMap<String,Object>();context.put("tool",result.tool());context.put("data",data);context.put("asOf",result.asOf());context.put("truncated",truncated[0]);if(result.pagination()!=null)context.put("pagination",result.pagination());return context;}
 private JsonNode withoutPersonalContext(JsonNode n){if(n.isObject()){var o=json.createObjectNode();n.fields().forEachRemaining(e->{if(!MODEL_PRIVATE.contains(e.getKey().toLowerCase(Locale.ROOT)))o.set(e.getKey(),withoutPersonalContext(e.getValue()));});return o;}if(n.isArray()){var a=json.createArrayNode();n.forEach(e->a.add(withoutPersonalContext(e)));return a;}return n;}
 private static final Set<String> PRIVATE=Set.of("phone","email","idcard","address","birthday","password","passwordhash","accountusername","accountstatus","traininggoalnote");
 public Projection clean(Object source){boolean[] truncated={false};int[] budget={24000};var data=project(json.valueToTree(source),truncated,budget,0);return new Projection(data,truncated[0]);}
 private JsonNode project(JsonNode n,boolean[] cut,int[] budget,int depth){return project(n,cut,budget,depth,true);}
 private JsonNode project(JsonNode n,boolean[] cut,int[] budget,int depth,boolean partialPages){
  if(n==null)return NullNode.instance;
  if(depth>12||budget[0]<=0){cut[0]=true;return TextNode.valueOf("内容已截断，请打开业务页面查看完整记录");}
  if(n.isObject()){var o=json.createObjectNode();var fields=n.fields();while(fields.hasNext()){var e=fields.next();if(PRIVATE.contains(e.getKey().toLowerCase(Locale.ROOT)))continue;budget[0]-=e.getKey().length()+4;o.set(e.getKey(),project(e.getValue(),cut,budget,depth+1,partialPages));if(budget[0]<=0){cut[0]=true;break;}}if(partialPages&&n.has("total")&&n.has("records")&&n.get("records").isArray()&&n.path("total").asLong()>n.get("records").size())cut[0]=true;return o;}
  if(n.isArray()){var a=json.createArrayNode();int i=0;for(var e:n){if(i++>=20||budget[0]<=0){cut[0]=true;break;}a.add(project(e,cut,budget,depth+1,partialPages));}return a;}
  if(n.isTextual()){String s=n.asText();int len=Math.max(0,Math.min(1000,budget[0]));if(s.length()>len){s=s.substring(0,len)+"…";cut[0]=true;}budget[0]-=s.length()+2;return TextNode.valueOf(s);}
  budget[0]-=n.toString().length()+1;return n;
 }
}
