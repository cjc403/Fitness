package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import static com.gym.fitness.module.agent.AgentIdentity.*;

/** Bounded, ephemeral query conditions only. Client history is never a business receipt. */
@Component
public class AgentChatContext {
 public record Owner(Long userId,String role,Long venueId,String session) {}
 public record Query(String tool,JsonNode arguments) {}
 public record Context(String id,Owner owner,List<Query> queries,String pendingTool,Instant expiresAt) {}
 private final Map<String,Context> entries=new LinkedHashMap<>();
 private final Clock clock;
 public AgentChatContext(){this(Clock.systemUTC());}
 AgentChatContext(Clock clock){this.clock=clock;}
 public synchronized Context open(String id,Owner owner){
  entries.values().removeIf(c->!c.expiresAt().isAfter(clock.instant()));
  if(id!=null&&!id.isBlank()){
   if(!id.matches("[a-f0-9-]{36}"))throw invalid("对话上下文格式不正确，请重新打开助手");
   Context old=entries.get(id);
   if(old!=null){if(!old.owner().equals(owner))throw denied();return copy(old);}
  }
  return new Context(UUID.randomUUID().toString(),owner,List.of(),null,clock.instant().plusSeconds(900));
 }
 public synchronized Context remember(Context context,String tool,JsonNode args,String pending){
  var queries=new ArrayList<>(context.queries());
  if(tool!=null&&args!=null&&args.toString().length()<=4000){
   queries.removeIf(q->q.tool().equals(tool));queries.add(new Query(tool,args.deepCopy()));
   while(queries.size()>4)queries.remove(0);
  }
  var saved=new Context(context.id(),context.owner(),List.copyOf(queries),pending,clock.instant().plusSeconds(900));
  entries.values().removeIf(c->!c.expiresAt().isAfter(clock.instant()));
  if(entries.size()>=1000&&!entries.containsKey(saved.id()))entries.remove(entries.keySet().iterator().next());
  entries.put(saved.id(),copy(saved));return saved;
 }
 public Optional<Query> last(Context context,String tool){return context.queries().stream().filter(q->tool.equals(q.tool())).findFirst();}
 private Context copy(Context c){return new Context(c.id(),c.owner(),c.queries().stream().map(q->new Query(q.tool(),q.arguments().deepCopy())).toList(),c.pendingTool(),c.expiresAt());}
}
