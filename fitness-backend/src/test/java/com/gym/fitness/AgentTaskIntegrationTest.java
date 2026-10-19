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
    @Autowired com.gym.fitness.module.lead.repository.SalesLeadRepository leadRows;
    @Autowired com.gym.fitness.module.commercial.repository.MembershipProductRepository productRows;
    @Autowired com.gym.fitness.module.course.repository.RoomRepository roomRows;
    @Test void roomCreationUsesFrozenApprovalAndCurrentRowEvidenceForTheExactUserGoal()throws Exception{
        for(String role:List.of("SUPER_ADMIN","MANAGER")){
            var u=account(role);String name="操课房F-"+UUID.randomUUID().toString().substring(0,8);String venue=venues.findById(u.getVenueId()).orElseThrow().getName();
            var body=Map.<String,Object>of("venueId",u.getVenueId(),"name",name,"capacity",50,"status","ACTIVE","note","");
            long count=roomRows.count();String doc="room-document";
            String task=postTask(u,"",Map.of("document",doc,"goal","点开课程与排课，帮我新增一个教室，所属场馆为"+venue+"，名称为"+name+"，可容纳50人"),0).path("id").asText();
            assertThat(postTask(u,"/"+task+"/verify",Map.of("version",0,"document",doc,"context",Map.of("path","/courses")),0).path("complete").asBoolean()).isFalse();
            var receipts=json.readTree(mvc.perform(get("/api/v1/agent/tasks/"+task).header("Authorization",token(u,"v2-session"))).andReturn().getResponse().getContentAsString()).path("data");long version=receipts.path("version").asLong();
            String permit=postTask(u,"/"+task+"/prepare",Map.of("version",version,"document",doc,"method","POST","path","/api/v1/admin/rooms","body",body),0).path("id").asText();
            assertThat(roomRows.count()).isEqualTo(count);
            postTask(u,"/"+task+"/approve/"+permit,Map.of("version",version,"document",doc),0);
            var response=json.readTree(mvc.perform(post("/api/v1/admin/rooms").header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",permit).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString());
            assertThat(roomRows.count()).isEqualTo(count+1);long roomId=response.path("data").path("id").asLong();assertThat(roomRows.findById(roomId).orElseThrow().getCapacity()).isEqualTo(50);
            postTask(u,"/"+task+"/prepare",Map.of("version",version,"document",doc,"method","POST","path","/api/v1/admin/rooms","body",body),1001);
            mvc.perform(post("/api/v1/admin/rooms").header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",permit).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
            assertThat(postTask(u,"/"+task+"/verify",Map.of("version",version,"document",doc,"context",Map.of("path","/courses")),0).path("complete").asBoolean()).isTrue();
        }
    }
    @Test void roomPreviewRejectsWrongFieldsAndAllFourUnauthorizedRoles()throws Exception{
        for(String role:List.of("SUPER_ADMIN","MANAGER","RECEPTION","COACH","SALES","MEMBER")){
            var u=account(role);String name="教室保护"+UUID.randomUUID().toString().substring(0,8),doc="room-guard";
            String task=postTask(u,"",Map.of("document",doc,"goal","新增一个教室，名称为"+name+"，可容纳50人"),0).path("id").asText();
            var body=new LinkedHashMap<String,Object>(Map.of("name",name,"capacity",50,"venueId",u.getVenueId(),"status","ACTIVE","note",""));
            if(List.of("SUPER_ADMIN","MANAGER").contains(role)){
                for(var wrong:List.of(Map.entry("name","错误教室"),Map.entry("capacity",51),Map.entry("status","INACTIVE"),Map.entry("note","未要求的备注"))){var bad=new LinkedHashMap<>(body);bad.put(wrong.getKey(),wrong.getValue());postTask(u,"/"+task+"/prepare",Map.of("version",0,"document",doc,"method","POST","path","/api/v1/admin/rooms","body",bad),1001);}
            }else postTask(u,"/"+task+"/prepare",Map.of("version",0,"document",doc,"method","POST","path","/api/v1/admin/rooms","body",body),1003);
        }
    }
    @Test void productPriceUsesActualControllerAndVerifierWithoutChangingExistingCommercialData()throws Exception{
        var u=account("MANAGER");var p=new com.gym.fitness.module.commercial.entity.MembershipProductEntity();
        p.setName("隔离季卡"+UUID.randomUUID().toString().substring(0,8));p.setCategory("CARD");p.setProductType("QUARTER_CARD");p.setDescription("保留商品说明");p.setVenueId(u.getVenueId());p.setValidityDays(90);p.setPrice(new BigDecimal("1299"));p.setStatus("ACTIVE");p=productRows.saveAndFlush(p);
        var body=new LinkedHashMap<String,Object>();body.put("id",p.getId());body.put("name",p.getName());body.put("category","CARD");body.put("productType","QUARTER_CARD");body.put("description",p.getDescription());body.put("validityDays",90);body.put("totalUses",null);body.put("accessAllowed",true);body.put("venueId",u.getVenueId());body.put("status","ACTIVE");body.put("price",998);
        String path="/api/v1/commercial/products/"+p.getId();
        String task=postTask(u,"",Map.of("document","product-document","goal","点开 会籍与商业，并把"+p.getName()+"的价格改为998"),0).path("id").asText();
        String receipt=postTask(u,"/"+task+"/prepare",Map.of("version",0,"document","product-document","method","PUT","path",path,"body",body),0).path("id").asText();
        assertThat(productRows.findById(p.getId()).orElseThrow().getPrice()).isEqualByComparingTo("1299");
        postTask(u,"/"+task+"/approve/"+receipt,Map.of("version",0,"document","product-document"),0);
        mvc.perform(put(path).header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",receipt).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(header().string("X-FitChain-Agent-Receipt",receipt));
        assertThat(productRows.findById(p.getId()).orElseThrow().getPrice()).isEqualByComparingTo("998");
        assertThat(productRows.findById(p.getId()).orElseThrow().getValidityDays()).isEqualTo(90);
        assertThat(postTask(u,"/"+task+"/verify",Map.of("version",0,"document","product-document","context",Map.of("path","/commercial")),0).path("complete").asBoolean()).isTrue();
        mvc.perform(put(path).header("Authorization",token(u,"v2-session")).header("X-FitChain-Agent-Permit",receipt).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
    }
    @BeforeAll void ledgerSchema(){new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__agent_task_ledger.sql"),new ClassPathResource("db/migration/V25__agent_mutation_receipts.sql")).execute(ds);}
    SysUserEntity account(String role){var u=new SysUserEntity();u.setUsername("v2-"+UUID.randomUUID());u.setRealName("隔离任务账号");u.setPasswordHash("unused-isolated");var r=roles.findByCode(role).orElseThrow();u.setRoleId(r.getId());u.setRole(r);var v=venues.findAll().get(0);u.setVenueId(v.getId());u.setVenue(v);u.setStatus("ACTIVE");return users.saveAndFlush(u);}
    String token(SysUserEntity u,String session){return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion(),UUID.nameUUIDFromBytes((u.getId()+session).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());}
    JsonNode postTask(SysUserEntity u,String path,Object body,int code)throws Exception{return json.readTree(mvc.perform(post("/api/v1/agent/tasks"+path).header("Authorization",token(u,"v2-session")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
    CourseEntity course(SysUserEntity u){var c=new CourseEntity();c.setName("V2隔离课程");c.setVenueId(u.getVenueId());c.setType("GROUP");c.setDurationMinutes(60);c.setMaxCapacity(20);c.setFee(new BigDecimal("88"));c.setIntensity("LOW");return courses.saveAndFlush(c);}
    Map<String,Object> courseBody(CourseEntity c){return Map.of("name",c.getName(),"venueId",c.getVenueId(),"type","GROUP","durationMinutes",60,"maxCapacity",20,"fee",99,"intensity","LOW");}
    @Test void headquartersCannotPrepareAFollowUpForTheWrongSameNamedVenueLead()throws Exception{
        var u=account("SUPER_ADMIN");var first=venues.findById(u.getVenueId()).orElseThrow();var otherVenue=new com.gym.fitness.module.organization.entity.VenueEntity();otherVenue.setName("隔离异店"+UUID.randomUUID().toString().substring(0,6));otherVenue.setCode(UUID.randomUUID().toString());otherVenue.setStatus("ACTIVE");otherVenue=venues.saveAndFlush(otherVenue);
        String name="同名潜客"+UUID.randomUUID().toString().substring(0,8);long wrong=0;
        for(var venue:List.of(first,otherVenue)){var owner=account("SALES");owner.setVenueId(venue.getId());owner.setVenue(venue);users.saveAndFlush(owner);var lead=new com.gym.fitness.module.lead.entity.SalesLeadEntity();lead.setName(name);lead.setPhone("13900000999");lead.setSource("WALK_IN");lead.setIntent("LOW");lead.setStage("CONSULTING");lead.setOwnerId(owner.getId());lead.setVenueId(venue.getId());lead.setCreatedAt(java.time.LocalDateTime.now());lead.setUpdatedAt(java.time.LocalDateTime.now());wrong=leadRows.saveAndFlush(lead).getId();}
        String id=postTask(u,"",Map.of("document","same-name-doc","goal","为"+first.getName()+"潜客"+name+"登记电话跟进，内容隔离检查，结果已联系"),0).path("id").asText();
        postTask(u,"/"+id+"/prepare",Map.of("version",0,"document","same-name-doc","method","POST","path","/api/v1/leads/"+wrong+"/follow-ups","body",Map.of("expectedVersion",0,"method","CALL","content","隔离检查","outcome","CONTACTED","idempotencyKey","guard-"+UUID.randomUUID())),1001);
        postTask(u,"/"+id+"/revise",Map.of("version",0,"document","same-name-doc","amendment","改为任务：为潜客编号#"+wrong+"登记电话跟进，内容隔离检查，结果已联系"),0);
        postTask(u,"/"+id+"/prepare",Map.of("version",1,"document","same-name-doc","method","POST","path","/api/v1/leads/"+wrong+"/follow-ups","body",Map.of("expectedVersion",0,"method","CALL","content","隔离检查","outcome","CONTACTED","idempotencyKey","right-"+UUID.randomUUID())),0);
    }
    @Test void reloginReadsOwnHistoryWithoutAcquiringTheOldExecutionLease()throws Exception{
        var u=account("SUPER_ADMIN");String id=postTask(u,"",Map.of("document","history-doc","goal","打开训练计划"),0).path("id").asText();
        mvc.perform(get("/api/v1/agent/tasks/history/"+id).header("Authorization",token(u,"new-login"))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.readOnly").value(true)).andExpect(jsonPath("$.data.task.id").value(id));
        mvc.perform(post("/api/v1/agent/tasks/"+id+"/recover").header("Authorization",token(u,"new-login")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("version",0,"document","history-new")))).andExpect(jsonPath("$.code").value(1003));
        var other=account("SUPER_ADMIN");mvc.perform(get("/api/v1/agent/tasks/history/"+id).header("Authorization",token(other,"new-login"))).andExpect(jsonPath("$.code").value(1003));
    }
    @Test void actualCourseControllerWriteAndReceiptAreAtomicAndSingleUse()throws Exception{
        var u=account("SUPER_ADMIN");var c=course(u);String path="/api/v1/admin/courses/"+c.getId();var body=courseBody(c);
        String task=postTask(u,"",Map.of("document","document-v2","goal","把课程编号#"+c.getId()+"价格改为99"),0).path("id").asText();
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
        String task=postTask(u,"",Map.of("document","document-v2","goal","把课程编号#"+c.getId()+"价格改为99"),0).path("id").asText();
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
    @Test void passwordChangeRequiredCannotEnterTheAgentTaskOrApprovalApi()throws Exception{
        var u=account("MEMBER");u.setMustChangePassword(true);users.saveAndFlush(u);
        mvc.perform(post("/api/v1/agent/tasks").header("Authorization",token(u,"v2-session")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("document","document-v2","goal","查询我的预约"))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(com.gym.fitness.common.constant.ErrorCode.PASSWORD_CHANGE_REQUIRED.getCode()));
        mvc.perform(get("/api/v1/agent/tasks").header("Authorization",token(u,"v2-session"))).andExpect(status().isForbidden());
    }
    @Test void roleRevocationAfterPreviewRejectsTheOldAgentApprovalToken()throws Exception{
        var u=account("SUPER_ADMIN");var c=course(u);String oldToken=token(u,"v2-session"),path="/api/v1/admin/courses/"+c.getId();
        String task=postTask(u,"",Map.of("document","document-v2","goal","把课程编号#"+c.getId()+"价格改为99"),0).path("id").asText();
        String receipt=postTask(u,"/"+task+"/prepare",Map.of("version",0,"document","document-v2","method","PUT","path",path,"body",courseBody(c)),0).path("id").asText();
        var downgraded=roles.findByCode("COACH").orElseThrow();u.setRoleId(downgraded.getId());u.setRole(downgraded);users.saveAndFlush(u);
        mvc.perform(post("/api/v1/agent/tasks/"+task+"/approve/"+receipt).header("Authorization",oldToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("version",0,"document","document-v2")))).andExpect(status().isUnauthorized());
        assertThat(courses.findById(c.getId()).orElseThrow().getFee()).isEqualByComparingTo("88");
    }
}
