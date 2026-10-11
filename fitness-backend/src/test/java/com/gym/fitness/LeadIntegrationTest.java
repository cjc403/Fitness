package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.organization.repository.*;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:leads;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.ai.enabled=false","fitness.operations.enabled=false"})
@AutoConfigureMockMvc @Transactional @org.springframework.context.annotation.Import(LeadIntegrationTest.TimestampClockConfiguration.class)
class LeadIntegrationTest {
    private static final MutableTimestampClock TIMESTAMP_CLOCK=new MutableTimestampClock();
    private static class MutableTimestampClock extends Clock {
        private final java.util.concurrent.atomic.AtomicReference<Clock> source;
        private final ZoneId zone;
        MutableTimestampClock(){this(new java.util.concurrent.atomic.AtomicReference<>(Clock.system(ZoneId.of("Asia/Shanghai"))),ZoneId.of("Asia/Shanghai"));}
        private MutableTimestampClock(java.util.concurrent.atomic.AtomicReference<Clock> source,ZoneId zone){this.source=source;this.zone=zone;}
        public ZoneId getZone(){return zone;}public Clock withZone(ZoneId next){return new MutableTimestampClock(source,next);}public Instant instant(){return source.get().instant();}
    }
    @org.springframework.boot.test.context.TestConfiguration
    static class TimestampClockConfiguration {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer timestampClock(){return properties->properties.put(org.hibernate.generator.internal.CurrentTimestampGeneration.CLOCK_SETTING_NAME,TIMESTAMP_CLOCK);}
    }
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String url=System.getProperty("leads.test.pgurl");
        if(url!=null&&!url.isBlank()) {
            if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55439/leads_junit"))throw new IllegalArgumentException("Only the isolated leads_junit database is allowed");
            registry.add("spring.datasource.url",()->url);registry.add("spring.datasource.username",()->"fitness_user");
            registry.add("spring.datasource.password",()->"isolated-test");registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
            registry.add("spring.jpa.hibernate.ddl-auto",()->"validate");registry.add("spring.flyway.enabled",()->"true");
        }
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired SysUserRepository users; @Autowired RoleRepository roles; @Autowired VenueRepository venues;
    @Autowired MemberRepository members; @Autowired SalesOrderRepository orders;
    @Autowired MembershipProductRepository products; @Autowired MemberEntitlementRepository entitlements;
    @Autowired JwtTokenProvider jwt;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource datasource;

    @Test void leadGeneratedTimesMatchTheirStoredMicrosecondPrecision() throws Exception {
        var fixed=Clock.fixed(Instant.parse("2026-10-09T04:00:00.123456900Z"),ZoneId.of("Asia/Shanghai"));
        var service=context.getBean("leadService");var original=(Clock)ReflectionTestUtils.getField(service,"businessClock");
        var timestampOriginal=TIMESTAMP_CLOCK.source.get();ReflectionTestUtils.setField(service,"businessClock",fixed);TIMESTAMP_CLOCK.source.set(fixed);
        try {
            var sales=account("SALES",0);var created=write(sales,"",createRequest(sales,"13812340040"),0);long id=created.path("lead").path("id").asLong();
            LocalDateTime expected=LocalDateTime.now(fixed).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            assertThat(LocalDateTime.parse(created.path("lead").path("createdAt").asText())).isEqualTo(expected);
            assertThat(jdbc.queryForObject("select created_at from sales_lead where id=?",LocalDateTime.class,id)).isEqualTo(expected);
            assertThat(LocalDateTime.parse(created.path("events").get(0).path("createdAt").asText())).isEqualTo(expected);
            var updated=write(sales,"/"+id+"/profile",profile(sales,0),0);
            assertThat(LocalDateTime.parse(updated.path("lead").path("updatedAt").asText())).isEqualTo(jdbc.queryForObject("select updated_at from sales_lead where id=?",LocalDateTime.class,id));
            assertThat(LocalDateTime.parse(updated.path("events").get(0).path("createdAt").asText())).isEqualTo(jdbc.queryForObject("select created_at from lead_event where id=?",LocalDateTime.class,updated.path("events").get(0).path("id").asLong()));
            var visited=write(sales,"/"+id+"/follow-ups",follow(1,"VISITED"),0);
            assertThat(LocalDateTime.parse(visited.path("lead").path("visitedAt").asText())).isEqualTo(jdbc.queryForObject("select visited_at from sales_lead where id=?",LocalDateTime.class,id));
            var booked=write(sales,"/"+id+"/trials",trial(sales,2,expected.plusHours(1),60),0);long tid=booked.path("lead").path("nextTrial").path("id").asLong();
            ReflectionTestUtils.setField(service,"businessClock",Clock.offset(fixed,Duration.ofHours(2)));
            var done=write(sales,"/"+id+"/trials/"+tid+"/result",result(3,"ATTENDED"),0);
            assertThat(LocalDateTime.parse(done.path("trials").get(0).path("recordedAt").asText())).isEqualTo(jdbc.queryForObject("select recorded_at from lead_trial where id=?",LocalDateTime.class,tid));
            assertThat(LocalDateTime.parse(done.path("lead").path("trialCompletedAt").asText())).isEqualTo(jdbc.queryForObject("select trial_completed_at from sales_lead where id=?",LocalDateTime.class,id));
        } finally {ReflectionTestUtils.setField(service,"businessClock",original);TIMESTAMP_CLOCK.source.set(timestampOriginal);}
    }

    @Test void sameInstantHibernateMemberAndPaidOrderRemainEligibleAtDatabasePrecision() throws Exception {
        var fixed=Clock.fixed(Instant.parse("2026-10-09T04:00:00.123456900Z"),ZoneId.of("Asia/Shanghai"));
        var service=context.getBean("leadService");var original=(Clock)ReflectionTestUtils.getField(service,"businessClock");
        var timestampOriginal=TIMESTAMP_CLOCK.source.get();ReflectionTestUtils.setField(service,"businessClock",fixed);TIMESTAMP_CLOCK.source.set(fixed);
        try {
            var sales=account("SALES",0);long id=create(sales,"13812340041");var member=fixtureMember(sales,"13812340041");var order=fixtureOrder(member,"NEW","PAID","20","0");
            LocalDateTime expected=LocalDateTime.now(fixed).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            assertThat(member.getCreatedAt()).isEqualTo(expected);assertThat(order.getCreatedAt()).isEqualTo(expected);
            order.setPaidAt(expected);orders.saveAndFlush(order);em.clear();
            assertThat(jdbc.queryForObject("select created_at from member where id=?",LocalDateTime.class,member.getId())).isEqualTo(expected);
            assertThat(jdbc.queryForObject("select paid_at from sales_order where id=?",LocalDateTime.class,order.getId())).isEqualTo(expected);
            var candidates=read(sales,"/"+id+"/conversion-options");assertThat(candidates.size()).isEqualTo(1);
            assertThat(candidates.get(0).path("orderId").asLong()).isEqualTo(order.getId());
            var converted=write(sales,"/"+id+"/convert",link(0,order.getId()),0);
            assertThat(converted.path("lead").path("stage").asText()).isEqualTo("CONVERTED");
            assertThat(LocalDateTime.parse(converted.path("lead").path("convertedAt").asText())).isEqualTo(jdbc.queryForObject("select converted_at from sales_lead where id=?",LocalDateTime.class,id));
        } finally {ReflectionTestUtils.setField(service,"businessClock",original);TIMESTAMP_CLOCK.source.set(timestampOriginal);}
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void newConversionRechecksProductAvailabilityAfterWaitingForVenue() throws Exception {
        productChangeDuringNewConversion("STATUS",2600,"13812340038");
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void newConversionRechecksReducedPriceAfterWaitingForVenue() throws Exception {
        productChangeDuringNewConversion("PRICE",1001,"13812340039");
    }
    private void productChangeDuringNewConversion(String change,int expectedCode,String phone) throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);var sales=tx.execute(s->account("SALES",0));long id=create(sales,phone);
        var request=convert(sales,0);Long productId=(Long)request.get("productId");var product=products.findById(productId).orElseThrow();
        long memberCount=members.count(),orderCount=orders.count(),entitlementCount=entitlements.count();
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var gate=datasource.getConnection()) {
            gate.setAutoCommit(false);String clause=System.getProperty("leads.test.pgurl")==null?"FOR UPDATE":"FOR NO KEY UPDATE";
            try(var statement=gate.prepareStatement("SELECT id FROM venue WHERE id=? "+clause)){statement.setLong(1,sales.getVenueId());statement.executeQuery().close();}
            var future=executor.submit(()->rawWrite(sales,"/"+id+"/convert",request));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
            while(System.nanoTime()<deadline) {
                String waitingQuery=System.getProperty("leads.test.pgurl")==null?
                    "select count(*) from information_schema.sessions where blocker_id is not null and lower(executing_statement) like '%venue%'":
                    "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%venue%'";
                if(jdbc.queryForObject(waitingQuery,Long.class)>=1){waiting=true;break;}Thread.sleep(20);
            }
            assertThat(waiting).as("NEW conversion reaches the actual venue lock before product mutation").isTrue();
            String update="STATUS".equals(change)?"update membership_product set status='INACTIVE' where id=?":"update membership_product set price=9 where id=?";
            try(var statement=gate.prepareStatement(update)){statement.setLong(1,productId);assertThat(statement.executeUpdate()).isEqualTo(1);}
            gate.commit(); // Make product change visible before releasing the waiting conversion.
            var response=future.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(response.path("code").asInt()).as("conversion must reject the changed product rather than use a stale JPA entity").isEqualTo(expectedCode);
            assertThat(members.count()).isEqualTo(memberCount);assertThat(orders.count()).isEqualTo(orderCount);assertThat(entitlements.count()).isEqualTo(entitlementCount);
            var detail=read(sales,"/"+id);assertThat(detail.path("lead").path("stage").asText()).isEqualTo("CONSULTING");assertThat(detail.path("lead").path("version").asLong()).isZero();assertThat(detail.path("events").size()).isEqualTo(1);
        } finally {
            executor.shutdownNow();executor.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);
            jdbc.update("update membership_product set status=?,price=? where id=?",product.getStatus(),product.getPrice(),productId);
            cleanup(List.of(id),List.of(sales));
        }
    }

    @Test void headquartersWorkbenchBatchesTenVenuesWithoutEagerAccountAssociations() throws Exception {
        for(int i=0;i<10;i++) {
            var venue=new com.gym.fitness.module.organization.entity.VenueEntity();venue.setName("隔离查询店"+i);venue.setCode("LEAD-"+UUID.randomUUID());venue.setStatus("ACTIVE");venues.saveAndFlush(venue);
            var advisor=accountForVenue("SALES",venue);create(advisor,"1389000"+String.format("%04d",i));
        }
        var admin=account("SUPER_ADMIN",0);em.clear();var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);stats.clear();
        try {var result=read(admin,"/workbench?page=1&size=2");assertThat(result.path("summary").path("totalLeads").asInt()).isEqualTo(10);
            assertThat(stats.getPrepareStatementCount()).as("headquarters query count must stay bounded across ten venues").isLessThanOrEqualTo(12);
            assertThat(stats.getEntityStatistics(SysUserEntity.class.getName()).getLoadCount()).isLessThanOrEqualTo(1);
        } finally {stats.setStatisticsEnabled(enabled);}
    }

    @Test void optionsAndConversionCandidatesUseBoundedScopedQueries() throws Exception {
        var sales=account("SALES",0);var foreign=account("SALES",1);long id=create(sales,"13812340037");
        var target=fixtureMember(sales,"13812340037");fixtureOrder(target,"NEW","PAID","20","0");
        for(int i=0;i<12;i++){account("MEMBER",1);fixtureOrder(fixtureMember(foreign,"1387000"+String.format("%04d",i)),"NEW","PAID","20","0");fixtureOrder(fixtureMember(sales,"1388000"+String.format("%04d",i)),"NEW","PAID","20","0");}
        em.clear();var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);stats.clear();
        try {
            read(sales,"/options");
            assertThat(stats.getEntityStatistics(SysUserEntity.class.getName()).getLoadCount()).as("option projections must not load member accounts").isLessThanOrEqualTo(1);
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(10);
            em.clear();stats.clear();assertThat(read(sales,"/"+id+"/conversion-options").size()).isEqualTo(1);
            assertThat(stats.getEntityStatistics("com.gym.fitness.module.commercial.entity.SalesOrderEntity").getLoadCount()).as("only qualifying NEW orders may be loaded").isEqualTo(1);
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(8);
        } finally {stats.setStatisticsEnabled(enabled);}
    }

    @Test void everyMutationRequiresAKeyAndVersionAndChangedKeysCannotCrossOperations() throws Exception {
        var sales=account("SALES",0);long id=create(sales,"13812340030");
        var follow=follow(0,"CONTACTED");var booked=trial(sales,0,LocalDateTime.now().plusDays(1),60);var conversion=convert(sales,0);
        for(var entry:List.of(Map.entry("/follow-ups",follow),Map.entry("/trials",booked),Map.entry("/convert",conversion))) {
            var r=new LinkedHashMap<>(entry.getValue());r.remove("idempotencyKey");badRequest(sales,"/"+id+entry.getKey(),r);
            r=new LinkedHashMap<>(entry.getValue());r.remove("expectedVersion");badRequest(sales,"/"+id+entry.getKey(),r);
        }
        var done=write(sales,"/"+id+"/trials",booked,0);long tid=done.path("lead").path("nextTrial").path("id").asLong();
        for(var field:List.of("idempotencyKey","expectedVersion")){var r=result(1,"CANCELLED");r.remove(field);badRequest(sales,"/"+id+"/trials/"+tid+"/result",r);}
        var changed=trial(sales,0,LocalDateTime.now().plusDays(2),60);changed.put("idempotencyKey",booked.get("idempotencyKey"));write(sales,"/"+id+"/trials",changed,1001);
        var r=result(1,"CANCELLED");r.put("idempotencyKey",booked.get("idempotencyKey"));write(sales,"/"+id+"/trials/"+tid+"/result",r,1001);
    }

    @Test void creationAndTrialRetrySurvivePassedTimeButRecheckOwnership() throws Exception {
        var sales=account("SALES",0);var other=account("SALES",0);var manager=account("MANAGER",0);var create=createRequest(sales,"13812340031");
        create.put("nextFollowAt",LocalDateTime.now().plusMinutes(1).withNano(0).toString());long id=write(sales,"",create,0).path("lead").path("id").asLong();
        var request=trial(sales,0,LocalDateTime.now().plusMinutes(1).withNano(0),60);write(sales,"/"+id+"/trials",request,0);
        var service=context.getBean("leadService");var clock=(Clock)ReflectionTestUtils.getField(service,"businessClock");
        try {ReflectionTestUtils.setField(service,"businessClock",Clock.offset(clock,Duration.ofHours(2)));
            write(sales,"",create,0);write(sales,"/"+id+"/trials",request,0);
        } finally {ReflectionTestUtils.setField(service,"businessClock",clock);}
        write(manager,"/"+id+"/profile",profile(other,1),0);write(sales,"",create,1003);write(sales,"/"+id+"/trials",request,1003);
    }

    @Test void noShowAndCancellationRestoreConsultingAndResultReplayPreservesHistory() throws Exception {
        var sales=account("SALES",0);long id=create(sales,"13812340032");var booked=write(sales,"/"+id+"/trials",trial(sales,0,LocalDateTime.now().plusMinutes(1),60),0);
        long tid=booked.path("lead").path("nextTrial").path("id").asLong();var result=result(1,"NO_SHOW");
        var service=context.getBean("leadService");var clock=(Clock)ReflectionTestUtils.getField(service,"businessClock");
        try {ReflectionTestUtils.setField(service,"businessClock",Clock.offset(clock,Duration.ofHours(2)));
            assertThat(write(sales,"/"+id+"/trials/"+tid+"/result",result,0).path("lead").path("stage").asText()).isEqualTo("CONSULTING");
            assertThat(write(sales,"/"+id+"/trials/"+tid+"/result",result,0).path("events").size()).isEqualTo(3);
            result.put("note","更改历史结果");write(sales,"/"+id+"/trials/"+tid+"/result",result,1001);
        } finally {ReflectionTestUtils.setField(service,"businessClock",clock);}
    }

    @Test void realApprovedFullRefundRemovesEffectiveConversion() throws Exception {
        var sales=account("SALES",0);var reception=account("RECEPTION",0);var manager=account("MANAGER",0);
        long id=create(sales,"13812340033");var done=write(sales,"/"+id+"/convert",convert(sales,0),0);
        long memberId=done.path("lead").path("convertedMemberId").asLong();long eid=entitlements.findByMemberIdOrderByIdDesc(memberId).get(0).getId();
        var quote=json.readTree(mvc.perform(get("/api/v1/finance/entitlements/"+eid+"/quote").header("Authorization",token(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString()).path("data");
        long refund=apiPost(reception,"/api/v1/finance/refunds",Map.of("entitlementId",eid,"version",quote.path("version").asLong(),"reason","会员取消未用开卡","idempotencyKey",UUID.randomUUID().toString())).path("id").asLong();
        assertThat(apiPost(manager,"/api/v1/finance/refunds/"+refund+"/approve",Map.of("reason","核实未使用")).path("status").asText()).isEqualTo("APPROVED");
        // This test owns an outer rollback transaction. Flush the request's writes before the
        // next HTTP call, reproducing the commit boundary used by independent real requests.
        em.flush();em.clear();
        assertThat(read(sales,"/workbench").path("summary").path("convertedLeads").asInt()).isEqualTo(1);
        apiPost(manager,"/api/v1/finance/refunds/"+refund+"/pay",Map.of("amount",10,"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"note","登记实际退款"));
        assertThat(read(sales,"/workbench").path("summary").path("convertedLeads").asInt()).isZero();
        assertThat(read(sales,"/"+id).path("lead").path("stage").asText()).isEqualTo("CONVERTED");
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousNewConversionReusesRealSaleAndDoesNotDuplicateEntitlements() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);var sales=tx.execute(s->account("SALES",0));long id=create(sales,"13812340034");
        try {var request=convert(sales,0);List<JsonNode> responses=parallel(List.of(()->write(sales,"/"+id+"/convert",request,0),()->write(sales,"/"+id+"/convert",request,0)));
            long member=responses.get(0).path("lead").path("convertedMemberId").asLong();assertThat(responses.get(1).path("lead").path("convertedMemberId").asLong()).isEqualTo(member);
            assertThat(entitlements.findByMemberIdOrderByIdDesc(member)).hasSize(1);assertThat(read(sales,"/"+id).path("events").size()).isEqualTo(2);
        } finally {cleanup(List.of(id),List.of(sales));}
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void hostForeignKeyAndOtherOperatorsRealSaleCompleteWhileBothWaitForVenue() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);var a=tx.execute(s->account("SALES",0));var b=tx.execute(s->account("SALES",0));
        var m=tx.execute(s->fixtureMember(b,"13812340035"));long id=create(a,"13812340036");
        try {
            var conversion=convert(b,0);var sale=Map.of("memberId",m.getId(),"productId",conversion.get("productId"),"paidAmount",10,"paymentMethod","CASH","idempotencyKey",UUID.randomUUID().toString());
            var tasks=List.<java.util.concurrent.Callable<JsonNode>>of(()->write(a,"/"+id+"/trials",trial(b,0,LocalDateTime.now().plusDays(1),60),0),()->apiPost(b,"/api/v1/commercial/orders",sale));
            if(System.getProperty("leads.test.pgurl")==null)parallel(tasks);else gatedVenue(a.getVenueId(),tasks);
        } finally {cleanup(List.of(id),List.of(a));tx.executeWithoutResult(s->{deleteSalesMember(m.getId());users.deleteById(b.getId());});}
    }

    private void gatedVenue(Long venue,List<java.util.concurrent.Callable<JsonNode>> tasks) throws Exception {
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var gate=datasource.getConnection()) {
            gate.setAutoCommit(false);try(var statement=gate.prepareStatement("SELECT id FROM venue WHERE id=? FOR NO KEY UPDATE")){statement.setLong(1,venue);statement.executeQuery().close();}
            var futures=tasks.stream().map(executor::submit).toList();long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);boolean blocked=false;
            while(System.nanoTime()<deadline) {Long waiting=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%venue%'",Long.class);if(waiting>=2){blocked=true;break;}Thread.sleep(20);}
            assertThat(blocked).as("both independent operator transactions reach the venue barrier").isTrue();gate.commit();
            for(var future:futures)future.get(15,java.util.concurrent.TimeUnit.SECONDS);
        } finally {executor.shutdownNow();executor.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);}
    }
    private JsonNode apiPost(SysUserEntity u,String path,Object request) throws Exception {return json.readTree(mvc.perform(post(path).header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString()).path("data");}

    @Test void workbenchScopesQueriesAndBatchLoadsAssociationsForSmallPages() throws Exception {
        var sales=account("SALES",0);var foreign=account("SALES",1);
        for(int i=0;i<12;i++){create(sales,"1385000"+String.format("%04d",i));create(foreign,"1386000"+String.format("%04d",i));}
        em.clear();var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);stats.clear();
        try {
            var work=read(sales,"/workbench?page=1&size=2");assertThat(work.path("summary").path("totalLeads").asInt()).isEqualTo(12);
            assertThat(work.path("leads").path("records").size()).isEqualTo(2);
            assertThat(stats.getEntityStatistics("com.gym.fitness.module.lead.entity.SalesLeadEntity").getLoadCount()).isEqualTo(12);
            assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(12);
        } finally {stats.setStatisticsEnabled(enabled);}
    }

    @Test void maximumLengthFollowContentIsAcceptedAndStoredWithoutTruncation() throws Exception {
        var sales=account("SALES",0);long id=create(sales,"13812340026");var request=follow(0,"CONTACTED");request.put("content","联".repeat(1000));
        var detail=write(sales,"/"+id+"/follow-ups",request,0);
        assertThat(detail.path("events").get(0).path("content").asText()).endsWith("联".repeat(1000));
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousCreateAndMutationReplaysOnlyOnceAndDifferentIntentSeesVersionConflict() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);var sales=tx.execute(s->account("SALES",0));
        var ids=new ArrayList<Long>();
        try {
            var create=createRequest(sales,"13812340027");
            List<JsonNode> created=parallel(List.of(()->write(sales,"",create,0),()->write(sales,"",create,0)));
            long id=created.get(0).path("lead").path("id").asLong();ids.add(id);
            assertThat(created.get(1).path("lead").path("id").asLong()).isEqualTo(id);
            var follow=follow(0,"CONTACTED");parallel(List.of(()->write(sales,"/"+id+"/follow-ups",follow,0),()->write(sales,"/"+id+"/follow-ups",follow,0)));
            assertThat(read(sales,"/"+id).path("events").size()).isEqualTo(2);
            var first=follow(1,"CONTACTED");var second=follow(1,"INTERESTED");
            List<JsonNode> outcomes=parallel(List.of(()->rawWrite(sales,"/"+id+"/follow-ups",first),()->rawWrite(sales,"/"+id+"/follow-ups",second)));
            assertThat(outcomes.stream().map(n->n.path("code").asInt()).toList()).containsExactlyInAnyOrder(0,2600);
        } finally {cleanup(ids,List.of(sales));}
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void differentOperatorsSerializeOverlappingTrialsAndCrossHostsDoNotDeadlock() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        var a=tx.execute(s->account("SALES",0));var b=tx.execute(s->account("SALES",0));var ids=new ArrayList<Long>();
        try {
            long aid=create(a,"13812340028"),bid=create(b,"13812340029");ids.add(aid);ids.add(bid);var start=LocalDateTime.now().plusDays(1).withNano(0);
            List<JsonNode> results=parallel(List.of(()->rawWrite(a,"/"+aid+"/trials",trial(a,0,start,60)),()->rawWrite(b,"/"+bid+"/trials",trial(a,0,start.plusMinutes(30),60))));
            assertThat(results.stream().map(n->n.path("code").asInt()).toList()).containsExactlyInAnyOrder(0,2600);
            for(var entry:List.of(Map.entry(a,aid),Map.entry(b,bid))) {
                var d=read(entry.getKey(),"/"+entry.getValue());if(d.path("lead").path("version").asLong()==1)write(entry.getKey(),"/"+entry.getValue()+"/trials/"+d.path("lead").path("nextTrial").path("id").asLong()+"/result",result(1,"CANCELLED"),0);
            }
            long av=read(a,"/"+aid).path("lead").path("version").asLong(),bv=read(b,"/"+bid).path("lead").path("version").asLong();
            parallel(List.of(()->write(a,"/"+aid+"/trials",trial(b,av,start,60),0),()->write(b,"/"+bid+"/trials",trial(a,bv,start,60),0)));
            assertThat(read(a,"/"+aid).path("lead").path("nextTrial").path("hostId").asLong()).isEqualTo(b.getId());
        } finally {cleanup(ids,List.of(a,b));}
    }

    private <T> List<T> parallel(List<java.util.concurrent.Callable<T>> tasks) throws Exception {
        var executor=java.util.concurrent.Executors.newFixedThreadPool(tasks.size());var ready=new java.util.concurrent.CountDownLatch(tasks.size());var go=new java.util.concurrent.CountDownLatch(1);
        try {var futures=tasks.stream().map(task->executor.submit(()->{ready.countDown();if(!go.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("parallel start timed out");return task.call();})).toList();
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();go.countDown();var results=new ArrayList<T>();for(var future:futures)results.add(future.get(20,java.util.concurrent.TimeUnit.SECONDS));return results;
        } finally {go.countDown();executor.shutdownNow();executor.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);}
    }
    private JsonNode rawWrite(SysUserEntity user,String path,Object request) throws Exception {return json.readTree(mvc.perform(post("/api/v1/leads"+path).header("Authorization",token(user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}

    @Test void validationRejectsMissingKeysDuplicatePhonesAndUnqualifiedOwners() throws Exception {
        var sales=account("SALES",0); var other=account("SALES",0); var foreign=account("SALES",1);
        var request=createRequest(sales,"13812340010"); var id=write(sales,"",request,0).path("lead").path("id").asLong();
        request.put("idempotencyKey",UUID.randomUUID().toString()); write(sales,"",request,2601);
        for(var field:List.of("source","intent","phone","name")) {
            var invalid=createRequest(sales,"13812340011"); invalid.put(field,field.equals("name")?"   ":"INVALID"); write(sales,"",invalid,1001);
        }
        var denied=createRequest(other,"13812340011"); write(sales,"",denied,1003);
        denied=createRequest(foreign,"13812340011"); write(account("MANAGER",0),"",denied,1003);
        denied=createRequest(account("COACH",0),"13812340011"); write(account("MANAGER",0),"",denied,1001);
        request=createRequest(sales,"13812340011"); request.remove("idempotencyKey"); badRequest(sales,"",request);
        var profile=profile(sales,0); profile.remove("expectedVersion"); badRequest(sales,"/"+id+"/profile",profile);
        profile=profile(sales,0); profile.remove("idempotencyKey"); badRequest(sales,"/"+id+"/profile",profile);
    }

    @Test void profileMaintainsImmutableIdentityAndVersionAndReassignChecks() throws Exception {
        var sales=account("SALES",0); var other=account("SALES",0); var manager=account("MANAGER",0);
        long id=create(sales,"13812340012"); var request=profile(sales,0);
        var first=write(sales,"/"+id+"/profile",request,0); assertThat(first.path("lead").path("version").asLong()).isEqualTo(1);
        write(sales,"/"+id+"/profile",request,0); request.put("idempotencyKey",UUID.randomUUID().toString()); write(sales,"/"+id+"/profile",request,2600);
        request=profile(other,1); write(sales,"/"+id+"/profile",request,1003);
        var changed=write(manager,"/"+id+"/profile",request,0);
        assertThat(changed.path("lead").path("phone").asText()).isEqualTo("13812340012");
        assertThat(changed.path("lead").path("source").asText()).isEqualTo("WECHAT");
        readCode(sales,"/"+id,1003); assertThat(read(other,"/"+id).path("events").size()).isEqualTo(3);
    }

    @Test void followHistoryAppendsLostMustReopenAndCalendarRetryPrecedesVersion() throws Exception {
        var sales=account("SALES",0); long id=create(sales,"13812340013");
        var follow=follow(0,"VISITED"); follow.put("nextFollowAt",LocalDateTime.now().plusMinutes(1).withNano(0).toString());
        var first=write(sales,"/"+id+"/follow-ups",follow,0); assertThat(first.path("lead").path("stage").asText()).isEqualTo("VISITED");
        var service=context.getBean("leadService"); var clock=(Clock)ReflectionTestUtils.getField(service,"businessClock");
        try {
            ReflectionTestUtils.setField(service,"businessClock",Clock.offset(clock,Duration.ofMinutes(5)));
            var replay=write(sales,"/"+id+"/follow-ups",follow,0); assertThat(replay.path("events").size()).isEqualTo(2);
            follow.put("idempotencyKey",UUID.randomUUID().toString()); follow.put("expectedVersion",1); write(sales,"/"+id+"/follow-ups",follow,1001);
        } finally {ReflectionTestUtils.setField(service,"businessClock",clock);}
        assertThat(write(sales,"/"+id+"/follow-ups",follow(1,"DECLINED"),0).path("lead").path("stage").asText()).isEqualTo("LOST");
        write(sales,"/"+id+"/follow-ups",follow(2,"CONTACTED"),2600);
        assertThat(write(sales,"/"+id+"/follow-ups",follow(2,"REOPEN"),0).path("lead").path("stage").asText()).isEqualTo("VISITED");
        var history=read(sales,"/"+id); assertThat(history.path("events").size()).isEqualTo(4);
    }

    @Test void trialTimesOverlapPendingRestrictionsAndCancelRestoreFacts() throws Exception {
        var sales=account("SALES",0); var host=account("SALES",0); long id=create(sales,"13812340014"),other=create(sales,"13812340015");
        write(sales,"/"+id+"/follow-ups",follow(0,"VISITED"),0);
        var trial=trial(host,1,LocalDateTime.now().plusHours(2).withNano(0),60);
        var booked=write(sales,"/"+id+"/trials",trial,0); long trialId=booked.path("trials").get(0).path("id").asLong();
        assertThat(booked.path("lead").path("stage").asText()).isEqualTo("TRIAL_BOOKED");
        write(sales,"/"+id+"/trials",trial,0);
        write(sales,"/"+id+"/trials",trial(host,2,LocalDateTime.now().plusDays(1),60),2600);
        write(sales,"/"+other+"/trials",trial(host,0,LocalDateTime.parse((String)trial.get("startsAt")).plusMinutes(30),60),2600);
        write(sales,"/"+id+"/follow-ups",follow(2,"DECLINED"),2600);
        write(sales,"/"+id+"/trials/"+trialId+"/result",result(2,"ATTENDED"),2600);
        var cancelled=write(sales,"/"+id+"/trials/"+trialId+"/result",result(2,"CANCELLED"),0);
        assertThat(cancelled.path("lead").path("stage").asText()).isEqualTo("VISITED");
        write(sales,"/"+other+"/trials",trial(host,0,LocalDateTime.parse((String)trial.get("startsAt")),60),0);
        assertThat(write(sales,"/"+id+"/trials",trial(sales,3,LocalDateTime.now().plusDays(2),60),0).path("trials").size()).isEqualTo(2);
    }

    @Test void trialDurationHostResultAndResultPathsAreValidated() throws Exception {
        var sales=account("SALES",0); var foreign=account("SALES",1); long id=create(sales,"13812340016");
        for(var duration:List.of(14,181,0,-1)) write(sales,"/"+id+"/trials",trial(sales,0,LocalDateTime.now().plusDays(1),duration),1001);
        write(sales,"/"+id+"/trials",trial(sales,0,LocalDateTime.now().minusMinutes(1),60),1001);
        write(sales,"/"+id+"/trials",trial(foreign,0,LocalDateTime.now().plusDays(1),60),1001);
        var booked=write(sales,"/"+id+"/trials",trial(sales,0,LocalDateTime.now().plusDays(1),60),0);
        var tid=booked.path("trials").get(0).path("id").asLong(); long other=create(sales,"13812340017");
        write(sales,"/"+other+"/trials/"+tid+"/result",result(0,"CANCELLED"),1004);
        write(sales,"/"+id+"/trials/"+tid+"/result",result(1,"BOOKED"),1001);
        var service=context.getBean("leadService"); var clock=(Clock)ReflectionTestUtils.getField(service,"businessClock");
        try {
            ReflectionTestUtils.setField(service,"businessClock",Clock.offset(clock,Duration.ofDays(2)));
            var done=write(sales,"/"+id+"/trials/"+tid+"/result",result(1,"ATTENDED"),0);
            assertThat(done.path("lead").path("stage").asText()).isEqualTo("TRIAL_COMPLETED");
            assertThat(done.path("lead").path("visitedAt").isNull()).isFalse();
            write(sales,"/"+id+"/trials/"+tid+"/result",result(2,"NO_SHOW"),2600);
        } finally {ReflectionTestUtils.setField(service,"businessClock",clock);}
        var second=write(sales,"/"+id+"/trials",trial(sales,2,LocalDateTime.now().plusDays(3),60),0);
        long secondId=second.path("lead").path("nextTrial").path("id").asLong();
        assertThat(write(sales,"/"+id+"/trials/"+secondId+"/result",result(3,"CANCELLED"),0).path("lead").path("stage").asText()).isEqualTo("TRIAL_COMPLETED");
    }

    @Test void newConversionCreatesRealOrderEntitlementPaymentAndReplaysOnce() throws Exception {
        var sales=account("SALES",0); long id=create(sales,"13812340018"),before=members.count(),beforeOrders=orders.count();
        var request=convert(sales,0); var done=write(sales,"/"+id+"/convert",request,0); var l=done.path("lead");
        assertThat(l.path("stage").asText()).isEqualTo("CONVERTED");
        var m=members.findById(l.path("convertedMemberId").asLong()).orElseThrow();
        assertThat(m.getMemberNo()).isEqualTo("LEAD-"+id); assertThat(m.getUserId()).isNull();
        assertThat(m.getSalesUserId()).isEqualTo(sales.getId());
        var order=orders.findById(l.path("convertedOrderId").asLong()).orElseThrow();
        assertThat(order.getOrderType()).isEqualTo("NEW"); assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(entitlements.findAll().stream().filter(e->Objects.equals(e.getSourceOrderId(),order.getId())).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_transaction where order_id=?",Long.class,order.getId())).isEqualTo(1);
        write(sales,"/"+id+"/convert",request,0); assertThat(members.count()).isEqualTo(before+1); assertThat(orders.count()).isEqualTo(beforeOrders+1);
        write(sales,"/"+id+"/follow-ups",follow(1,"CONTACTED"),2600);
        write(sales,"/"+id+"/trials",trial(sales,1,LocalDateTime.now().plusDays(1),60),2600);
        request.put("paidAmount",new BigDecimal("11")); write(sales,"/"+id+"/convert",request,1001);
        request.put("idempotencyKey",UUID.randomUUID().toString()); write(sales,"/"+id+"/convert",request,2600);
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedNewSaleRollsBackMemberOrderEntitlementAndLead() throws Exception {
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        var sales=tx.execute(s->account("SALES",0));long id=create(sales,"13812340019"),mc=members.count(),oc=orders.count(),ec=entitlements.count();
        try {
            var request=convert(sales,0); request.put("paidAmount",new BigDecimal("999999"));
            write(sales,"/"+id+"/convert",request,1001);
            assertThat(members.count()).isEqualTo(mc); assertThat(orders.count()).isEqualTo(oc); assertThat(entitlements.count()).isEqualTo(ec);
            assertThat(read(sales,"/"+id).path("lead").path("version").asLong()).isZero();
            assertThat(read(sales,"/"+id).path("events").size()).isEqualTo(1);
        } finally {cleanup(List.of(id),List.of(sales));}
    }

    @Test void conversionRequiresTruePaymentPendingTrialResolutionAndExistingMemberFlow() throws Exception {
        var sales=account("SALES",0); long id=create(sales,"13812340020");
        for(var method:List.of("SELF_SERVICE","MOCK","INVALID")) {var r=convert(sales,0);r.put("paymentMethod",method);write(sales,"/"+id+"/convert",r,1001);}
        for(var amount:List.of("0","1.001","-1")) {var r=convert(sales,0);r.put("paidAmount",new BigDecimal(amount));write(sales,"/"+id+"/convert",r,1001);}
        var r=convert(sales,0);r.remove("paidAmount");write(sales,"/"+id+"/convert",r,1001);
        write(sales,"/"+id+"/trials",trial(sales,0,LocalDateTime.now().plusDays(1),60),0);
        write(sales,"/"+id+"/convert",convert(sales,1),2600);
        long other=create(sales,"13812340021"); fixtureMember(sales,"13812340021");
        write(sales,"/"+other+"/convert",convert(sales,0),2600); assertThat(read(sales,"/"+other+"/conversion-options").size()).isZero();
    }

    @Test void linkOptionsQualifyPhoneVenueOwnerCreationPaymentAndNetAmount() throws Exception {
        var sales=account("SALES",0);var other=account("SALES",0); long id=create(sales,"13812340022");
        var member=fixtureMember(sales,"13812340022");var good=fixtureOrder(member,"NEW","PAID","20","0");
        var historic=fixtureOrder(member,"NEW","PAID","30","0");historic.setPaidAt(LocalDateTime.now().minusDays(1));orders.saveAndFlush(historic);
        fixtureOrder(member,"RENEW","PAID","40","0");fixtureOrder(member,"NEW","REFUNDED","50","50");
        fixtureOrder(fixtureMember(other,"13812340022"),"NEW","PAID","60","0");
        fixtureOrder(fixtureMember(sales,"13812349999"),"NEW","PAID","70","0");
        var options=read(sales,"/"+id+"/conversion-options");assertThat(options.size()).isEqualTo(1);
        assertThat(options.get(0).path("orderId").asLong()).isEqualTo(good.getId());
        write(sales,"/"+id+"/convert",link(0,historic.getId()),2600);
        var done=write(sales,"/"+id+"/convert",link(0,good.getId()),0);
        assertThat(done.path("lead").path("convertedMemberId").asLong()).isEqualTo(member.getId());
        assertThat(member.getMemberNo()).startsWith("EXIST-");
    }

    @Test void historicalMemberOrForeignOrderCannotBeAttributedAndOrderCannotBeReused() throws Exception {
        var sales=account("SALES",0);long id=create(sales,"13812340023");var m=fixtureMember(sales,"13812340023");
        var good=fixtureOrder(m,"NEW","PAID","20","0");
        jdbc.update("update member set created_at=? where id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)),m.getId());em.clear();
        assertThat(read(sales,"/"+id+"/conversion-options").size()).isZero();write(sales,"/"+id+"/convert",link(0,good.getId()),2600);
        jdbc.update("update member set created_at=? where id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().plusSeconds(1)),m.getId());em.clear();
        good=orders.findById(good.getId()).orElseThrow();good.setVenueId(venues.findAll().get(1).getId());orders.saveAndFlush(good);
        write(sales,"/"+id+"/convert",link(0,good.getId()),2600);
        good.setVenueId(sales.getVenueId());orders.saveAndFlush(good);write(sales,"/"+id+"/convert",link(0,good.getId()),0);
        assertThat(read(sales,"/"+id+"/conversion-options").size()).isZero();
    }

    @Test void refundChangesEffectiveConversionsButKeepsConversionHistoryAndFilteredDenominator() throws Exception {
        var sales=account("SALES",0);long id=create(sales,"13812340024");create(sales,"13812340025");
        write(sales,"/"+id+"/follow-ups",follow(0,"VISITED"),0);var converted=write(sales,"/"+id+"/convert",convert(sales,1),0);
        var order=orders.findById(converted.path("lead").path("convertedOrderId").asLong()).orElseThrow();
        var work=read(sales,"/workbench?stage=CONVERTED&keyword=13812340024&page=1&size=1");
        assertThat(work.path("summary").path("totalLeads").asInt()).isEqualTo(2);assertThat(work.path("summary").path("conversionRate").asDouble()).isEqualTo(50);
        order.setStatus("PARTIALLY_REFUNDED");order.setRefundedAmount(new BigDecimal("5"));orders.saveAndFlush(order);
        assertThat(read(sales,"/workbench").path("summary").path("convertedLeads").asInt()).isEqualTo(1);
        order.setStatus("REFUNDED");order.setRefundedAmount(order.getPaidAmount());orders.saveAndFlush(order);
        assertThat(read(sales,"/workbench").path("summary").path("convertedLeads").asInt()).isZero();
        assertThat(read(sales,"/"+id).path("lead").path("stage").asText()).isEqualTo("CONVERTED");
        var channels=read(sales,"/workbench").path("channels");
        var wechat=java.util.stream.StreamSupport.stream(channels.spliterator(),false).filter(c->"WECHAT".equals(c.path("source").asText())).findFirst().orElseThrow();
        assertThat(wechat.path("visited").asInt()).isEqualTo(1);
    }

    private long create(SysUserEntity u,String phone) throws Exception {return write(u,"",createRequest(u,phone),0).path("lead").path("id").asLong();}
    private Map<String,Object> profile(SysUserEntity owner,long version) {return new LinkedHashMap<>(Map.of("expectedVersion",version,"name","更新名字","intent","MEDIUM","ownerId",owner.getId(),"idempotencyKey",UUID.randomUUID().toString()));}
    private Map<String,Object> follow(long version,String outcome) {return new LinkedHashMap<>(Map.of("expectedVersion",version,"method","CALL","content","真实跟进","outcome",outcome,"idempotencyKey",UUID.randomUUID().toString()));}
    private Map<String,Object> trial(SysUserEntity host,long version,LocalDateTime start,int minutes) {return new LinkedHashMap<>(Map.of("expectedVersion",version,"hostId",host.getId(),"startsAt",start.toString(),"endsAt",start.plusMinutes(minutes).toString(),"content","力量训练体验","idempotencyKey",UUID.randomUUID().toString()));}
    private Map<String,Object> result(long version,String status) {return new LinkedHashMap<>(Map.of("expectedVersion",version,"status",status,"note","结果说明","idempotencyKey",UUID.randomUUID().toString()));}
    private Map<String,Object> convert(SysUserEntity sales,long version) {return new LinkedHashMap<>(Map.of("expectedVersion",version,"mode","NEW","productId",products.findAll().stream().filter(p->Objects.equals(p.getVenueId(),sales.getVenueId())&&"ACTIVE".equals(p.getStatus())&&p.getPrice().compareTo(new BigDecimal("10"))>=0).findFirst().orElseThrow().getId(),"paymentMethod","CASH","paidAmount",new BigDecimal("10"),"idempotencyKey",UUID.randomUUID().toString()));}
    private Map<String,Object> link(long version,long orderId) {return new LinkedHashMap<>(Map.of("expectedVersion",version,"mode","LINK","orderId",orderId,"idempotencyKey",UUID.randomUUID().toString()));}
    private com.gym.fitness.module.member.entity.MemberEntity fixtureMember(SysUserEntity sales,String phone) {
        var m=new com.gym.fitness.module.member.entity.MemberEntity();m.setMemberNo("EXIST-"+UUID.randomUUID());m.setRealName("已有会员");m.setPhone(phone);m.setVenueId(sales.getVenueId());m.setSalesUserId(sales.getId());m.setStatus("ACTIVE");m.setBlacklistFlag(false);return members.saveAndFlush(m);
    }
    private com.gym.fitness.module.commercial.entity.SalesOrderEntity fixtureOrder(com.gym.fitness.module.member.entity.MemberEntity m,String type,String status,String paid,String refunded) {
        var o=new com.gym.fitness.module.commercial.entity.SalesOrderEntity();o.setOrderNo("LINK-"+UUID.randomUUID());o.setOrderType(type);o.setMemberId(m.getId());o.setProductId(products.findAll().stream().filter(p->Objects.equals(p.getVenueId(),m.getVenueId())).findFirst().orElseThrow().getId());o.setVenueId(m.getVenueId());o.setOperatorId(m.getSalesUserId());o.setPaidAmount(new BigDecimal(paid));o.setOriginalAmount(new BigDecimal(paid));o.setRefundedAmount(new BigDecimal(refunded));o.setStatus(status);o.setPaidAt(LocalDateTime.now());return orders.saveAndFlush(o);
    }
    private void badRequest(SysUserEntity user,String path,Object request) throws Exception {mvc.perform(post("/api/v1/leads"+path).header("Authorization",token(user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));}
    private void cleanup(List<Long> ids,List<SysUserEntity> accounts) {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s->{
            for(Long id:ids){var memberIds=jdbc.queryForList("select converted_member_id from sales_lead where id=? and converted_member_id is not null",Long.class,id);jdbc.update("delete from lead_event where lead_id=?",id);jdbc.update("delete from lead_trial where lead_id=?",id);jdbc.update("delete from sales_lead where id=?",id);for(Long mid:memberIds)deleteSalesMember(mid);}
            for(var user:accounts)users.deleteById(user.getId());
        });
    }
    private void deleteSalesMember(Long memberId) {
        for(Long eid:jdbc.queryForList("select id from member_entitlement where member_id=?",Long.class,memberId)) {
            for(String table:List.of("entitlement_term_period","entitlement_value_event","entitlement_funding","entitlement_value_account","entitlement_operation"))jdbc.update("delete from "+table+" where entitlement_id=?",eid);
        }
        jdbc.update("delete from notification where member_id=?",memberId);jdbc.update("delete from business_document where member_id=?",memberId);
        for(Long oid:jdbc.queryForList("select id from sales_order where member_id=?",Long.class,memberId))jdbc.update("delete from payment_transaction where order_id=?",oid);
        jdbc.update("delete from member_entitlement where member_id=?",memberId);jdbc.update("delete from sales_order where member_id=?",memberId);jdbc.update("delete from member where id=?",memberId);
    }

    @Test void creationIsIndependentAndRetriesDoNotCreateMembersOrOrders() throws Exception {
        var sales=account("SALES",0); long memberCount=members.count(),orderCount=orders.count();
        var request=createRequest(sales,"13812340001");
        var lead=write(sales,"",request,0).path("lead");
        assertThat(lead.path("name").asText()).isEqualTo("待联系潜客");
        assertThat(lead.path("stage").asText()).isEqualTo("CONSULTING");
        assertThat(lead.path("version").asLong()).isZero();
        assertThat(write(sales,"",request,0).path("lead").path("id")).isEqualTo(lead.path("id"));
        request.put("name","更换内容"); write(sales,"",request,1001);
        assertThat(members.count()).isEqualTo(memberCount); assertThat(orders.count()).isEqualTo(orderCount);
        assertThat(read(sales,"/workbench").path("summary").path("totalLeads").asInt()).isEqualTo(1);
    }

    @Test void allReadAndWritePathsEnforceOwnerAndVenueAndRestrictedRoles() throws Exception {
        var sales=account("SALES",0); var other=account("SALES",0); var foreign=account("SALES",1);
        long id=write(sales,"",createRequest(sales,"13812340002"),0).path("lead").path("id").asLong();
        write(other,"",createRequest(other,"13812340003"),0); write(foreign,"",createRequest(foreign,"13812340002"),0);
        assertThat(read(sales,"/workbench").path("leads").path("total").asInt()).isEqualTo(1);
        assertThat(read(account("MANAGER",0),"/workbench").path("summary").path("totalLeads").asInt()).isEqualTo(2);
        assertThat(read(account("SUPER_ADMIN",0),"/workbench").path("summary").path("totalLeads").asInt()).isEqualTo(3);
        for(var denied:List.of(other,foreign,account("MANAGER",1))) {
            readCode(denied,"/"+id,1003); readCode(denied,"/"+id+"/conversion-options",1003);
            write(denied,"/"+id+"/profile",Map.of("expectedVersion",0,"name","越权","intent","LOW","ownerId",denied.getId(),"idempotencyKey",UUID.randomUUID().toString()),1003);
        }
        readCode(sales,"/options?venueId="+foreign.getVenueId(),1003);
        var options=read(sales,"/options");
        assertThat(options.path("advisors").toString()).contains(sales.getRealName()).contains(other.getRealName()).doesNotContain(foreign.getRealName()).doesNotContain("phone");
        for(var role:List.of("RECEPTION","COACH","MEMBER")) {
            mvc.perform(get("/api/v1/leads/workbench").header("Authorization",token(account(role,0)))).andExpect(status().isForbidden());
        }
    }

    private Map<String,Object> createRequest(SysUserEntity owner,String phone) {
        var request=new LinkedHashMap<String,Object>(); request.put("name","待联系潜客"); request.put("phone",phone);
        request.put("source","WECHAT"); request.put("intent","HIGH"); request.put("ownerId",owner.getId());
        request.put("venueId",owner.getVenueId()); request.put("idempotencyKey",UUID.randomUUID().toString()); return request;
    }
    private JsonNode write(SysUserEntity user,String path,Object request,int code) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/leads"+path).header("Authorization",token(user))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode read(SysUserEntity user,String path) throws Exception { return readCode(user,path,0); }
    private JsonNode readCode(SysUserEntity user,String path,int code) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/leads"+path).header("Authorization",token(user)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");
    }
    private SysUserEntity account(String code,int index) {
        return accountForVenue(code,venues.findAll().get(index));
    }
    private SysUserEntity accountForVenue(String code,com.gym.fitness.module.organization.entity.VenueEntity v) {
        var u=new SysUserEntity(); var r=roles.findByCode(code).orElseThrow();
        u.setUsername("lead-"+UUID.randomUUID()); u.setRealName(u.getUsername()); u.setPasswordHash("unused-test-hash");
        u.setRoleId(r.getId()); u.setRole(r); u.setVenueId(v.getId()); u.setVenue(v); u.setStatus("ACTIVE");
        u.setMustChangePassword(false); u.setCoach(false); return users.saveAndFlush(u);
    }
    private String token(SysUserEntity u) { return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion()); }
}
