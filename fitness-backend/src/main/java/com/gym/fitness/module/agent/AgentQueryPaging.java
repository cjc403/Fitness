package com.gym.fitness.module.agent;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

/** Paging occurs after the original service has enforced ownership and venue scope. */
public final class AgentQueryPaging {
 private AgentQueryPaging(){}
 public record Slice(JsonNode data,AgentDtos.Pagination pagination){}
 public static Slice slice(String tool,JsonNode source,int page){
  if(page<1||page>10000)throw invalid("查询页码超出范围");
  JsonNode data=source.deepCopy(),records;
  int size=20;long total;
  if(data.isArray()){
   total=data.size();records=part(data,page,size);data=records;
  }else{
   String key=switch(tool){case "training_plans"->"plans";case "leads"->"leads";case "cash_summary"->"shifts";case "retention"->"members";case "lead_options"->"advisors";default->"";};
   var container=key.isEmpty()?data:data.path(key);
   if(container.isArray()){
    total=container.size();records=part(container,page,size);((ObjectNode)data).set(key,records);
   }else if(container.path("records").isArray()&&container.path("total").canConvertToLong()){
    records=container.path("records");total=container.path("total").asLong();page=container.path("page").asInt(page);size=container.path("size").asInt(20);
    if(size<1||size>20||records.size()>size||total<records.size())throw invalid("业务查询分页信息不完整");
   }else return new Slice(data,null);
  }
  var keys=new ArrayList<String>();for(var row:records){String id=null;for(var key:List.of("id","scheduleId","memberId","userId"))if(row.hasNonNull(key)){id=key+":"+row.get(key).asText();break;}keys.add(id==null?row.toString():id);}
  return new Slice(data,new AgentDtos.Pagination(page,size,total,records.size(),List.copyOf(keys),(long)page*size<total?page+1:null,false));
 }
 private static ArrayNode part(JsonNode source,int page,int size){var out=JsonNodeFactory.instance.arrayNode();int from=Math.min(source.size(),(page-1)*size),to=Math.min(source.size(),from+size);for(int i=from;i<to;i++)out.add(source.get(i));return out;}
}
