package com.gym.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fitness_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Keep tests deterministic and never consume a configured provider's paid quota.
        "app.ai.enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FitnessApplicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    @Order(1)
    void loginDashboardAndAssistantAreAvailable() throws Exception {
        String token = login("admin", "SUPER_ADMIN", "总部管理员");

        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMembers").value(18))
                .andExpect(jsonPath("$.data.courses").value(4));

        mvc.perform(post("/api/v1/admin/assistant/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"如何预约课程？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").isNotEmpty())
                .andExpect(jsonPath("$.data.source").value("本地知识库"))
                .andExpect(jsonPath("$.data.aiEnabled").value(false));
        mvc.perform(get("/api/v1/admin/assistant/status").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.fallbackAvailable").value(true));
    }

    @Test
    @Order(2)
    void rolePermissionMatrixIsEnforced() throws Exception {
        String manager = login("manager", "MANAGER", "店长");
        String coach = login("coach", "COACH", "教练");
        String reception = login("reception", "RECEPTION", "前台");
        String member = login("member", "MEMBER", "会员");

        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("CURRENT_VENUE"));

        mvc.perform(get("/api/v1/admin/courses").header("Authorization", bearer(coach)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", bearer(coach)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/members").header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权课程\",\"type\":\"GROUP\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/members").header("Authorization", bearer(reception)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", bearer(reception)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(reception)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/courses").header("Authorization", bearer(member)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/bookings").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/assistant/chat")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"有哪些课程？\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void storeManagerCannotReadAnotherStoresCourse() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String manager = login("manager", "MANAGER", "店长");
        String venuesJson = mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long secondVenueId = mapper.readTree(venuesJson).path("data").get(1).path("id").asLong();

        mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"滨江隔离测试课\",\"type\":\"GROUP\",\"venueId\":" + secondVenueId + "}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/courses").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '滨江隔离测试课')]").isNotEmpty());
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '滨江隔离测试课')]").isEmpty());
    }

    @Test
    @Order(4)
    void memberSelfServiceAndCoachWorkbenchAreAvailable() throws Exception {
        String member = login("member", "MEMBER", "会员");
        String coach = login("coach", "COACH", "教练");
        String manager = login("manager", "MANAGER", "店长");

        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberNo").isNotEmpty())
                .andExpect(jsonPath("$.data.realName").value("林晓雨"));

        String schedulesJson = mvc.perform(get("/api/v1/member/schedules")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();
        JsonNode available = mapper.readTree(schedulesJson).path("data");

        mvc.perform(post("/api/v1/member/recommendations")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"减脂\",\"preferredTime\":\"EVENING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("可解释规则引擎"))
                .andExpect(jsonPath("$.data.algorithmVersion").value("hybrid-rule-v1"))
                .andExpect(jsonPath("$.data.recommendations").isNotEmpty())
                .andExpect(jsonPath("$.data.recommendations[0].score").isNumber())
                .andExpect(jsonPath("$.data.recommendations[0].reasons").isArray());
        mvc.perform(post("/api/v1/member/recommendations")
                        .header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"增肌\",\"preferredTime\":\"ANY\"}"))
                .andExpect(status().isForbidden());

        Long scheduleId = null;
        for (JsonNode schedule : available) {
            if (!schedule.path("booked").asBoolean() && !schedule.path("full").asBoolean()) {
                scheduleId = schedule.path("scheduleId").asLong();
                break;
            }
        }
        if (scheduleId == null) throw new AssertionError("演示数据中应至少有一节可预约课程");

        mvc.perform(post("/api/v1/member/bookings")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/v1/member/bookings")
                        .header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2301));

        String bookingsJson = mvc.perform(get("/api/v1/member/bookings")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.scheduleId == " + scheduleId + ")]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long bookingId = 0L;
        for (JsonNode booking : mapper.readTree(bookingsJson).path("data")) {
            if (booking.path("scheduleId").asLong() == scheduleId) {
                bookingId = booking.path("id").asLong();
                break;
            }
        }
        mvc.perform(put("/api/v1/member/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/coach/workbench").header("Authorization", bearer(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.upcomingSchedules").isNumber())
                .andExpect(jsonPath("$.data.schedules").isNotEmpty());
        mvc.perform(get("/api/v1/coach/workbench").header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/coach/workbench").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void employeeCreationMemberRegistrationAndPasswordLifecycleWorkEndToEnd() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String manager = login("manager", "MANAGER", "店长");
        String reception = login("reception", "RECEPTION", "前台");

        String venuesJson = mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode firstVenue = mapper.readTree(venuesJson).path("data").get(0);
        long venueId = firstVenue.path("id").asLong();
        String venueCode = firstVenue.path("code").asText();

        mvc.perform(post("/api/v1/admin/accounts/employees")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"storemanager2","password":"TempPass@2026","realName":"新店长",
                                 "phone":"13900001001","roleCode":"MANAGER","venueId":%d}
                                """.formatted(venueId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value("MANAGER"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        String firstLogin = loginResponse("storemanager2", "TempPass@2026");
        String firstToken = mapper.readTree(firstLogin).path("data").path("accessToken").asText();
        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(firstToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1105));
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"TempPass@2026\",\"newPassword\":\"ManagerNew@2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        String changedLogin = loginResponse("storemanager2", "ManagerNew@2026");
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(changedLogin).path("data").path("userInfo")
                .path("mustChangePassword").asBoolean()).isFalse();

        mvc.perform(post("/api/v1/admin/accounts/employees")
                        .header("Authorization", bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"illegalmanager","password":"TempPass@2026","realName":"越权店长",
                                 "roleCode":"MANAGER","venueId":%d}
                                """.formatted(venueId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(2602));
        mvc.perform(post("/api/v1/admin/accounts/employees")
                        .header("Authorization", bearer(manager)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"newcoach","password":"TempPass@2026","realName":"新教练",
                                 "phone":"13900001002","roleCode":"COACH","venueId":%d}
                                """.formatted(venueId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.venueId").value(venueId));
        mvc.perform(get("/api/v1/admin/accounts/employees").header("Authorization", bearer(reception)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/auth/venues")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
        mvc.perform(post("/api/v1/auth/register/member").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"selfmember","password":"MemberSelf@2026","realName":"自助会员",
                                 "phone":"13900001003","email":"selfmember@example.com","venueCode":"%s"}
                                """.formatted(venueCode)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.memberNo").isNotEmpty())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        String selfMemberToken = mapper.readTree(loginResponse("selfmember", "MemberSelf@2026"))
                .path("data").path("accessToken").asText();
        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(selfMemberToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.realName").value("自助会员"));

        String membersJson = mvc.perform(get("/api/v1/admin/members?size=100").header("Authorization", bearer(reception)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode unlinked = null;
        for (JsonNode item : mapper.readTree(membersJson).path("data").path("items")) {
            if (!item.path("accountOpened").asBoolean()) { unlinked = item; break; }
        }
        if (unlinked == null) throw new AssertionError("演示数据中应至少有一个未开通账号的会员");
        long memberId = unlinked.path("id").asLong();
        mvc.perform(post("/api/v1/admin/members/{id}/account", memberId)
                        .header("Authorization", bearer(reception)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"openedmember\",\"password\":\"MemberTemp@2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(true));
        String openedToken = mapper.readTree(loginResponse("openedmember", "MemberTemp@2026"))
                .path("data").path("accessToken").asText();
        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(openedToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1105));
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(openedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"MemberTemp@2026\",\"newPassword\":\"MemberReady@2026\"}"))
                .andExpect(status().isOk());
        String readyToken = mapper.readTree(loginResponse("openedmember", "MemberReady@2026"))
                .path("data").path("accessToken").asText();
        mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(readyToken)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void commercialLifecycleQrAccessAndWaitlistWorkEndToEnd() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String member = login("member", "MEMBER", "会员");

        mvc.perform(get("/api/v1/commercial/overview").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthRevenue").isNumber())
                .andExpect(jsonPath("$.data.activeEntitlements").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/v1/commercial/products").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(10));
        mvc.perform(get("/api/v1/commercial/orders").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNotEmpty());
        mvc.perform(get("/api/v1/commercial/payments").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNotEmpty());
        mvc.perform(get("/api/v1/commercial/documents").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNotEmpty());

        String ownEntitlements = mvc.perform(get("/api/v1/commercial/me/entitlements")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long entitlementId = mapper.readTree(ownEntitlements).path("data").get(0).path("id").asLong();
        mvc.perform(post("/api/v1/commercial/entitlements/{id}/freeze", entitlementId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"集成测试冻结\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FROZEN"));
        mvc.perform(post("/api/v1/commercial/entitlements/{id}/unfreeze", entitlementId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"集成测试解冻\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));

        String accessJson = mvc.perform(get("/api/v1/commercial/me/access-code")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.qrPayload").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String qrPayload = mapper.readTree(accessJson).path("data").path("qrPayload").asText();
        mvc.perform(post("/api/v1/commercial/access/scan").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("token", qrPayload, "direction", "IN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("APPROVED"));

        String scheduleJson = mvc.perform(get("/api/v1/member/schedules").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long fullScheduleId = null;
        for (JsonNode item : mapper.readTree(scheduleJson).path("data")) {
            if (item.path("full").asBoolean() && !item.path("booked").asBoolean()) {
                fullScheduleId = item.path("scheduleId").asLong();
                break;
            }
        }
        if (fullScheduleId != null) {
            mvc.perform(post("/api/v1/member/schedules/{id}/waitlist", fullScheduleId)
                            .header("Authorization", bearer(member)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            mvc.perform(get("/api/v1/member/bookings").header("Authorization", bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.scheduleId == " + fullScheduleId + ")].status").value("WAITLISTED"));
        }
    }

    @Test
    @Order(7)
    void expiredAccessTokenReturns401AndRefreshTokenRestoresTheSession() throws Exception {
        JsonNode initialSession = mapper.readTree(loginResponse("admin", "password")).path("data");
        JsonNode userInfo = initialSession.path("userInfo");
        String refreshToken = initialSession.path("refreshToken").asText();

        long originalExpiration = (long) ReflectionTestUtils.getField(tokenProvider, "jwtExpiration");
        String expiredToken;
        try {
            ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", -1000L);
            expiredToken = tokenProvider.generateToken(
                    userInfo.path("id").asLong(),
                    userInfo.path("username").asText(),
                    userInfo.path("role").asText(),
                    userInfo.path("organizationId").asLong());
        } finally {
            ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", originalExpiration);
        }

        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1101));
        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1100));
        mvc.perform(get("/api/v1/admin/reports/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));

        String refreshedJson = mvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", bearer(expiredToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String refreshedAccessToken = mapper.readTree(refreshedJson).path("data").path("accessToken").asText();

        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(refreshedAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(8)
    void malformedRequestsYieldActionableClientErrorsInsteadOfServerErrors() throws Exception {
        String token = login("admin", "SUPER_ADMIN", "总部管理员");

        // 缺少必填查询参数：必须点名缺的是哪个参数，而不是笼统的"系统异常"
        mvc.perform(get("/api/v1/finance/report").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("from")));

        // 参数类型不合法（日期格式错误）
        mvc.perform(get("/api/v1/finance/report")
                        .param("from", "not-a-date").param("to", "2026-09-21")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        // 请求体 JSON 语法错误
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        // 请求内容类型不支持（例如误用表单提交）
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=admin&password=password"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(1008));

        // HTTP 方法不支持
        mvc.perform(delete("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(1005));

        // 不存在的接口路径（已认证）
        mvc.perform(get("/api/v1/not-a-real-endpoint").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    @Order(9)
    void courseDeletionAndVenueChangeAreGuardedByExistingSchedules() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String venuesJson = mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long ownVenueId = mapper.readTree(venuesJson).path("data").get(0).path("id").asLong();
        long otherVenueId = mapper.readTree(venuesJson).path("data").get(1).path("id").asLong();

        // 无排课的课程应可被删除，并由超级管理员指定所属场馆。
        String created = mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"可删除测试课\",\"type\":\"GROUP\",\"venueId\":" + ownVenueId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.venueId").value(ownVenueId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long deletableId = mapper.readTree(created).path("data").path("id").asLong();

        mvc.perform(delete("/api/v1/admin/courses/{id}", deletableId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + deletableId + ")]").isEmpty());

        // 已有排课的课程既不能删除，也不能变更所属场馆。
        String withSchedule = mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"有排课测试课\",\"type\":\"GROUP\",\"venueId\":" + ownVenueId
                                + ",\"durationMinutes\":45,\"maxCapacity\":10,\"fee\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long busyId = mapper.readTree(withSchedule).path("data").path("id").asLong();

        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(2).withNano(0);
        mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + busyId + ",\"coachName\":\"测试教练\","
                                + "\"scheduledStartTime\":\"" + start + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(delete("/api/v1/admin/courses/{id}", busyId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能删除")));

        mvc.perform(put("/api/v1/admin/courses/{id}", busyId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"有排课测试课\",\"type\":\"GROUP\",\"venueId\":" + otherVenueId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能变更所属场馆")));

        // 被拒绝的删除不得改动数据：课程仍然存在。
        mvc.perform(get("/api/v1/admin/courses").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + busyId + ")]").isNotEmpty());

        // 被拒绝的场馆变更同样不得落库：课程还在原场馆，所以再次改场馆依旧会触发守卫。
        // （若上一次已写入，这次的目标场馆就等于当前场馆，守卫不会再报错。）
        mvc.perform(put("/api/v1/admin/courses/{id}", busyId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"有排课测试课\",\"type\":\"GROUP\",\"venueId\":" + otherVenueId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能变更所属场馆")));
    }

    @Test
    @Order(10)
    void scheduleEditAndDeleteRespectExistingReservations() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String member = login("member", "MEMBER", "会员");
        String coach = login("coach", "COACH", "教练");

        String profile = mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long venueId = mapper.readTree(profile).path("data").path("venueId").asLong();

        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(3).withNano(0);
        long courseId = mapper.readTree(mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"排课增删改测试课\",\"type\":\"GROUP\",\"venueId\":" + venueId
                                + ",\"durationMinutes\":45,\"maxCapacity\":10,\"fee\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 无预约的排课可以改教练、改时间，也可以删除。
        long freeScheduleId = mapper.readTree(mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"原教练\","
                                + "\"scheduledStartTime\":\"" + start + "\",\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        mvc.perform(put("/api/v1/admin/schedules/{id}", freeScheduleId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"改后教练\","
                                + "\"scheduledStartTime\":\"" + start.plusHours(2) + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(3) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.coachName").value("改后教练"));

        // 结束时间不能早于开始时间
        mvc.perform(put("/api/v1/admin/schedules/{id}", freeScheduleId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"改后教练\","
                                + "\"scheduledStartTime\":\"" + start.plusHours(3) + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(2) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));

        // 不存在的排课
        mvc.perform(put("/api/v1/admin/schedules/999999")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"x\","
                                + "\"scheduledStartTime\":\"" + start + "\",\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2201));

        mvc.perform(delete("/api/v1/admin/schedules/{id}", freeScheduleId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/admin/schedules").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + freeScheduleId + ")]").isEmpty());

        // 已被会员预约的排课：既不能删除，也不能更换课程。
        long busyScheduleId = mapper.readTree(mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"原教练\","
                                + "\"scheduledStartTime\":\"" + start.plusDays(1) + "\","
                                + "\"scheduledEndTime\":\"" + start.plusDays(1).plusHours(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        mvc.perform(post("/api/v1/member/bookings")
                        .header("Authorization", bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + busyScheduleId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(delete("/api/v1/admin/schedules/{id}", busyScheduleId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能删除")));

        long otherCourseId = mapper.readTree(mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"排课换课目标课\",\"type\":\"GROUP\",\"venueId\":" + venueId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        mvc.perform(put("/api/v1/admin/schedules/{id}", busyScheduleId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + otherCourseId + ",\"coachName\":\"原教练\","
                                + "\"scheduledStartTime\":\"" + start.plusDays(1) + "\","
                                + "\"scheduledEndTime\":\"" + start.plusDays(1).plusHours(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能更换课程")));

        // 教练没有 SCHEDULE_WRITE，改与删都应被拒。
        mvc.perform(put("/api/v1/admin/schedules/{id}", freeScheduleId)
                        .header("Authorization", bearer(coach)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"x\","
                                + "\"scheduledStartTime\":\"" + start + "\",\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/schedules/{id}", busyScheduleId).header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void schedulesBoundToACoachAccountAppearInThatCoachWorkbench() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String coach = login("coach", "COACH", "教练");

        // 教练列表供排课表单使用；纯教练角色没有 SCHEDULE_WRITE，应被拒。
        mvc.perform(get("/api/v1/admin/coaches").header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
        String coachesJson = mvc.perform(get("/api/v1/admin/coaches").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 测试库里存在不止一个 COACH 账号，必须精确定位到"当前登录的这位教练"，不能取列表第一条。
        long coachUserId = mapper.readTree(loginResponse("coach", "password"))
                .path("data").path("userInfo").path("id").asLong();
        JsonNode matchedCoach = null;
        for (JsonNode candidate : mapper.readTree(coachesJson).path("data")) {
            if (candidate.path("id").asLong() == coachUserId) {
                matchedCoach = candidate;
                break;
            }
        }
        if (matchedCoach == null) throw new AssertionError("教练列表应包含当前登录的教练账号");
        String coachDisplayName = matchedCoach.path("name").asText();
        long coachVenueId = matchedCoach.path("venueId").asLong();

        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(4).withNano(0);
        long courseId = mapper.readTree(mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"教练绑定测试课\",\"type\":\"GROUP\",\"venueId\":" + coachVenueId
                                + ",\"durationMinutes\":45,\"maxCapacity\":10,\"fee\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 只填教练姓名、没有 coachId：教练工作台看不到（这正是本轮要修的场景）。
        long namedOnlyId = mapper.readTree(mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"临时客串教练\","
                                + "\"scheduledStartTime\":\"" + start + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 绑定真实教练账号：教练工作台应能看到。
        String boundJson = mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachId\":" + coachUserId
                                + ",\"coachName\":\"" + coachDisplayName + "\","
                                + "\"scheduledStartTime\":\"" + start.plusHours(2) + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(3) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.coachId").value(coachUserId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long boundId = mapper.readTree(boundJson).path("data").path("id").asLong();

        mvc.perform(get("/api/v1/coach/workbench").header("Authorization", bearer(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedules[?(@.id == " + boundId + ")]").isNotEmpty())
                .andExpect(jsonPath("$.data.schedules[?(@.id == " + namedOnlyId + ")]").isEmpty());

        // 编辑排课时也能补齐教练账号：把只填了姓名的那条改成绑定真实教练后即可见。
        mvc.perform(put("/api/v1/admin/schedules/{id}", namedOnlyId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachId\":" + coachUserId
                                + ",\"coachName\":\"" + coachDisplayName + "\","
                                + "\"scheduledStartTime\":\"" + start + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/coach/workbench").header("Authorization", bearer(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedules[?(@.id == " + namedOnlyId + ")]").isNotEmpty());
    }

    @Test
    @Order(12)
    void memberCanPurchaseMembershipWithoutSettingPriceOrChannel() throws Exception {
        String member = login("member", "MEMBER", "会员");
        String manager = login("manager", "MANAGER", "店长");

        // 员工角色不能访问会员自助购买
        mvc.perform(get("/api/v1/member/products").header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());

        String productsJson = mvc.perform(get("/api/v1/member/products").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = mapper.readTree(productsJson).path("data");
        // 会员端现在返回"全部在售商品"（含其他门店，仅展示不可买），必须挑 purchasable=true 的那款下单。
        JsonNode purchasable = null;
        boolean hasVenueFlag = true;
        for (JsonNode item : data) {
            if (!item.has("purchasable") || !item.has("venueName")) hasVenueFlag = false;
            if (purchasable == null && item.path("purchasable").asBoolean()) purchasable = item;
        }
        org.assertj.core.api.Assertions.assertThat(hasVenueFlag).isTrue();
        if (purchasable == null) throw new AssertionError("会员端应至少有一款本门店可购买的商品");
        long productId = purchasable.path("id").asLong();
        double price = purchasable.path("price").asDouble();

        int before = mapper.readTree(mvc.perform(get("/api/v1/commercial/me/entitlements").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").size();

        // 只传 productId，不传金额和渠道：金额取服务端商品标价，渠道固定为自助。
        mvc.perform(post("/api/v1/member/purchase").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"idempotencyKey\":\"it-member-purchase-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.paidAmount").value(price))
                .andExpect(jsonPath("$.data.paymentMethod").value("SELF_SERVICE"));

        int after = mapper.readTree(mvc.perform(get("/api/v1/commercial/me/entitlements").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").size();
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @Order(13)
    void memberCanOnlyRequestRefundOnOwnEntitlement() throws Exception {
        String member = login("member", "MEMBER", "会员");
        String manager = login("manager", "MANAGER", "店长");

        // 会员侧自助退款接口对员工不开放；员工侧受理接口对会员不开放。
        mvc.perform(get("/api/v1/finance/me/refunds").header("Authorization", bearer(manager)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/finance/refunds").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());

        long myMemberId = mapper.readTree(mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        String mineJson = mvc.perform(get("/api/v1/commercial/me/entitlements").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long myEntitlementId = mapper.readTree(mineJson).path("data").get(0).path("id").asLong();

        // 报价：可退金额与版本号由服务端核定，会员无法指定金额。
        String quoteJson = mvc.perform(get("/api/v1/finance/me/entitlements/{id}/quote", myEntitlementId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.entitlementId").value(myEntitlementId))
                .andExpect(jsonPath("$.data.version").isNumber())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long version = mapper.readTree(quoteJson).path("data").path("version").asLong();

        mvc.perform(get("/api/v1/finance/me/entitlements/999999/quote").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("权益不存在")));

        // 同门店其他会员的权益必须被拒：values.lock 只校验门店范围，不校验持有人。
        String allJson = mvc.perform(get("/api/v1/commercial/entitlements").header("Authorization", bearer(manager)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long otherEntitlementId = 0L;
        for (JsonNode item : mapper.readTree(allJson).path("data")) {
            if (item.path("memberId").asLong() != myMemberId) { otherEntitlementId = item.path("id").asLong(); break; }
        }
        org.assertj.core.api.Assertions.assertThat(otherEntitlementId).isNotZero();
        mvc.perform(get("/api/v1/finance/me/entitlements/{id}/quote", otherEntitlementId)
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("只能对自己名下")));

        // 提交申请：接口对会员开放（不是 403）；被价值账或未完成服务阻断属于业务层结果。
        String submitBody = mvc.perform(post("/api/v1/finance/me/refunds").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entitlementId\":" + myEntitlementId + ",\"version\":" + version
                                + ",\"reason\":\"会员自助退款验收\",\"idempotencyKey\":\"it-self-refund-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(submitBody).path("code").asInt()).isIn(0, 2600);
    }

    @Test
    @Order(14)
    void productDeletionIsRefusedOnceSoldAndOffSaleHidesItFromTheMemberShop() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String coach = login("coach", "COACH", "教练");
        String member = login("member", "MEMBER", "会员");

        // 教练没有 PRODUCT_WRITE
        mvc.perform(delete("/api/v1/commercial/products/1").header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());

        // 从未卖出的商品可以删除（超管建商品必须指定场馆）
        String venuesJson = mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long venueId = mapper.readTree(venuesJson).path("data").get(0).path("id").asLong();
        long freshId = mapper.readTree(mvc.perform(post("/api/v1/commercial/products")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除测试商品\",\"category\":\"CARD\",\"productType\":\"MONTH_CARD\","
                                + "\"validityDays\":30,\"price\":10,\"status\":\"ACTIVE\",\"venueId\":" + venueId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        mvc.perform(delete("/api/v1/commercial/products/{id}", freshId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/commercial/products").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + freshId + ")]").isEmpty());

        // 已经有订单的商品不能删除
        String ordersJson = mvc.perform(get("/api/v1/commercial/orders").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long soldProductId = mapper.readTree(ordersJson).path("data").get(0).path("productId").asLong();
        mvc.perform(delete("/api/v1/commercial/products/{id}", soldProductId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能删除")));

        // 下架：会员端立即不再展示；上架后恢复（测试结束要还原为 ACTIVE）
        String productsJson = mvc.perform(get("/api/v1/commercial/products").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode sold = null;
        for (JsonNode item : mapper.readTree(productsJson).path("data")) {
            if (item.path("id").asLong() == soldProductId) { sold = item; break; }
        }
        if (sold == null) throw new AssertionError("未找到已售出的商品");
        // 课包/次卡必须带 totalUses，否则 applyProduct 会以"必须填写总次数"拒绝。
        String base = "{\"name\":\"" + sold.path("name").asText() + "\",\"category\":\""
                + sold.path("category").asText() + "\",\"productType\":\"" + sold.path("productType").asText()
                + "\",\"validityDays\":" + sold.path("validityDays").asInt() + ",\"price\":"
                + sold.path("price").asDouble() + ",\"accessAllowed\":" + sold.path("accessAllowed").asBoolean()
                + ",\"venueId\":" + sold.path("venueId").asLong()
                + (sold.path("totalUses").isNull() ? "" : ",\"totalUses\":" + sold.path("totalUses").asInt());

        mvc.perform(put("/api/v1/commercial/products/{id}", soldProductId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(base + ",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/member/products").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + soldProductId + ")]").isEmpty());

        mvc.perform(put("/api/v1/commercial/products/{id}", soldProductId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(base + ",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/member/products").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + soldProductId + ")]").isNotEmpty());
    }

    @Test
    @Order(15)
    void privateTrainingReadinessExposesEachPrerequisiteCount() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String manager = login("manager", "MANAGER", "店长");
        String coach = login("coach", "COACH", "教练");
        String member = login("member", "MEMBER", "会员");

        // 管理端与教练可读本门店自检；会员不需要这个诊断接口
        for (String token : new String[]{admin, manager, coach}) {
            mvc.perform(get("/api/v1/training/readiness").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.privateCourses").isNumber())
                    .andExpect(jsonPath("$.data.scheduledPrivateLessons").isNumber())
                    .andExpect(jsonPath("$.data.lessonsWithCoach").isNumber())
                    .andExpect(jsonPath("$.data.usablePackages").isNumber());
        }
        mvc.perform(get("/api/v1/training/readiness").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());

        // 自检必须能反映"排课是否已绑定可用教练账号"：先建一门私教课并排课，不带教练时应为 0
        String venuesJson = mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long venueId = mapper.readTree(venuesJson).path("data").get(0).path("id").asLong();
        long privateCourseId = mapper.readTree(mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"私教自检测试课\",\"type\":\"PRIVATE\",\"venueId\":" + venueId
                                + ",\"durationMinutes\":60,\"maxCapacity\":1,\"fee\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(5).withNano(0);
        long scheduleId = mapper.readTree(mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + privateCourseId + ",\"coachName\":\"占位姓名\","
                                + "\"scheduledStartTime\":\"" + start + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        String withoutCoach = mvc.perform(get("/api/v1/training/readiness").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode before = mapper.readTree(withoutCoach).path("data");
        org.assertj.core.api.Assertions.assertThat(before.path("scheduledPrivateLessons").asInt()).isPositive();
        int withCoachBefore = before.path("lessonsWithCoach").asInt();

        // 绑定真实教练账号后，已绑教练的排课数应当增加
        long coachUserId = mapper.readTree(loginResponse("coach", "password"))
                .path("data").path("userInfo").path("id").asLong();
        mvc.perform(put("/api/v1/admin/schedules/{id}", scheduleId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + privateCourseId + ",\"coachId\":" + coachUserId
                                + ",\"coachName\":\"首席教练\",\"scheduledStartTime\":\"" + start + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        String withCoach = mvc.perform(get("/api/v1/training/readiness").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(mapper.readTree(withCoach).path("data").path("lessonsWithCoach").asInt())
                .isEqualTo(withCoachBefore + 1);
    }

    @Test
    @Order(16)
    void duplicateSaleSubmissionsCollapseIntoOneOrderByTheIdempotencyKey() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String memberToken = login("member", "MEMBER", "会员");

        long memberId = mapper.readTree(mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        String productsJson = mvc.perform(get("/api/v1/commercial/products").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long memberVenueId = mapper.readTree(mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("venueId").asLong();
        // 会员只能买本门店的商品，必须挑同门店上架中的那款
        JsonNode product = null;
        for (JsonNode item : mapper.readTree(productsJson).path("data")) {
            if (item.path("venueId").asLong() == memberVenueId && "ACTIVE".equals(item.path("status").asText())) { product = item; break; }
        }
        if (product == null) throw new AssertionError("演示数据中应有该会员门店上架中的商品");
        long productId = product.path("id").asLong();
        double price = product.path("price").asDouble();
        String key = "it-sale-idempotency-1";
        String body = "{\"memberId\":" + memberId + ",\"productId\":" + productId
                + ",\"paymentMethod\":\"CASH\",\"paidAmount\":" + price + ",\"idempotencyKey\":\"" + key + "\"}";

        long firstOrderId = mapper.readTree(mvc.perform(post("/api/v1/commercial/orders")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 同一个幂等键再提交一次：必须返回同一张单，而不是再开一张卡
        mvc.perform(post("/api/v1/commercial/orders")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(firstOrderId));

        // 同一个键换个内容：必须被识别并拒绝，不能静默复用旧单
        mvc.perform(post("/api/v1/commercial/orders")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + memberId + ",\"productId\":" + productId
                                + ",\"paymentMethod\":\"WECHAT\",\"paidAmount\":" + price
                                + ",\"idempotencyKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("幂等键")));
    }

    @Test
    @Order(17)
    void accessQrOnlyAcceptsShortLivedSingleUseGrants() throws Exception {
        String member = login("member", "MEMBER", "会员");
        String reception = login("reception", "RECEPTION", "前台");

        String firstJson = mvc.perform(get("/api/v1/commercial/me/access-code").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ttlSeconds").isNumber())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token1 = mapper.readTree(firstJson).path("data").path("token").asText();
        String token2 = mapper.readTree(mvc.perform(get("/api/v1/commercial/me/access-code")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("token").asText();
        // 每次取码都是新令牌（不再是那个永不轮换的会员令牌）
        org.assertj.core.api.Assertions.assertThat(token1).isNotEqualTo(token2);

        // 旧的"永久令牌"格式（32 位 hex 字符串）不再被接受
        mvc.perform(post("/api/v1/commercial/access/scan").header("Authorization", bearer(reception))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"0123456789abcdef0123456789abcdef\",\"direction\":\"IN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("DENIED"))
                .andExpect(jsonPath("$.data.reason").value(org.hamcrest.Matchers.containsString("无效二维码")));

        // 新签发的令牌能被识别（是否放行取决于权益，但绝不能再报"无效二维码"）
        mvc.perform(post("/api/v1/commercial/access/scan").header("Authorization", bearer(reception))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token1 + "\",\"direction\":\"IN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("无效二维码"))));
    }

    @Test
    @Order(18)
    void changingScheduleCoachNotifiesBookedMembers() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String member = login("member", "MEMBER", "会员");
        long coachUserId = mapper.readTree(loginResponse("coach", "password"))
                .path("data").path("userInfo").path("id").asLong();
        String venuesJson = mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long venueId = mapper.readTree(venuesJson).path("data").get(0).path("id").asLong();

        long courseId = mapper.readTree(mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"通知测试团课\",\"type\":\"GROUP\",\"venueId\":" + venueId
                                + ",\"durationMinutes\":60,\"maxCapacity\":20,\"fee\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(6).withNano(0);
        long scheduleId = mapper.readTree(mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachId\":" + coachUserId
                                + ",\"coachName\":\"首席教练\",\"scheduledStartTime\":\"" + start + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        mvc.perform(post("/api/v1/member/bookings").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // 换教练是允许保留预约的变更，会员必须收到站内消息
        mvc.perform(put("/api/v1/admin/schedules/{id}", scheduleId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"coachName\":\"换班教练\","
                                + "\"scheduledStartTime\":\"" + start + "\","
                                + "\"scheduledEndTime\":\"" + start.plusHours(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.category == 'SCHEDULE_CHANGED' && @.refId == " + scheduleId + ")]").isNotEmpty());
    }


    // ------------------------------------------------ 2026-09-23 新功能回归测试

    @Test
    @Order(19)
    void coachCannotBeDoubleBookedInOverlappingSlots() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        long coachUserId = coachUserId();
        long venueId = firstVenueId(admin);
        long courseId = newCourse(admin, "冲突检测团课", "GROUP", venueId);
        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(8)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        mvc.perform(post("/api/v1/admin/schedules").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody(courseId, coachUserId, "首席教练", start, start.plusHours(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        // 与已有排课重叠（10:30~11:30）：必须被拦下，并说明冲突在哪
        java.time.LocalDateTime overlap = start.plusMinutes(30);
        mvc.perform(post("/api/v1/admin/schedules").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody(courseId, coachUserId, "首席教练", overlap, overlap.plusHours(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已有排课")));

        // 首尾相接不算重叠（11:00~12:00 接 10:00~11:00）：应当允许
        mvc.perform(post("/api/v1/admin/schedules").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody(courseId, coachUserId, "首席教练",
                                start.plusHours(1), start.plusHours(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(20)
    void batchSchedulePreviewsConflictsThenCreatesTheRest() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        long coachUserId = coachUserId();
        long venueId = firstVenueId(admin);
        long courseId = newCourse(admin, "批量排课团课", "GROUP", venueId);
        java.time.LocalDate from = java.time.LocalDate.now().plusDays(10);
        java.time.LocalDate to = from.plusDays(2);

        // 先占掉中间那天 19:00~20:00，制造一条可预期的冲突
        java.time.LocalDateTime occupied = java.time.LocalDateTime.of(from.plusDays(1), java.time.LocalTime.of(19, 0));
        mvc.perform(post("/api/v1/admin/schedules").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody(courseId, coachUserId, "首席教练", occupied, occupied.plusHours(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));

        String dry = batchBody(courseId, coachUserId, from, to, true);
        mvc.perform(post("/api/v1/admin/schedules/batch").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(dry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.created.length()").value(2))
                .andExpect(jsonPath("$.data.skipped.length()").value(1))
                .andExpect(jsonPath("$.data.skipped[0].note")
                        .value(org.hamcrest.Matchers.containsString("已有排课")));

        // 预检不得落库：再预检一次结果必须完全一样
        mvc.perform(post("/api/v1/admin/schedules/batch").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(dry))
                .andExpect(jsonPath("$.data.created.length()").value(2))
                .andExpect(jsonPath("$.data.skipped.length()").value(1));

        // 正式生成
        mvc.perform(post("/api/v1/admin/schedules/batch").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(courseId, coachUserId, from, to, false)))
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.created.length()").value(2))
                .andExpect(jsonPath("$.data.skipped.length()").value(1));

        // 生成之后再预检：这 2 节现在也会被判为与该教练冲突，于是 3 节全跳过
        mvc.perform(post("/api/v1/admin/schedules/batch").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(dry))
                .andExpect(jsonPath("$.data.created.length()").value(0))
                .andExpect(jsonPath("$.data.skipped.length()").value(3));
    }

    @Test
    @Order(21)
    void couponDiscountsPaidAmountAndCannotBeReused() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String member = login("member", "MEMBER", "会员");
        long venueId = firstVenueId(admin);
        long memberId = mapper.readTree(mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 满 ¥100 减 ¥20 的券（金额一律用分）
        String today = java.time.LocalDate.now().toString();
        String until = java.time.LocalDate.now().plusDays(30).toString();
        long templateId = mapper.readTree(mvc.perform(post("/api/v1/commercial/coupons")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"满100减20测试券\",\"couponType\":\"FULL_REDUCTION\","
                                + "\"thresholdCents\":10000,\"discountCents\":2000,\"totalQuantity\":5,"
                                + "\"validFrom\":\"" + today + "\",\"validUntil\":\"" + until + "\","
                                + "\"venueId\":" + venueId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        mvc.perform(post("/api/v1/commercial/coupons/{id}/issue", templateId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[" + memberId + "],\"quantityPerMember\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.issued").value(1));

        JsonNode myCoupons = mapper.readTree(mvc.perform(get("/api/v1/commercial/me/coupons")
                        .header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        long couponId = myCoupons.path("data").get(0).path("id").asLong();
        org.assertj.core.api.Assertions.assertThat(myCoupons.path("data").get(0).path("status").asText())
                .isEqualTo("UNUSED");

        // ¥200 的商品
        long productId = mapper.readTree(mvc.perform(post("/api/v1/commercial/products")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"券测试月卡\",\"category\":\"CARD\",\"productType\":\"MONTH_CARD\","
                                + "\"validityDays\":30,\"price\":200.00,\"accessAllowed\":true,"
                                + "\"venueId\":" + venueId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 试算：抵扣 2000 分，应付 180.00
        mvc.perform(get("/api/v1/commercial/members/" + memberId + "/coupon-quote")
                        .param("productId", String.valueOf(productId))
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usableCount").value(1))
                .andExpect(jsonPath("$.data.options[0].discountCents").value(2000))
                .andExpect(jsonPath("$.data.options[0].payable").value(180.00));

        // 用券开卡：实收必须是折后价，且服务端忽略前端传的金额
        mvc.perform(post("/api/v1/commercial/orders").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + memberId + ",\"productId\":" + productId
                                + ",\"paymentMethod\":\"CASH\",\"paidAmount\":200.00,\"memberCouponId\":" + couponId
                                + ",\"idempotencyKey\":\"it-coupon-sale-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.paidAmount").value(180.00))
                .andExpect(jsonPath("$.data.discountCents").value(2000));

        // 券已被核销
        mvc.perform(get("/api/v1/commercial/me/coupons").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("USED"));

        // 同一张券不能再用一次
        mvc.perform(post("/api/v1/commercial/orders").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + memberId + ",\"productId\":" + productId
                                + ",\"paymentMethod\":\"CASH\",\"memberCouponId\":" + couponId
                                + ",\"idempotencyKey\":\"it-coupon-sale-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("优惠券不可用")));
    }

    @Test
    @Order(22)
    void memberCanReviewAnAttendedClassOnlyOnce() throws Exception {
        String admin = login("admin", "SUPER_ADMIN", "总部管理员");
        String member = login("member", "MEMBER", "会员");
        String reception = login("reception", "RECEPTION", "前台");
        String coach = login("coach", "COACH", "教练");
        long coachUserId = coachUserId();
        long venueId = firstVenueId(admin);
        long courseId = newCourse(admin, "评价测试团课", "GROUP", venueId);
        java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(9)
                .withHour(15).withMinute(0).withSecond(0).withNano(0);
        long scheduleId = mapper.readTree(mvc.perform(post("/api/v1/admin/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody(courseId, coachUserId, "首席教练", start, start.plusHours(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        long memberId = mapper.readTree(mvc.perform(get("/api/v1/member/profile").header("Authorization", bearer(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        // 会员端 POST /member/bookings 只返回 Result<Void>，这里用管理端预约接口取 bookingId（归属仍是该会员）
        long bookingId = mapper.readTree(mvc.perform(post("/api/v1/admin/bookings")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + memberId + ",\"scheduleId\":" + scheduleId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 还没签到：不允许评价
        mvc.perform(post("/api/v1/member/reviews").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"rating\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2600))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已签到")));

        // 前台签到后进入待评价列表
        mvc.perform(post("/api/v1/admin/bookings/{id}/check-in", bookingId)
                        .header("Authorization", bearer(reception)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/member/reviews/pending").header("Authorization", bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bookingId == " + bookingId + ")]").isNotEmpty());

        // 提交评价
        mvc.perform(post("/api/v1/member/reviews").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"rating\":5,\"tags\":\"教练专业\","
                                + "\"content\":\"节奏很好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.rating").value(5));

        // 同一条预约不能重复评价
        mvc.perform(post("/api/v1/member/reviews").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"rating\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2601));

        // 教练端与管理端都能看到这条评价
        mvc.perform(get("/api/v1/coach/reviews/summary").header("Authorization", bearer(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.averageRating").value(org.hamcrest.Matchers.notNullValue()));
        mvc.perform(get("/api/v1/admin/reviews/summary").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    private long coachUserId() throws Exception {
        return mapper.readTree(loginResponse("coach", "password")).path("data").path("userInfo").path("id").asLong();
    }

    private long firstVenueId(String admin) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/admin/venues").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").get(0).path("id").asLong();
    }

    private long newCourse(String admin, String name, String type, long venueId) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"venueId\":" + venueId
                                + ",\"durationMinutes\":60,\"maxCapacity\":10,\"fee\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    private String scheduleBody(long courseId, long coachId, String coachName,
                                java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return "{\"courseId\":" + courseId + ",\"coachId\":" + coachId + ",\"coachName\":\"" + coachName
                + "\",\"scheduledStartTime\":\"" + start + "\",\"scheduledEndTime\":\"" + end + "\"}";
    }

    private String batchBody(long courseId, long coachId, java.time.LocalDate from,
                             java.time.LocalDate to, boolean dryRun) {
        return "{\"courseId\":" + courseId + ",\"coachId\":" + coachId + ",\"coachName\":\"首席教练\","
                + "\"weekdays\":[1,2,3,4,5,6,7],\"startTime\":\"19:00\",\"endTime\":\"20:00\","
                + "\"fromDate\":\"" + from + "\",\"toDate\":\"" + to + "\",\"maxStudents\":10,"
                + "\"dryRun\":" + dryRun + "}";
    }

    private String login(String username, String role, String roleName) throws Exception {
        String loginJson = loginResponse(username, "password");
        JsonNode loginNode = mapper.readTree(loginJson);
        org.assertj.core.api.Assertions.assertThat(loginNode.path("code").asInt()).isZero();
        org.assertj.core.api.Assertions.assertThat(loginNode.path("data").path("userInfo").path("role").asText()).isEqualTo(role);
        org.assertj.core.api.Assertions.assertThat(loginNode.path("data").path("userInfo").path("roleName").asText()).isEqualTo(roleName);
        org.assertj.core.api.Assertions.assertThat(loginNode.path("data").path("userInfo").path("permissions").isArray()).isTrue();
        return loginNode.path("data").path("accessToken").asText();
    }

    private String loginResponse(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userInfo.permissions").isArray())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
