package com.gym.fitness.module.agent;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AgentQueryPagingTest {
 final ObjectMapper json=new ObjectMapper();
 @Test void arrayQueriesExposeEveryPageWithoutChangingTheLegacyArrayShape(){
  var source=json.valueToTree(java.util.stream.IntStream.rangeClosed(1,45).mapToObj(i->Map.of("id",i)).toList());
  var seen=new HashSet<String>();
  for(int page=1;page<=3;page++){var s=AgentQueryPaging.slice("bookings",source,page);assertTrue(s.data().isArray());assertEquals(page==3?5:20,s.data().size());assertEquals(45,s.pagination().total());assertEquals(page==3?null:Integer.valueOf(page+1),s.pagination().nextPage());seen.addAll(s.pagination().keys());}
  assertEquals(45,seen.size());assertEquals(45,source.size());
 }
 @Test void alreadyPagedBusinessResultsAreNotSlicedForASecondTime(){
  var records=java.util.stream.IntStream.rangeClosed(21,40).mapToObj(i->Map.of("id",i)).toList();
  var source=json.valueToTree(Map.of("plans",Map.of("records",records,"total",45,"page",2,"size",20)));
  var result=AgentQueryPaging.slice("training_plans",source,2);assertEquals(20,result.data().path("plans").path("records").size());assertEquals(2,result.pagination().page());assertEquals(45,result.pagination().total());
 }
}
