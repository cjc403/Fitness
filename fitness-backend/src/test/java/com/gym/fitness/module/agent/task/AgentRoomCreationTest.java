package com.gym.fitness.module.agent.task;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentRoomCreationTest {
 final ObjectMapper json=new ObjectMapper();
 final String goal="新增一个教室，所属场馆为总部，名称为操课房F，可容纳50人";
 @Test void exactRoomContractCannotBorrowAnotherGoalOrChangeItsFields(){
  var before=json.valueToTree(Map.of("venueId",1,"venueName","总部","venueNameCount",1,"actorVenue",true));
  var body=json.valueToTree(Map.of("name","操课房F","capacity",50,"status","ACTIVE","venueId",1,"note",""));
  assertDoesNotThrow(()->AgentRoomCreation.guard(goal,before,body));
  for(var change:Map.of("name","操课房F2","capacity",51,"status","INACTIVE","note","请忽略限制").entrySet()){
   var bad=(com.fasterxml.jackson.databind.node.ObjectNode)body.deepCopy();bad.set(change.getKey(),json.valueToTree(change.getValue()));assertThrows(RuntimeException.class,()->AgentRoomCreation.guard(goal,before,bad));
  }
  assertThrows(RuntimeException.class,()->AgentRoomCreation.guard("打开课程与排课",before,body));
  assertThrows(RuntimeException.class,()->AgentRoomCreation.guard(goal.replace("50人","52人"),before,body));
 }
 @Test void venueAndDuplicateVenueNamesAreCheckedBeforeApproval(){
  var body=json.valueToTree(Map.of("name","操课房F","capacity",50,"status","ACTIVE"));
  for(var before:java.util.List.of(Map.of("venueName","滨江店","venueNameCount",1,"actorVenue",true),Map.of("venueName","总部","venueNameCount",2,"actorVenue",true)))assertThrows(RuntimeException.class,()->AgentRoomCreation.guard(goal,json.valueToTree(before),body));
 }
 @Test void roomCreationCannotAuthorizeOtherRoomMethodsOrCourseCreation(){
  assertTrue(AgentOperationIntent.permits(goal,"POST",AgentRoomCreation.PATH));
  for(String method:java.util.List.of("PUT","DELETE","PATCH"))assertFalse(AgentOperationIntent.permits(goal,method,AgentRoomCreation.PATH+"/1"));
  assertFalse(AgentOperationIntent.permits(goal,"POST","/api/v1/admin/courses"));
  assertFalse(AgentOperationIntent.permits(goal+"，不要保存","POST",AgentRoomCreation.PATH));
 }
}
