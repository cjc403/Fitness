package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentMutationReceiptTest {
    @Test void deletingACourseCannotBorrowAnotherRequestedCourseId(){
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：删除课程#2");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),task.version(),"document-a","DELETE","/api/v1/admin/courses/1",json.nullNode()));
    }
    @Test void duplicateContactNamesNeedAnExplicitAccountIdBeforeAnyPreview(){
        var chat=org.mockito.Mockito.mock(com.gym.fitness.module.chat.service.ChatService.class);var a=new com.gym.fitness.module.chat.dto.ChatDtos.Contact(2L,"李四","COACH","教练","翠湖店",false,0);var b=new com.gym.fitness.module.chat.dto.ChatDtos.Contact(3L,"李四","COACH","教练","滨江店",false,0);org.mockito.Mockito.when(chat.contacts()).thenReturn(new com.gym.fitness.module.chat.dto.ChatDtos.ContactGroups(List.of(a,b),List.of()));
        var domains=org.mockito.Mockito.mock(AgentDomainVerifier.class);org.mockito.Mockito.when(domains.before(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenAnswer(i->json.createObjectNode());var service=new AgentMutationReceiptService(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),json,tasks,chat,domains);task=tasks.revise(me,task.id(),0,"document-a","改为任务：给李四发送：你好");var body=json.valueToTree(Map.of("recipientUserId",2,"content","你好"));
        assertThrows(RuntimeException.class,()->service.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/messages/send",body));
        task=tasks.revise(me,task.id(),task.version(),"document-a","改为任务：给用户编号#2发送：你好");assertNotNull(service.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/messages/send",body));
    }
    @Test void recordedSessionIdComesFromTheCommittedActorAndIdempotencyEventNotTheFirstPage(){
        jdbc.execute("CREATE TABLE training_plan_event(plan_id BIGINT,actor_id BIGINT,idempotency_key VARCHAR(80),type VARCHAR(30),session_id BIGINT)");var domains=org.mockito.Mockito.mock(AgentDomainVerifier.class);org.mockito.Mockito.when(domains.before(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(json.valueToTree(Map.of("kind","training","id",2,"name","基础训练","sessionHighWatermark",20)));
        var service=new AgentMutationReceiptService(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),json,tasks,org.mockito.Mockito.mock(com.gym.fitness.module.chat.service.ChatService.class),domains);task=tasks.revise(me,task.id(),0,"document-a","改为任务：为训练计划#2登记今天训练");var body=json.valueToTree(Map.of("idempotencyKey","backdated","durationMinutes",30,"exercises",List.of()));var p=service.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/training-plans/2/sessions",body);service.approve(me,p.id(),task.version(),"document-a");
        var response=service.execute(me,p.id(),"POST",p.path(),body,()->{jdbc.update("INSERT INTO training_plan_event VALUES(2,1,'backdated','RECORD',99)");return json.valueToTree(Map.of("code",0,"data",Map.of("plan",Map.of("id",2),"sessions",Map.of("records",List.of()))));});
        assertFalse(response.path("data").has("recordedSessionId"));assertEquals(99,service.get(me,p.id()).result().path("data").path("recordedSessionId").asLong());
    }
    final ObjectMapper json=new ObjectMapper();final Session me=new Session(1L,"SUPER_ADMIN",1L,"session","/dashboard",List.of("ASSISTANT_USE","COURSE_WRITE"),null);
    JdbcTemplate jdbc;AgentTaskStore tasks;AgentMutationReceiptService receipts;AgentTaskStore.View task;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:receipts"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);var tx=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE venue(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO sys_user VALUES(1)");jdbc.update("INSERT INTO venue VALUES(1)");jdbc.execute("CREATE TABLE fixture_write(id BIGINT PRIMARY KEY,amount INT)");
        jdbc.execute("CREATE TABLE course(id BIGINT PRIMARY KEY,name VARCHAR(100),type VARCHAR(20),venue_id BIGINT,description VARCHAR(300),duration_minutes INT,max_capacity INT,fee DECIMAL(12,2),intensity VARCHAR(20))");
        jdbc.update("INSERT INTO course VALUES(1,'流瑜伽','GROUP',1,'课程',60,20,88,'LOW')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__agent_task_ledger.sql"),new ClassPathResource("db/migration/V25__agent_mutation_receipts.sql")).execute(ds);
        tasks=new AgentTaskStore(jdbc,tx,json);receipts=new AgentMutationReceiptService(jdbc,tx,json,tasks,org.mockito.Mockito.mock(com.gym.fitness.module.chat.service.ChatService.class),org.mockito.Mockito.mock(AgentDomainVerifier.class));task=tasks.create(me,"document-a","把流瑜伽价格改为99");
    }
    @Test void writesRequireSeparateHumanApprovalAndPermitIsConsumedExactlyOnce(){
        var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),body,()->{jdbc.update("INSERT INTO fixture_write VALUES(1,99)");return json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"fee",99)));}));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fixture_write",Integer.class));
        receipts.approve(me,p.id(),0,"document-a");
        var result=receipts.execute(me,p.id(),"PUT",p.path(),body,()->{jdbc.update("INSERT INTO fixture_write VALUES(1,99)");return json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"fee",99)));});
        assertEquals("SUCCEEDED",receipts.get(me,p.id()).status());assertEquals(99,result.path("data").path("fee").asInt());
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),body,()->json.createObjectNode()));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM fixture_write",Integer.class));
    }
    @Test void changedPayloadOrCancelledTaskCannotConsumeApproval(){
        var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),json.valueToTree(Map.of("fee",109)),()->json.createObjectNode()));
        tasks.cancel(me,task.id(),0,"document-a");assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),body,()->json.createObjectNode()));
    }
    @Test void businessFailureRollsBackBothWriteAndSuccessReceipt(){
        var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),body,()->{jdbc.update("INSERT INTO fixture_write VALUES(1,99)");throw new IllegalStateException("failure");}));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fixture_write",Integer.class));assertNotEquals("SUCCEEDED",receipts.get(me,p.id()).status());
    }
    @Test void aRejectedBusinessResponsePreservesItsReasonWithoutCommittingAnything(){
        var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),body,()->{jdbc.update("INSERT INTO fixture_write VALUES(1,99)");return json.valueToTree(Map.of("code",2600,"message","接待顾问在此时间已有体验预约，请调整时间或接待人"));}));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fixture_write",Integer.class));var failed=receipts.get(me,p.id());assertEquals("FAILED",failed.status());assertEquals(2600,failed.result().path("code").asInt());assertTrue(failed.result().path("message").asText().contains("已有体验预约"));
    }
    @Test void authenticationFinanceAndUnregisteredEndpointsAreRejected(){
        for(String path:List.of("/api/v1/auth/login","/api/v1/commercial/refunds","/api/v1/unknown"))assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","POST",path,json.createObjectNode()));
    }
    @Test void wrongCourseWrongAmountAndUnrequestedCapacityNeverReachApproval(){
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",109))));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",99,"maxCapacity",30))));
        jdbc.update("UPDATE course SET name='功能性力量' WHERE id=1");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",99))));
    }
    @Test void aSuccessfulFrozenRequestCannotBePreparedAgainAfterAnUncertainClientResponse(){
        var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");
        receipts.execute(me,p.id(),"PUT",p.path(),body,()->json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"fee",99))));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT",p.path(),body));
    }
    @Test void aReadOnlyGoalCannotAuthorizeUnrelatedMessageOrMemberCreation(){
        tasks.cancel(me,task.id(),0,"document-a");var read=tasks.create(me,"document-a","查看本周课程");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,read.id(),0,"document-a","POST","/api/v1/messages/send",json.valueToTree(Map.of("recipientUserId",2,"content","你好"))));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,read.id(),0,"document-a","POST","/api/v1/admin/members",json.valueToTree(Map.of("name","无关会员"))));
    }
    @Test void capacityOnlyChangeCannotSmuggleInAnUnrequestedPrice(){
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：把流瑜伽容量改成21");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),task.version(),"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",99,"maxCapacity",21))));
    }
    @Test void pricesForTwoNamedCoursesAreBoundIndividually(){
        jdbc.update("INSERT INTO course VALUES(2,'力量','GROUP',1,'课程',60,20,88,'LOW')");task=tasks.revise(me,task.id(),0,"document-a","改为任务：把流瑜伽价格改成99，把力量价格改成109");
        assertNotNull(receipts.prepare(me,task.id(),task.version(),"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",99))));
    }
    @Test void changingACancelReasonCannotConsumeTheFrozenPermitAndRawReasonIsNotStoredInTheSummary(){
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：取消预约编号#7");String target="/api/v1/admin/bookings/7/cancel?reason=%E5%90%8E%E5%8F%B0%E5%8F%96%E6%B6%88";
        var p=receipts.prepare(me,task.id(),task.version(),"document-a","PUT",target,json.nullNode());receipts.approve(me,p.id(),task.version(),"document-a");
        assertFalse(p.request().toString().contains("后台取消"));assertTrue(p.request().has("queryHash"));
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT","/api/v1/admin/bookings/7/cancel?reason=changed",json.nullNode(),()->{fail("must not invoke business");return null;}));
    }
    @Test void notesAreRepresentedByHashesAndBlankOptionalContentUsesTheStoredNullForm(){
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：新建潜客，姓名小李，备注私有备注");
        var p=receipts.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/leads",json.valueToTree(Map.of("name","小李","note","私有备注","content"," ")));
        assertTrue(p.request().hasNonNull("noteHash"));assertFalse(p.request().toString().contains("私有备注"));
        assertEquals("74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b",p.request().path("contentHash").asText());
    }
    @Test void duplicateCourseNamesRequireAnExplicitCourseIdBeforeApproval(){
        jdbc.update("INSERT INTO course VALUES(2,'流瑜伽','GROUP',1,'另一课程',60,20,88,'LOW')");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",99))));
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：把课程编号#1价格改成99");
        assertNotNull(receipts.prepare(me,task.id(),task.version(),"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",99))));
    }
    @Test void aLostResponseCannotBeReplayedByRegeneratingAnIdempotencyKey(){
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：新建潜客，姓名小李");
        var first=json.valueToTree(Map.of("name","小李","idempotencyKey","first-key-0001","expectedVersion",0));
        var p=receipts.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/leads",first);receipts.approve(me,p.id(),task.version(),"document-a");
        receipts.execute(me,p.id(),"POST",p.path(),first,()->json.valueToTree(Map.of("code",0,"data",Map.of("lead",Map.of("id",9,"name","小李")))));
        var replay=json.valueToTree(Map.of("name","小李","idempotencyKey","regenerated-key-0002","expectedVersion",1));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),task.version(),"document-a","POST",p.path(),replay));
    }
    @Test void aNegatedBookingInstructionCannotCreateAPermit(){
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：不要预约流瑜伽，只查询本周课程");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/member/bookings",json.valueToTree(Map.of("scheduleId",7))));
    }
    private void boundary(String fault){
        var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");
        if(fault.equals("expired"))jdbc.update("UPDATE agent_task_receipt SET expires_at=? WHERE id=?",java.time.LocalDateTime.now().minusMinutes(1),p.id());
        if(fault.equals("document"))tasks.recover(me,task.id(),0,"document-b");
        var actor=switch(fault){case "user"->new Session(2L,me.role(),1L,"session",me.home(),List.of(),null);case "venue"->new Session(1L,me.role(),2L,"session",me.home(),List.of(),null);case "session"->new Session(1L,me.role(),1L,"other-session",me.home(),List.of(),null);default->me;};
        var count=new java.util.concurrent.atomic.AtomicInteger();assertThrows(RuntimeException.class,()->receipts.execute(actor,p.id(),fault.equals("method")?"POST":"PUT",fault.equals("path")?"/api/v1/admin/courses/2":p.path(),body,()->{count.incrementAndGet();return json.valueToTree(Map.of("code",0));}));assertEquals(0,count.get());
    }
    @Test void changingTheFrozenHttpMethodCannotReachBusiness(){boundary("method");}
    @Test void changingTheFrozenHttpPathCannotReachBusiness(){boundary("path");}
    @Test void anExpiredApprovalCannotReachBusiness(){boundary("expired");}
    @Test void aForeignUserCannotConsumeThePermit(){boundary("user");}
    @Test void aForeignVenueCannotConsumeThePermit(){boundary("venue");}
    @Test void aDifferentLoginSessionCannotConsumeThePermit(){boundary("session");}
    @Test void recoveringInANewDocumentInvalidatesTheOldApprovedRequest(){boundary("document");}
    @Test void wrongRecipientOrMessageCannotEvenProduceAnApprovablePermit(){
        var chat=org.mockito.Mockito.mock(com.gym.fitness.module.chat.service.ChatService.class);var domains=org.mockito.Mockito.mock(AgentDomainVerifier.class);
        org.mockito.Mockito.when(chat.contacts()).thenReturn(new com.gym.fitness.module.chat.dto.ChatDtos.ContactGroups(List.of(new com.gym.fitness.module.chat.dto.ChatDtos.Contact(2L,"李四","MANAGER","店长","总部",false,0),new com.gym.fitness.module.chat.dto.ChatDtos.Contact(3L,"王五","COACH","教练","总部",false,0)),List.of()));
        org.mockito.Mockito.when(domains.before(org.mockito.Mockito.any(),org.mockito.Mockito.anyString(),org.mockito.Mockito.anyString(),org.mockito.Mockito.any())).thenAnswer(i->json.createObjectNode());
        var service=new AgentMutationReceiptService(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),json,tasks,chat,domains);task=tasks.revise(me,task.id(),0,"document-a","改为任务：给李四发送：你好");
        assertThrows(RuntimeException.class,()->service.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/messages/send",json.valueToTree(Map.of("recipientUserId",3,"content","你好"))));
        assertThrows(RuntimeException.class,()->service.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/messages/send",json.valueToTree(Map.of("recipientUserId",2,"content","别的内容"))));
        assertNotNull(service.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/messages/send",json.valueToTree(Map.of("recipientUserId",2,"content","你好"))));
    }
    @Test void aCorrectParentCannotAuthorizeAnotherTrainingSessionOrTrial(){
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：为训练计划#2中训练记录#9提交反馈，评分5，内容很好");var feedback=json.valueToTree(Map.of("rating",5,"content","很好"));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/member/training-plans/2/sessions/8/feedback",feedback));
        assertNotNull(receipts.prepare(me,task.id(),task.version(),"document-a","POST","/api/v1/member/training-plans/2/sessions/9/feedback",feedback));
        tasks.cancel(me,task.id(),task.version(),"document-a");task=tasks.create(me,"document-a","把潜客小李的体验编号#3记录为已到店");var result=json.valueToTree(Map.of("status","ATTENDED","note","已到店"));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","POST","/api/v1/leads/2/trials/4/result",result));
        assertNotNull(receipts.prepare(me,task.id(),0,"document-a","POST","/api/v1/leads/2/trials/3/result",result));
    }
}
