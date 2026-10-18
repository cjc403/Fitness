package com.gym.fitness.module.agent;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AgentModelContextTest {
 @Test void exactRequestedNameCanGroundTheReturnedIdWithoutDisclosingOtherNamesOrPrivateFields()throws Exception{
  var json=new ObjectMapper().findAndRegisterModules();var business=mock(AgentBusiness.class,CALLS_REAL_METHODS);ReflectionTestUtils.setField(business,"json",json);
  var result=new AgentDtos.ToolResult("members","会员",json.valueToTree(Map.of("items",List.of(Map.of("id",1,"realName","林晓雨","phone","13912345678"),Map.of("id",2,"realName","林晓雨同名后缀","note","私有备注")))),"QA",LocalDateTime.now(),false);
  var method=AgentBusiness.class.getMethod("modelContext",AgentDtos.ToolResult.class,JsonNode.class);String visible=json.writeValueAsString(method.invoke(business,result,json.valueToTree(Map.of("keyword","林晓雨"))));
  assertTrue(visible.contains("林晓雨"));assertFalse(visible.contains("同名后缀"));assertFalse(visible.contains("13912345678"));assertFalse(visible.contains("私有备注"));assertFalse(json.writeValueAsString(business.modelContext(result)).contains("林晓雨"));
 }
}
