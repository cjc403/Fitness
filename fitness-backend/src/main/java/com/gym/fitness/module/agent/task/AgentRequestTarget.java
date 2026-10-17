package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

/** A query is part of the frozen request hash, never a route or a model instruction. */
final class AgentRequestTarget {
 private AgentRequestTarget(){}
 record Target(String path,JsonNode query){}
 static Target parse(String method,String target){
  try{
   if(target==null||target.length()>2400||!target.startsWith("/api/v1/")||target.contains("#"))throw invalid("业务请求目标不正确");
   URI uri=URI.create(target);String path=uri.getRawPath();
   if(uri.isAbsolute()||uri.getRawAuthority()!=null||path==null||path.length()>500||path.contains("%")||path.contains(".."))throw invalid("业务请求目标不正确");
   var query=JsonNodeFactory.instance.objectNode();String raw=uri.getRawQuery();
   if(raw!=null&&!raw.isEmpty()){
    if(!"PUT".equals(method)||!path.matches("/api/v1/admin/bookings/\\d+/cancel"))throw invalid("此业务操作不接受查询参数");
    String[] pairs=raw.split("&",-1);if(pairs.length!=1)throw invalid("业务查询参数重复或未注册");
    String[] pair=pairs[0].split("=",2);String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8),value=pair.length==2?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):"";
    if(!"reason".equals(key)||value.length()>200)throw invalid("只支持200字以内的取消原因");query.put("reason",value);
   }
   return new Target(path,query);
  }catch(IllegalArgumentException malformed){throw invalid("业务请求目标不正确");}
 }
}
