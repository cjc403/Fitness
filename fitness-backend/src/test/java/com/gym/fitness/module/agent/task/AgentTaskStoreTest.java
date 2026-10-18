package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentTaskStoreTest {
    private AgentTaskStore store;
    private final Session me=new Session(1L,"COACH",1L,"session-a","/coach-workbench",List.of("ASSISTANT_USE"),null);
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:tasks"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE venue(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO sys_user VALUES(1)");jdbc.update("INSERT INTO venue VALUES(1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__agent_task_ledger.sql")).execute(ds);
        store=new AgentTaskStore(jdbc,new DataSourceTransactionManager(ds),new ObjectMapper());
    }
    @Test void taskIsScopedToActorRoleVenueAndLoginSession(){
        var task=store.create(me,"document-a","为学员准备训练计划");
        assertEquals("RUNNING",task.status());assertEquals(0L,task.version());
        assertThrows(RuntimeException.class,()->store.get(new Session(1L,"COACH",1L,"session-b","/coach-workbench",List.of(),null),task.id()));
        assertThrows(RuntimeException.class,()->store.get(new Session(1L,"MANAGER",1L,"session-a","/dashboard",List.of(),null),task.id()));
        assertThrows(RuntimeException.class,()->store.get(new Session(2L,"COACH",1L,"session-a","/coach-workbench",List.of(),null),task.id()));
        assertThrows(RuntimeException.class,()->store.get(new Session(1L,"COACH",2L,"session-a","/coach-workbench",List.of(),null),task.id()));
        assertFalse(task.toString().contains("session-a"));assertFalse(task.toString().contains("document-a"));
    }
    @Test void revisionsAndRecoveryRejectOldVersionAndOldDocumentLease(){
        var task=store.create(me,"document-a","把指定课程价格改成99");
        var changed=store.revise(me,task.id(),0,"document-a","价格改成109，容量保持原样");
        assertEquals(1L,changed.version());assertTrue(changed.goal().contains("109"));
        assertThrows(RuntimeException.class,()->store.check(me,task.id(),0,"document-a"));
        var recovered=store.recover(me,task.id(),1,"document-b");
        assertEquals(2L,recovered.version());assertEquals("PAUSED",recovered.status());
        assertThrows(RuntimeException.class,()->store.check(me,task.id(),2,"document-a"));
        assertNotNull(store.check(me,task.id(),2,"document-b"));
    }
    @Test void cancellationIsTerminalAndAnOldPlannerCannotPublishProgress(){
        var task=store.create(me,"document-a","查看本周课程");
        store.cancel(me,task.id(),0,"document-a");
        assertThrows(RuntimeException.class,()->store.update(me,task.id(),0,"document-a","SUCCEEDED",Map.of(),Map.of()));
        assertEquals("CANCELLED",store.get(me,task.id()).status());
    }
    @Test void largeInputsAndUnknownStatusAreRejected(){
        assertThrows(RuntimeException.class,()->store.create(me,"document-a","x".repeat(2001)));
        var task=store.create(me,"document-a","查看课程");
        assertThrows(RuntimeException.class,()->store.update(me,task.id(),0,"document-a","ANYTHING",Map.of(),Map.of()));
    }
    @Test void credentialBearingGoalsAreRejectedBeforeTheyCanEnterTaskDiagnostics(){
        assertThrows(RuntimeException.class,()->store.create(me,"document-a","给管理员发送 sk-"+"a".repeat(32)));
        assertThrows(RuntimeException.class,()->store.create(me,"document-a","保存令牌 eyJ"+"a".repeat(18)+"."+"b".repeat(18)+"."+"c".repeat(18)));
    }
    @Test void modelReservationsSurviveLostResponsesAndDoNotChangeTheDocumentVersion(){
        var task=store.create(me,"document-a","查看课程");
        for(int i=1;i<=100;i++)assertEquals(i,store.reserveModelCall(me,task.id(),0,"document-a"));
        assertEquals(0,store.get(me,task.id()).version());
        assertThrows(RuntimeException.class,()->store.reserveModelCall(me,task.id(),0,"document-a"));
    }
    @Test void usageSurvivesAnActionValidationFailureAndCannotBeCountedTwice(){
        var task=store.create(me,"document-a","查看课程");int call=store.reserveModelCall(me,task.id(),0,"document-a");
        store.recordModelUsage(me,task.id(),call,100001);store.recordModelUsage(me,task.id(),call,100001);
        assertEquals(100001,store.get(me,task.id()).checkpoint().path("totalTokens").asInt());assertEquals(0,store.get(me,task.id()).version());
        assertThrows(RuntimeException.class,()->store.reserveModelCall(me,task.id(),0,"document-a"));
    }
}
