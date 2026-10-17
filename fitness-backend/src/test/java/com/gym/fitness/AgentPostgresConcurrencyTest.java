package com.gym.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.course.entity.CourseEntity;
import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.course.repository.CourseRepository;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.RoleRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.security.JwtTokenProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in only: this suite must never use the application's normal database. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false", "app.ai.enabled=false", "fitness.operations.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=10"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "agent.test.pgurl", matches = ".+")
class AgentPostgresConcurrencyTest {
    @DynamicPropertySource
    static void isolatedPostgres(DynamicPropertyRegistry registry) {
        String url = System.getProperty("agent.test.pgurl");
        if (!"jdbc:postgresql://127.0.0.1:55451/agent_junit".equals(url)) {
            throw new IllegalArgumentException("Only exact isolated agent_junit JDBC is allowed");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "agent_test");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SysUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired VenueRepository venues;
    @Autowired MemberRepository members;
    @Autowired CourseRepository courses;
    @Autowired CourseScheduleRepository schedules;
    @Autowired JwtTokenProvider jwt;

    private record Fixture(SysUserEntity actor, MemberEntity member, CourseScheduleEntity schedule,
                           String sessionId, String bearer) { }

    @Test
    void freshPostgresRunsV1ThroughV25AndHibernateValidatesTheMigratedSchema() {
        flyway.validate();
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("agent_junit");
        assertThat(jdbc.queryForObject("select inet_server_port()", Integer.class)).isEqualTo(55451);
        assertThat(jdbc.queryForObject("select current_setting('server_version_num')::int", Integer.class)).isGreaterThanOrEqualTo(180000);
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank", String.class))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 25).mapToObj(Integer::toString).toList());
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_schema='public' and table_name='agent_action' and column_name='arguments_json'", String.class)).isEqualTo("text");
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns where table_schema='public' and table_name='agent_action' and column_name='result_json'", String.class)).isEqualTo("text");
    }

    @Test
    void simultaneousConfirmationOfOneActionCommitsOneBookingAndReturnsTheSameReceipt() throws Exception {
        var fixture = fixture("MEMBER", "REGULAR", 3);
        String action = preview(fixture);
        var responses = raceOnAction(action,
                () -> confirm(fixture.bearer(), action), () -> confirm(fixture.bearer(), action));
        assertThat(responses).allSatisfy(r -> assertThat(r.path("code").asInt()).isZero());
        var first = responses.get(0).path("data");
        var second = responses.get(1).path("data");
        assertThat(first.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(second.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(first.path("result").path("id").asLong()).isPositive();
        assertThat(second.path("result")).isEqualTo(first.path("result"));
        assertThat(bookingCount(fixture)).isEqualTo(1);
        assertThat(enrolled(fixture)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from agent_action where id=?", Long.class, action)).isEqualTo(1);
        assertThat(confirm(fixture.bearer(), action).path("data").path("result")).isEqualTo(first.path("result"));
        assertThat(bookingCount(fixture)).isEqualTo(1);
    }

    @Test
    void confirmAndCancelRaceHasOneTerminalOutcomeAndNeverADuplicateWrite() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            var fixture = fixture("MEMBER", "REGULAR", 3);
            String action = preview(fixture);
            var responses = raceOnAction(action, () -> confirm(fixture.bearer(), action),
                    () -> request(fixture.bearer(), "/api/v1/agent/actions/" + action + "/cancel", Map.of(), 200));
            String persisted = actionStatus(action);
            assertThat(persisted).isIn("SUCCEEDED", "CANCELLED");
            assertThat(responses.get(1).path("code").asInt()).isZero();
            if ("SUCCEEDED".equals(persisted)) {
                assertThat(responses.get(0).path("code").asInt()).isZero();
                assertThat(responses.get(0).path("data").path("result"))
                        .isEqualTo(responses.get(1).path("data").path("result"));
                assertThat(bookingCount(fixture)).isEqualTo(1);
                assertThat(enrolled(fixture)).isEqualTo(1);
            } else {
                assertThat(responses.get(0).path("code").asInt()).isEqualTo(2600);
                assertThat(responses.get(1).path("data").path("status").asText()).isEqualTo("CANCELLED");
                assertNoBooking(fixture, action);
            }
        }
    }

    @Test
    void capacityFilledWhileConfirmationWaitsIsRecheckedAndLeavesOnlyAFailedAudit() throws Exception {
        var fixture = fixture("MEMBER", "REGULAR", 1);
        String action = preview(fixture);
        var executor = Executors.newSingleThreadExecutor();
        try (var gate = datasource.getConnection()) {
            lock(gate, "select id from course_schedule where id=? for update", fixture.schedule().getId());
            var pending = executor.submit(() -> confirm(fixture.bearer(), action));
            awaitLockWaiters("course_schedule", 1);
            try (var update = gate.prepareStatement("update course_schedule set enrolled_count=1 where id=?")) {
                update.setLong(1, fixture.schedule().getId());
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            gate.commit();
            var response = pending.get(20, TimeUnit.SECONDS);
            assertThat(response.path("code").asInt()).isZero();
            assertThat(response.path("data").path("status").asText()).isEqualTo("FAILED");
            assertThat(response.path("data").path("error").asText()).isNotBlank();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, TimeUnit.SECONDS);
        }
        assertThat(actionStatus(action)).isEqualTo("FAILED");
        assertNoBooking(fixture, action);
        assertThat(enrolled(fixture)).isEqualTo(1);
    }

    @Test
    void latePrivateEntitlementFailureRollsBackTheInsertedBookingAndCapacity() throws Exception {
        var fixture = fixture("RECEPTION", "PRIVATE", 2);
        String action = preview(fixture);
        Long sequenceBefore = jdbc.queryForObject("select last_value from booking_id_seq", Long.class);
        var response = confirm(fixture.bearer(), action);
        assertThat(response.path("code").asInt()).isZero();
        assertThat(response.path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(response.path("data").path("error").asText()).isNotBlank();
        assertThat(actionStatus(action)).isEqualTo("FAILED");
        assertNoBooking(fixture, action);
        assertThat(enrolled(fixture)).isZero();
        assertThat(jdbc.queryForObject("select last_value from booking_id_seq", Long.class)).isGreaterThan(sequenceBefore);
        assertThat(jdbc.queryForObject("select count(*) from member_entitlement where member_id=?", Long.class, fixture.member().getId())).isZero();
        assertThat(jdbc.queryForObject("select coalesce(sum(reserved_uses),0) from member_entitlement where member_id=?", Long.class, fixture.member().getId())).isZero();
    }

    @Test
    void logoutRejectsOldCredentialsAndAnotherLoginCannotReuseThePendingAction() throws Exception {
        var fixture = fixture("MEMBER", "REGULAR", 3);
        String action = preview(fixture);
        logout(fixture.bearer());
        var old = request(fixture.bearer(), "/api/v1/agent/actions/" + action + "/confirm", Map.of(), 401);
        assertThat(old.path("code").asInt()).isEqualTo(1100);
        assertForbidden(confirm(token(fixture.actor(), UUID.randomUUID().toString()), action));
        assertThat(actionStatus(action)).isEqualTo("PENDING");
        assertNoBooking(fixture, action);
    }

    @Test
    void changedRoleRejectsBothOldCredentialsAndANewRoleTokenForThePriorPreview() throws Exception {
        assertIdentityChangeBetweenRequests(fixture -> changeRole(fixture.actor().getId()));
    }

    @Test
    void changedVenueRejectsBothOldCredentialsAndANewVenueTokenForThePriorPreview() throws Exception {
        assertIdentityChangeBetweenRequests(fixture -> changeVenue(fixture.actor().getId()));
    }

    @Test
    void logoutWhileConfirmationWaitsInvalidatesTheAlreadyAuthenticatedRequest() throws Exception {
        assertIdentityChangeWhileWaiting(fixture -> {
            try { logout(fixture.bearer()); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        });
    }

    @Test
    void roleChangeWhileConfirmationWaitsIsCheckedAfterTheActionLock() throws Exception {
        assertIdentityChangeWhileWaiting(fixture -> changeRole(fixture.actor().getId()));
    }

    @Test
    void venueChangeWhileConfirmationWaitsIsCheckedAfterTheActionLock() throws Exception {
        assertIdentityChangeWhileWaiting(fixture -> changeVenue(fixture.actor().getId()));
    }

    private void assertIdentityChangeBetweenRequests(Consumer<Fixture> change) throws Exception {
        var fixture = fixture("RECEPTION", "REGULAR", 3);
        String action = preview(fixture);
        change.accept(fixture);
        assertThat(request(fixture.bearer(), "/api/v1/agent/actions/" + action + "/confirm", Map.of(), 401)
                .path("code").asInt()).isEqualTo(1100);
        var changed = users.findById(fixture.actor().getId()).orElseThrow();
        assertForbidden(confirm(token(changed, fixture.sessionId()), action));
        assertThat(actionStatus(action)).isEqualTo("PENDING");
        assertNoBooking(fixture, action);
    }

    private void assertIdentityChangeWhileWaiting(Consumer<Fixture> change) throws Exception {
        var fixture = fixture("RECEPTION", "REGULAR", 3);
        String action = preview(fixture);
        var executor = Executors.newSingleThreadExecutor();
        try (var gate = datasource.getConnection()) {
            lock(gate, "select id from agent_action where id=? for update", action);
            var pending = executor.submit(() -> confirm(fixture.bearer(), action));
            awaitLockWaiters("agent_action", 1);
            change.accept(fixture);
            gate.commit();
            assertForbidden(pending.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, TimeUnit.SECONDS);
        }
        assertThat(actionStatus(action)).isEqualTo("PENDING");
        assertNoBooking(fixture, action);
        assertThat(enrolled(fixture)).isZero();
    }

    private List<JsonNode> raceOnAction(String action, Callable<JsonNode> first, Callable<JsonNode> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try (var gate = datasource.getConnection()) {
            lock(gate, "select id from agent_action where id=? for update", action);
            var one = executor.submit(() -> { start.await(); return first.call(); });
            var two = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            awaitLockWaiters("agent_action", 2);
            gate.commit();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, TimeUnit.SECONDS);
        }
    }

    private void lock(Connection connection, String sql, Object id) throws Exception {
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (var row = statement.executeQuery()) { assertThat(row.next()).isTrue(); }
        }
    }

    private void awaitLockWaiters(String table, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike ?", Long.class, "%" + table + "%");
            if (waiting != null && waiting >= expected) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Expected " + expected + " actual PostgreSQL lock waiters on " + table);
    }

    private void assertNoBooking(Fixture fixture, String action) {
        assertThat(bookingCount(fixture)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from private_lesson where schedule_id=? and member_id=?", Long.class, fixture.schedule().getId(), fixture.member().getId())).isZero();
        assertThat(jdbc.queryForObject("select result_json from agent_action where id=?", String.class, action)).isNull();
    }

    private long bookingCount(Fixture fixture) {
        return jdbc.queryForObject("select count(*) from booking where schedule_id=? and member_id=?", Long.class, fixture.schedule().getId(), fixture.member().getId());
    }

    private int enrolled(Fixture fixture) {
        return jdbc.queryForObject("select enrolled_count from course_schedule where id=?", Integer.class, fixture.schedule().getId());
    }

    private String actionStatus(String action) { return jdbc.queryForObject("select status from agent_action where id=?", String.class, action); }
    private void assertForbidden(JsonNode response) { assertThat(response.path("code").asInt()).isEqualTo(1003); }

    private void changeRole(Long actor) {
        jdbc.update("update sys_user set role_id=? where id=?", roles.findByCode("MANAGER").orElseThrow().getId(), actor);
    }

    private void changeVenue(Long actor) {
        jdbc.update("update sys_user set venue_id=? where id=?", venues.findAllByOrderByIdAsc().get(1).getId(), actor);
    }

    private void logout(String bearer) throws Exception {
        assertThat(request(bearer, "/api/v1/auth/logout", Map.of(), 200).path("code").asInt()).isZero();
    }

    private String preview(Fixture fixture) throws Exception {
        Map<String, Long> arguments = "MEMBER".equals(fixture.actor().getRole().getCode())
                ? Map.of("scheduleId", fixture.schedule().getId())
                : Map.of("scheduleId", fixture.schedule().getId(), "memberId", fixture.member().getId());
        var result = request(fixture.bearer(), "/api/v1/agent/actions/preview",
                Map.of("tool", "book_course", "arguments", arguments), 200);
        assertThat(result.path("code").asInt()).isZero();
        assertThat(result.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(bookingCount(fixture)).isZero();
        return result.path("data").path("id").asText();
    }

    private JsonNode confirm(String bearer, String action) throws Exception {
        return request(bearer, "/api/v1/agent/actions/" + action + "/confirm", Map.of(), 200);
    }

    private JsonNode request(String bearer, String path, Object body, int httpStatus) throws Exception {
        return json.readTree(mvc.perform(post(path).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().is(httpStatus)).andReturn().getResponse().getContentAsString());
    }

    private Fixture fixture(String role, String type, int capacity) {
        return tx(() -> {
            var actor = account(role);
            var coach = account("COACH");
            var member = new MemberEntity();
            member.setMemberNo("APG-" + UUID.randomUUID());
            member.setRealName("隔离并发学员");
            member.setPhone("13800000000");
            member.setUserId(actor.getId());
            member.setCoachId(coach.getId());
            member.setVenueId(actor.getVenueId());
            member.setStatus("ACTIVE");
            member.setBlacklistFlag(false);
            members.saveAndFlush(member);
            var course = new CourseEntity();
            course.setName("Agent PG隔离课程");
            course.setType(type);
            course.setVenueId(actor.getVenueId());
            course.setStatus("ACTIVE");
            courses.saveAndFlush(course);
            var schedule = new CourseScheduleEntity();
            schedule.setCourseId(course.getId());
            schedule.setCourse(course);
            schedule.setVenueId(actor.getVenueId());
            schedule.setCoachId(coach.getId());
            schedule.setScheduledStartTime(LocalDateTime.now().plusDays(2));
            schedule.setScheduledEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
            schedule.setMaxStudents(capacity);
            schedule.setEnrolledCount(0);
            schedule.setStatus("SCHEDULED");
            schedules.saveAndFlush(schedule);
            String session = UUID.randomUUID().toString();
            return new Fixture(actor, member, schedule, session, token(actor, session));
        });
    }

    private SysUserEntity account(String role) {
        var actor = new SysUserEntity();
        var assignedRole = roles.findByCode(role).orElseThrow();
        var venue = venues.findAllByOrderByIdAsc().get(0);
        actor.setUsername("agent-pg-" + UUID.randomUUID());
        actor.setPasswordHash("isolated-unused");
        actor.setRealName("隔离" + role);
        actor.setRoleId(assignedRole.getId());
        actor.setRole(assignedRole);
        actor.setVenueId(venue.getId());
        actor.setVenue(venue);
        actor.setCoach("COACH".equals(role));
        actor.setStatus("ACTIVE");
        return users.saveAndFlush(actor);
    }

    private String token(SysUserEntity actor, String session) {
        return "Bearer " + jwt.generateToken(actor.getId(), actor.getUsername(), actor.getRole().getCode(),
                actor.getVenueId(), actor.currentSessionVersion(), session);
    }

    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status -> work.get()); }
}
