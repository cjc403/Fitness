package com.gym.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.organization.repository.RoleRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.gym.fitness.security.JwtTokenProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authentication_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.ai.enabled=false", "fitness.operations.enabled=false"
})
@AutoConfigureMockMvc
class AuthenticationSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SysUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired VenueRepository venues;
    @SpyBean PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokens;
    @Autowired com.gym.fitness.module.auth.repository.RevokedAuthSessionRepository revokedSessions;
    @Autowired com.gym.fitness.module.operations.service.OperationsService operations;
    @Autowired com.gym.fitness.module.auth.service.AuthService authService;
    private SysUserEntity subject;
    private static final String TEST_PASSWORD = "SessionTest@2026";

    @BeforeEach
    void isolatedAccount() {
        subject = new SysUserEntity();
        subject.setUsername("security-" + UUID.randomUUID());
        subject.setRealName("隔离会话测试");
        subject.setPasswordHash(encoder.encode(TEST_PASSWORD));
        var role = roles.findByCode("MANAGER").orElseThrow();
        var venue = venues.findAll().get(0);
        subject.setRoleId(role.getId()); subject.setRole(role);
        subject.setVenueId(venue.getId()); subject.setVenue(venue);
        subject.setStatus("ACTIVE"); subject.setMustChangePassword(false); subject.setCoach(false);
        subject = users.saveAndFlush(subject);
    }

    @Test
    void refreshTokenCannotAuthenticateBusinessRequests() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        mvc.perform(get("/api/v1/notifications/summary").header("Authorization", bearer(session, "refreshToken")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1100));
    }

    @Test
    void disabledAccountCannotKeepUsingAccessToken() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        subject.setStatus("DISABLED"); users.saveAndFlush(subject);
        rejectedAccess(session);
    }

    @Test
    void deletedAccountCannotKeepUsingAccessToken() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        users.deleteById(subject.getId());
        rejectedAccess(session);
    }

    @Test
    void roleChangeRejectsPreviouslyIssuedPrivilegesAndRefresh() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        updateEmployee("RECEPTION", subject.getVenueId());
        rejectedAccess(session); rejectedRefresh(session);
        mvc.perform(get("/api/v1/admin/bookings")
                        .header("Authorization", bearer(login(subject.getUsername(), TEST_PASSWORD), "accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void venueTransferRejectsOldScopeAndRefresh() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        long otherVenue = venues.findAll().stream().filter(v -> !v.getId().equals(subject.getVenueId()))
                .findFirst().orElseThrow().getId();
        updateEmployee("MANAGER", otherVenue);
        rejectedAccess(session); rejectedRefresh(session);
        var fresh = login(subject.getUsername(), TEST_PASSWORD);
        org.assertj.core.api.Assertions.assertThat(fresh.path("userInfo").path("organizationId").asLong()).isEqualTo(otherVenue);
    }

    @Test
    void disablingThenReenablingDoesNotResurrectOldSessions() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        var admin = login("admin", "password");
        for (String state : new String[]{"DISABLED", "ACTIVE"}) {
            mvc.perform(put("/api/v1/admin/accounts/employees/{id}/status", subject.getId())
                            .header("Authorization", bearer(admin, "accessToken"))
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("status", state))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        }
        rejectedAccess(session); rejectedRefresh(session);
    }

    @Test
    void passwordResetRejectsOldAccessAndRefresh() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        mvc.perform(put("/api/v1/admin/accounts/employees/{id}/password", subject.getId())
                        .header("Authorization", bearer(login("admin", "password"), "accessToken"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"ResetSession@2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        rejectedAccess(session); rejectedRefresh(session);
    }

    @Test
    void passwordChangeRevokesOldSessionsAndReturnsUsableReplacement() throws Exception {
        var old = login(subject.getUsername(), TEST_PASSWORD);
        String body = mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(old, "accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("currentPassword", TEST_PASSWORD, "newPassword", "NewSession@2026"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var replacement = mapper.readTree(body).path("data");
        rejectedAccess(old); rejectedRefresh(old);
        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(replacement, "accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void logoutRevokesAccessAndRefresh() throws Exception {
        var old = login(subject.getUsername(), TEST_PASSWORD);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(old, "accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        rejectedAccess(old); rejectedRefresh(old);
    }

    @Test
    void lateLogoutRevokesOnlyItsLoginAndKeepsAnotherDeviceUsable() throws Exception {
        var old = login(subject.getUsername(), TEST_PASSWORD);
        var fresh = login(subject.getUsername(), TEST_PASSWORD);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(old, "accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        rejectedAccess(old); rejectedRefresh(old);
        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(fresh, "accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", fresh.path("refreshToken").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void logoutWithExpiredAccessStillRevokesCapturedRefresh() throws Exception {
        var old = login(subject.getUsername(), TEST_PASSWORD);
        String expired;
        long expiration = (long) ReflectionTestUtils.getField(tokens, "jwtExpiration");
        try {
            ReflectionTestUtils.setField(tokens, "jwtExpiration", -2000L);
            expired = tokens.generateToken(subject.getId(), subject.getUsername(), "MANAGER", subject.getVenueId());
        } finally { ReflectionTestUtils.setField(tokens, "jwtExpiration", expiration); }
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + expired)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", old.path("refreshToken").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        rejectedAccess(old); rejectedRefresh(old);
    }

    @Test
    void passwordResetCannotBeOverwrittenByConcurrentLoginBookkeeping() throws Exception {
        var admin = login("admin", "password");
        var checkingPassword = new CountDownLatch(1);
        var releaseLogin = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (TEST_PASSWORD.contentEquals((CharSequence) invocation.getArgument(0))) {
                checkingPassword.countDown();
                if (!releaseLogin.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("login latch timeout");
            }
            return invocation.callRealMethod();
        }).when(encoder).matches(any(CharSequence.class), anyString());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var loginFuture = pool.submit(() -> login(subject.getUsername(), TEST_PASSWORD));
            assertThat(checkingPassword.await(10, TimeUnit.SECONDS)).isTrue();
            var resetFuture = pool.submit(() -> mvc.perform(put("/api/v1/admin/accounts/employees/{id}/password", subject.getId())
                    .header("Authorization", bearer(admin, "accessToken"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"ConcurrentReset@2026\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)));
            try { resetFuture.get(300, TimeUnit.MILLISECONDS); }
            catch (TimeoutException expectedWhileLoginHoldsRowLock) { }
            finally { releaseLogin.countDown(); }
            var old = loginFuture.get(10, TimeUnit.SECONDS);
            resetFuture.get(10, TimeUnit.SECONDS);
            assertThat(encoder.matches("ConcurrentReset@2026", users.findById(subject.getId()).orElseThrow().getPasswordHash())).isTrue();
            rejectedAccess(old); rejectedRefresh(old);
            login(subject.getUsername(), "ConcurrentReset@2026");
        } finally { releaseLogin.countDown(); pool.shutdownNow(); reset(encoder); }
    }

    @Test
    void legacyRefreshCannotEscapeLogoutByUpgradingToAnotherSession() throws Exception {
        String legacy = tokens.generateRefreshToken(subject.getId());
        String body = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", legacy))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var refreshed = mapper.readTree(body).path("data");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(refreshed, "accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", legacy))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1100));
        rejectedAccess(refreshed); rejectedRefresh(refreshed);
    }

    @Test
    void refreshingKeepsTheSameRevocationBoundary() throws Exception {
        var initial = login(subject.getUsername(), TEST_PASSWORD);
        String body = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", initial.path("refreshToken").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var refreshed = mapper.readTree(body).path("data");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(refreshed, "accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        rejectedAccess(initial); rejectedRefresh(initial); rejectedAccess(refreshed); rejectedRefresh(refreshed);
    }

    @Test
    void passwordChangeCannotIssueNewSessionAfterConcurrentLogoutRevokedIt() throws Exception {
        var session = login(subject.getUsername(), TEST_PASSWORD);
        var principal = new com.gym.fitness.security.UserDetailsImpl();
        principal.setId(subject.getId());
        principal.setSessionVersion(tokens.getSessionVersionFromToken(session.path("accessToken").asText()));
        principal.setSessionId(tokens.getSessionIdFromToken(session.path("accessToken").asText()));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(session, "accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        // Model a request that already passed the JWT filter before logout got
        // the row lock. Exercise the real transactional service and database.
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            var request = new com.gym.fitness.module.auth.dto.ChangePasswordRequest();
            request.setCurrentPassword(TEST_PASSWORD); request.setNewPassword("RevivedSession@2026");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.changePassword(request))
                    .isInstanceOf(com.gym.fitness.common.exception.BusinessException.class)
                    .satisfies(ex -> assertThat(((com.gym.fitness.common.exception.BusinessException) ex).getErrorCode().getCode()).isEqualTo(1100));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        assertThat(encoder.matches(TEST_PASSWORD, users.findById(subject.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void logoutRejectsMixedAccountsOrLoginIdsWithoutRevokingEither() throws Exception {
        var own = login(subject.getUsername(), TEST_PASSWORD);
        for (var other : new JsonNode[]{login("admin", "password"), login(subject.getUsername(), TEST_PASSWORD)}) {
            mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(own, "accessToken"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("refreshToken", other.path("refreshToken").asText()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1100));
            for (var usable : new JsonNode[]{own, other}) {
                mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(usable, "accessToken")))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            }
        }
    }

    @Test
    void operationsCleanupRemovesOnlyExpiredRevocationTombstones() {
        var expired = new com.gym.fitness.module.auth.entity.RevokedAuthSessionEntity();
        expired.setSessionId(UUID.randomUUID().toString()); expired.setUserId(subject.getId());
        expired.setRevokedAt(java.time.Instant.now().minusSeconds(20)); expired.setExpiresAt(java.time.Instant.now().minusSeconds(10));
        var live = new com.gym.fitness.module.auth.entity.RevokedAuthSessionEntity();
        live.setSessionId(UUID.randomUUID().toString()); live.setUserId(subject.getId());
        live.setRevokedAt(java.time.Instant.now()); live.setExpiresAt(java.time.Instant.now().plusSeconds(100));
        revokedSessions.saveAndFlush(expired); revokedSessions.saveAndFlush(live);
        operations.cleanupExpiredGrants();
        assertThat(revokedSessions.existsById(expired.getSessionId())).isFalse();
        assertThat(revokedSessions.existsById(live.getSessionId())).isTrue();
    }

    private void updateEmployee(String role, Long venue) throws Exception {
        mvc.perform(put("/api/v1/admin/accounts/employees/{id}", subject.getId())
                        .header("Authorization", bearer(login("admin", "password"), "accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("roleCode", role, "venueId", venue, "realName", "隔离会话测试"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    private void rejectedAccess(JsonNode session) throws Exception {
        mvc.perform(get("/api/v1/admin/reports/dashboard").header("Authorization", bearer(session, "accessToken")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1100));
    }

    private void rejectedRefresh(JsonNode session) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", session.path("refreshToken").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1100));
    }

    private JsonNode login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body).path("data");
    }

    private String bearer(JsonNode session, String field) { return "Bearer " + session.path(field).asText(); }
}
