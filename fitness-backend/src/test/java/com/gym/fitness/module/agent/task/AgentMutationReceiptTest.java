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
}
