package com.gym.fitness;

import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.chat.entity.DirectMessageEntity;
import com.gym.fitness.module.chat.repository.DirectMessageRepository;
import com.gym.fitness.module.organization.repository.RoleRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_privacy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.ai.enabled=false", "fitness.operations.enabled=false"
})
@AutoConfigureMockMvc
class ChatPrivacyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SysUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired VenueRepository venues;
    @Autowired DirectMessageRepository messages;
    @Autowired JwtTokenProvider jwt;

    @Test
    void memberCannotEnumerateUnrelatedMemberIdentity() throws Exception {
        var me = account("MEMBER", 0); var other = account("MEMBER", 0);
        mvc.perform(get("/api/v1/messages/thread/{id}", other.getId()).header("Authorization", token(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void staffCannotEnumerateUnrelatedOtherVenueIdentity() throws Exception {
        var me = account("RECEPTION", 0); var other = account("RECEPTION", 1);
        mvc.perform(get("/api/v1/messages/thread/{id}", other.getId()).header("Authorization", token(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void legitimateContactWithoutHistoryRemainsReadable() throws Exception {
        var me = account("MEMBER", 0); var other = account("RECEPTION", 0);
        mvc.perform(get("/api/v1/messages/thread/{id}", other.getId()).header("Authorization", token(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(other.getId()));
    }

    @Test
    void historicalConversationRemainsReadableAfterCounterpartMovesAndIsDisabled() throws Exception {
        var me = account("MEMBER", 0); var other = account("RECEPTION", 1);
        other.setStatus("DISABLED"); users.saveAndFlush(other);
        var prior = new DirectMessageEntity();
        prior.setSenderUserId(other.getId()); prior.setRecipientUserId(me.getId());
        prior.setSenderName("原门店工作人员"); prior.setRecipientName(me.getRealName());
        prior.setVenueId(me.getVenueId()); prior.setContent("既有业务会话"); messages.saveAndFlush(prior);
        mvc.perform(get("/api/v1/messages/thread/{id}", other.getId()).header("Authorization", token(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.messages[0].content").value("既有业务会话"));
        mvc.perform(post("/api/v1/messages/send").header("Authorization", token(me))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":" + other.getId() + ",\"content\":\"禁止的新消息\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(2600));
    }

    private SysUserEntity account(String roleCode, int venueIndex) {
        var user = new SysUserEntity(); var role = roles.findByCode(roleCode).orElseThrow();
        var venue = venues.findAll().get(venueIndex);
        user.setUsername("chat-security-" + UUID.randomUUID().toString().substring(0, 12));
        user.setPasswordHash("test-only-unused-hash"); user.setRealName("隔离聊天用户");
        user.setRoleId(role.getId()); user.setRole(role); user.setVenueId(venue.getId()); user.setVenue(venue);
        user.setStatus("ACTIVE"); user.setMustChangePassword(false); user.setCoach(false);
        return users.saveAndFlush(user);
    }

    private String token(SysUserEntity user) {
        return "Bearer " + jwt.generateToken(user.getId(), user.getUsername(), user.getRole().getCode(), user.getVenueId(), user.currentSessionVersion());
    }
}
