package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real row locks, exclusively inside the marked V2 test database. */
@EnabledIfEnvironmentVariable(named="FITCHAIN_AGENT_V2_PG",matches="true")
class AgentTaskPostgresTest {
 final ObjectMapper json=new ObjectMapper();
 final Session me=new Session(1L,"SUPER_ADMIN",1L,"session","/dashboard",List.of("ASSISTANT_USE","COURSE_WRITE"),null);
 JdbcTemplate admin,jdbc;String schema;AgentTaskStore tasks;AgentMutationReceiptService receipts;AgentTaskStore.View task;
 @BeforeEach void setup(){
  String url=System.getenv("FITCHAIN_AGENT_V2_PG_URL");
  assertEquals("jdbc:postgresql://127.0.0.1:55457/agent_v2_junit",url,"Refuse any non-exclusive target");
  String password=System.getenv("FITCHAIN_AGENT_V2_PG_PASSWORD");assertNotNull(password);
  var root=new DriverManagerDataSource(url,"agent_v2",password);admin=new JdbcTemplate(root);
  assertEquals("agent_v2_20261017",admin.queryForObject("SELECT id FROM public.codex_agent_v2_isolation_marker",String.class));
  schema="agent_v2_"+UUID.randomUUID().toString().replace("-","");admin.execute("CREATE SCHEMA "+schema);
  var ds=new DriverManagerDataSource(url+"?currentSchema="+schema,"agent_v2",password);jdbc=new JdbcTemplate(ds);var tx=new DataSourceTransactionManager(ds);
  jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE venue(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO sys_user VALUES(1)");jdbc.update("INSERT INTO venue VALUES(1)");
  jdbc.execute("CREATE TABLE course(id BIGINT PRIMARY KEY,name VARCHAR(100),type VARCHAR(20),venue_id BIGINT,description VARCHAR(300),duration_minutes INT,max_capacity INT,fee DECIMAL(12,2),intensity VARCHAR(20))");jdbc.update("INSERT INTO course VALUES(1,'流瑜伽','GROUP',1,'课程',60,20,88,'LOW')");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__agent_task_ledger.sql"),new ClassPathResource("db/migration/V25__agent_mutation_receipts.sql")).execute(ds);
  tasks=new AgentTaskStore(jdbc,tx,json);receipts=new AgentMutationReceiptService(jdbc,tx,json,tasks,mock(com.gym.fitness.module.chat.service.ChatService.class),mock(AgentDomainVerifier.class));task=tasks.create(me,"document-a","把流瑜伽价格改为99");
 }
 @AfterEach void cleanup(){if(admin!=null&&schema!=null&&schema.matches("agent_v2_[a-f0-9]{32}"))admin.execute("DROP SCHEMA "+schema+" CASCADE");}
 int race(Callable<Boolean> job)throws Exception{var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);try{var a=pool.submit(()->{start.await();return job.call();});var b=pool.submit(()->{start.await();return job.call();});start.countDown();return (a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0);}finally{pool.shutdownNow();}}
 @Test void concurrentPrepareHasExactlyOneWinner()throws Exception{
  assertEquals(1,race(()->{try{receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",json.valueToTree(Map.of("fee",99)));return true;}catch(RuntimeException e){return false;}}));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM agent_task_receipt",Integer.class));
 }
 @Test void concurrentConsumptionCommitsBusinessAndReceiptExactlyOnce()throws Exception{
  var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");var writes=new AtomicInteger();
  assertEquals(1,race(()->{try{receipts.execute(me,p.id(),"PUT",p.path(),body,()->{jdbc.update("UPDATE course SET fee=99 WHERE id=1");writes.incrementAndGet();return json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"fee",99)));});return true;}catch(RuntimeException e){return false;}}));
  assertEquals(1,writes.get());assertEquals("SUCCEEDED",receipts.get(me,p.id()).status());assertEquals(99,jdbc.queryForObject("SELECT fee FROM course WHERE id=1",Integer.class));
 }
 @Test void oldApprovalCannotWriteAfterGoalRevision(){
  var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");tasks.revise(me,task.id(),0,"document-a","价格改为109");
  assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),body,()->{fail("must not invoke business");return null;}));assertEquals(88,jdbc.queryForObject("SELECT fee FROM course WHERE id=1",Integer.class));
 }
 @Test void businessRollbackCannotLeaveSuccessfulReceipt(){
  var body=json.valueToTree(Map.of("fee",99));var p=receipts.prepare(me,task.id(),0,"document-a","PUT","/api/v1/admin/courses/1",body);receipts.approve(me,p.id(),0,"document-a");
  assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",p.path(),body,()->{jdbc.update("UPDATE course SET fee=99 WHERE id=1");throw new IllegalStateException("fixture failure");}));
  assertEquals(88,jdbc.queryForObject("SELECT fee FROM course WHERE id=1",Integer.class));assertEquals("FAILED",receipts.get(me,p.id()).status());
 }
}
