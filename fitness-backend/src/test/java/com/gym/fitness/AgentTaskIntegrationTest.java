package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.organization.repository.*;
import com.gym.fitness.module.course.entity.CourseEntity;
import com.gym.fitness.module.course.repository.CourseRepository;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:agent_v2;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","app.ai.enabled=false","fitness.operations.enabled=false"})
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgentTaskIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired SysUserRepository users;@Autowired RoleRepository roles;@Autowired VenueRepository venues;@Autowired CourseRepository courses;@Autowired JwtTokenProvider jwt;@Autowired DataSource ds;
    @BeforeAll void ledgerSchema(){new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__agent_task_ledger.sql"),new ClassPathResource("db/migration/V25__agent_mutation_receipts.sql")).execute(ds);}
    SysUserEntity account(String role){var u=new SysUserEntity();u.setUsername("v2-"+UUID.randomUUID());u.setRealName("隔离任务账号");u.setPasswordHash("unused-isolated");var r=roles.findByCode(role).orElseThrow();u.setRoleId(r.getId());u.setRole(r);var v=venues.findAll().get(0);u.setVenueId(v.getId());u.setVenue(v);u.setStatus("ACTIVE");return users.saveAndFlush(u);}
    String token(SysUserEntity u,String session){return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion(),UUID.nameUUIDFromBytes((u.getId()+session).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());}
    JsonNode postTask(SysUserEntity u,String path,Object body,int code)throws Exception{return json.readTree(mvc.perform(post("/api/v1/agent/tasks"+path).header("Authorization",token(u,"v2-session")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
    CourseEntity course(SysUserEntity u){var c=new CourseEntity();c.setName("V2隔离课程");c.setVenueId(u.getVenueId());c.setType("GROUP");c.setDurationMinutes(60);c.setMaxCapacity(20);c.setFee(new BigDecimal("88"));c.setIntensity("LOW");return courses.saveAndFlush(c);}
    Map<String,Object> courseBody(CourseEntity c){return Map.of("name",c.getName(),"venueId",c.getVenueId(),"type","GROUP","durationMinutes",60,"maxCapacity",20,"fee",99,"intensity","LOW");}
    @Test void actualCourseControllerWriteAndReceiptAreAtomicAndSingleUse()throws Exception{
        var u=account("SUPER_ADMIN");var c=course(u);String path="/api/v1/admin/courses/"+c.getId();var body=courseBody(c);
        String task=postTask(u,"",Map.of("document","document-v2","goal","把V2隔离课程价格改为99"),0).path("id").asText();
        String receipt=postTask(u,"/"+task+"/prepare",Map.of("version",0,"document","document-v2","method","PUT","path",path,"body",body),0).path("id").asText();
        mvc.perform(put(path).header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",receipt).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        assertThat(courses.findById(c.getId()).orElseThrow().getFee()).isEqualByComparingTo("88");
        postTask(u,"/"+task+"/approve/"+receipt,Map.of("version",0,"document","document-v2"),0);
        mvc.perform(put(path).header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",receipt).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(header().string("X-FitChain-Agent-Receipt",receipt));
        assertThat(courses.findById(c.getId()).orElseThrow().getFee()).isEqualByComparingTo("99");
        mvc.perform(put(path).header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",receipt).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        var receipts=json.readTree(mvc.perform(get("/api/v1/agent/tasks/"+task+"/receipts").header("Authorization",token(u,"v2-session"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");assertThat(receipts.get(0).path("status").asText()).isEqualTo("SUCCEEDED");
    }
    @Test void changedCourseBeforeConfirmationCannotBeOverwritten()throws Exception{
        var u=account("SUPER_ADMIN");var c=course(u);String path="/api/v1/admin/courses/"+c.getId();var body=courseBody(c);
        String task=postTask(u,"",Map.of("document","document-v2","goal","把V2隔离课程价格改为99"),0).path("id").asText();
        String receipt=postTask(u,"/"+task+"/prepare",Map.of("version",0,"document","document-v2","method","PUT","path",path,"body",body),0).path("id").asText();
        postTask(u,"/"+task+"/approve/"+receipt,Map.of("version",0,"document","document-v2"),0);
        c.setFee(new BigDecimal("109"));courses.saveAndFlush(c);
        mvc.perform(put(path).header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",receipt).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());assertThat(courses.findById(c.getId()).orElseThrow().getFee()).isEqualByComparingTo("109");
    }
    @Test void sixRolesHaveTaskOwnershipAndCannotReadAnotherLoginSession()throws Exception{
        for(String role:List.of("SUPER_ADMIN","MANAGER","RECEPTION","COACH","SALES","MEMBER")){
            var u=account(role);String id=postTask(u,"",Map.of("document","document-v2","goal","查看本人可见数据"),0).path("id").asText();
            mvc.perform(get("/api/v1/agent/tasks/"+id).header("Authorization",token(u,"another-session"))).andExpect(jsonPath("$.code").value(1003));
            postTask(u,"/"+id+"/cancel",Map.of("document","document-v2","version",0),0);
        }
    }
}
