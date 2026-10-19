package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.agent.computer.*;
import com.gym.fitness.module.course.service.CourseService;
import com.gym.fitness.module.chat.service.ChatService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentProductPriceTest {
    final ObjectMapper json = new ObjectMapper();
    final ComputerAgentDtos.Session me = actor("SUPER_ADMIN", 1L, List.of("ASSISTANT_USE", "PRODUCT_WRITE"));
    JdbcTemplate jdbc;
    AgentTaskStore tasks;
    AgentMutationReceiptService receipts;
    AgentTaskStore.View task;
    final String route = "/api/v1/commercial/products/7";

    static ComputerAgentDtos.Session actor(String role, Long venue, List<String> permissions) {
        return new ComputerAgentDtos.Session((long) List.of("SUPER_ADMIN","MANAGER","COACH","RECEPTION","SALES","MEMBER").indexOf(role)+1, role, venue, "session", "/dashboard", permissions, null);
    }
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:product" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE venue(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO sys_user VALUES(1),(2),(3),(4),(5),(6)"); jdbc.update("INSERT INTO venue VALUES(1),(2)");
        jdbc.execute("CREATE TABLE membership_product(id BIGINT PRIMARY KEY,name VARCHAR(100),category VARCHAR(20),product_type VARCHAR(30),description VARCHAR(500),validity_days INT,total_uses INT,access_allowed BOOLEAN,price DECIMAL(12,2),venue_id BIGINT,status VARCHAR(20))");
        jdbc.update("INSERT INTO membership_product VALUES(7,'进阶季卡','CARD','QUARTER_CARD','连续训练三个月，无限次入场',90,NULL,TRUE,1299,2,'ACTIVE')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__agent_task_ledger.sql"), new ClassPathResource("db/migration/V25__agent_mutation_receipts.sql")).execute(ds);
        tasks = new AgentTaskStore(jdbc, new DataSourceTransactionManager(ds), json);
        receipts = new AgentMutationReceiptService(jdbc, new DataSourceTransactionManager(ds), json, tasks, mock(ChatService.class), mock(AgentDomainVerifier.class));
        task = tasks.create(me, "document-a", "点开 会籍与商业，并把进阶季卡的价格改为998");
    }
    JsonNode body(int price) {
        var result=json.createObjectNode().put("name","进阶季卡").put("category","CARD").put("productType","QUARTER_CARD").put("description","连续训练三个月，无限次入场").put("validityDays",90).putNull("totalUses").put("accessAllowed",true).put("price",price).put("venueId",2).put("status","ACTIVE");
        return result;
    }
    AgentOutcomeVerifier.Report verify(String goal, List<AgentMutationReceiptService.Receipt> received) {
        var store=mock(AgentTaskStore.class); var service=mock(AgentMutationReceiptService.class);
        var t=new AgentTaskStore.View("id",0,goal,"RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());
        when(store.check(me,"id",0,"document-a")).thenReturn(t); when(service.list(me,"id")).thenReturn(received);
        return new AgentOutcomeVerifier(store,service,mock(ComputerAgentService.class),mock(CourseService.class),mock(ChatService.class),json,mock(AgentDomainVerifier.class)).verify(me,"id",0,"document-a",json.valueToTree(Map.of("path","/commercial")));
    }
    @Test void commercialNavigationIsARecognizedGoalButFillingThePriceIsNotSaving() {
        assertTrue(verify("点开 会籍与商业",List.of()).complete());
        var report=verify(task.goal(),List.of());
        assertFalse(report.complete()); assertTrue(report.partial());
    }
    @Test void exactProductPriceRequiresApprovalCommitsOnceAndCanBeVerified() {
        var p=receipts.prepare(me,task.id(),0,"document-a","PUT",route,body(998));
        assertEquals(new BigDecimal("1299.00"),jdbc.queryForObject("SELECT price FROM membership_product WHERE id=7",BigDecimal.class));
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",route,body(998),()->{fail("no approval");return null;}));
        receipts.approve(me,p.id(),0,"document-a");
        receipts.execute(me,p.id(),"PUT",route,body(998),()->{
            jdbc.update("UPDATE membership_product SET price=998 WHERE id=7");
            var saved=body(998).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)saved).put("id",7);
            return json.createObjectNode().put("code",0).set("data",saved);
        });
        var verifier=new AgentOutcomeVerifier(tasks,receipts,mock(ComputerAgentService.class),mock(CourseService.class),mock(ChatService.class),json,mock(AgentDomainVerifier.class));
        assertTrue(verifier.verify(me,task.id(),0,"document-a",json.valueToTree(Map.of("path","/commercial"))).complete());
        assertEquals(90,jdbc.queryForObject("SELECT validity_days FROM membership_product WHERE id=7",Integer.class));
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",route,body(998),()->{fail("duplicate");return null;}));
    }
    @Test void wrongPriceWrongProductAndUnrequestedValidityNeverReachConfirmation() {
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT",route,body(999)));
        var changed=body(998).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)changed).put("validityDays",900);
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT",route,changed));
        jdbc.update("UPDATE membership_product SET name='其他季卡' WHERE id=7");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT",route,body(998)));
    }
    @Test void allFourNonPricingRolesAreDeniedEvenWithProductRead() {
        for(var role:List.of("COACH","RECEPTION","SALES","MEMBER")) {
            var user=actor(role,2L,List.of("ASSISTANT_USE","PRODUCT_READ"));
            var own=tasks.create(user,"document-a",task.goal());
            assertThrows(RuntimeException.class,()->receipts.prepare(user,own.id(),0,"document-a","PUT",route,body(998)),role);
        }
    }
    @Test void managerCannotEditAnotherVenueAndAmbiguousHeadquartersNameNeedsProductId() {
        var manager=actor("MANAGER",1L,List.of("ASSISTANT_USE","PRODUCT_WRITE"));
        var own=tasks.create(manager,"document-a",task.goal());
        assertThrows(RuntimeException.class,()->receipts.prepare(manager,own.id(),0,"document-a","PUT",route,body(998)));
        jdbc.update("INSERT INTO membership_product SELECT 8,name,category,product_type,description,validity_days,total_uses,access_allowed,price,1,status FROM membership_product WHERE id=7");
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT",route,body(998)));
        task=tasks.revise(me,task.id(),0,"document-a","改为任务：把商品编号#7价格改为998");
        assertNotNull(receipts.prepare(me,task.id(),task.version(),"document-a","PUT",route,body(998)));
    }
    @Test void changedProductAfterApprovalCannotOverwriteAnotherEditorsWork() {
        var p=receipts.prepare(me,task.id(),0,"document-a","PUT",route,body(998)); receipts.approve(me,p.id(),0,"document-a");
        jdbc.update("UPDATE membership_product SET validity_days=120 WHERE id=7");
        assertThrows(RuntimeException.class,()->receipts.execute(me,p.id(),"PUT",route,body(998),()->{fail("stale product");return null;}));
    }
    @Test void registeringPriceDoesNotOpenPurchasesRefundsOrProductDeletion() {
        for(var path:List.of("/api/v1/commercial/orders","/api/v1/commercial/entitlements/7/refund","/api/v1/commercial/products"))
            assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","POST",path,body(998)));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","DELETE",route,json.nullNode()));
        assertThrows(RuntimeException.class,()->receipts.prepare(me,task.id(),0,"document-a","PUT",route+"?price=1",body(998)));
    }
}
