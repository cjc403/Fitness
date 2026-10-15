package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static com.gym.fitness.module.agent.AgentDtos.*;
import static com.gym.fitness.module.agent.AgentIdentity.*;

@Service @RequiredArgsConstructor @Slf4j
public class AgentActions {
 private final AgentActionRepository repository;private final AgentBusiness business;private final AgentTools tools;
 private final AgentIdentity identity;private final ObjectMapper json;private final PlatformTransactionManager manager;
 private <T>T tx(Supplier<T> work){var transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);transaction.setTimeout(25);return transaction.execute(s->work.get());}
 public Action preview(String tool,JsonNode args){return tx(()->{
  var t=tools.validate(tool,args,true);var actor=identity.actor();String session=identity.session();
  if(repository.countByUserIdAndSessionHashAndStatusAndExpiresAtAfter(actor.getId(),session,"PENDING",LocalDateTime.now())>=30)throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,"待确认操作过多，请先完成或取消已有操作");
  String summary=business.preview(tool,args);var a=new AgentActionEntity();a.setId(UUID.randomUUID().toString());a.setUserId(actor.getId());a.setVenueId(identity.contextVenue());a.setRole(actor.getRole().getCode());a.setSessionHash(session);a.setTool(tool);a.setArgumentsJson(write(args));a.setBusinessSnapshot(write(business.snapshot(tool,args,false)));a.setSummary(summary);a.setStatus("PENDING");a.setCreatedAt(LocalDateTime.now());a.setExpiresAt(a.getCreatedAt().plusMinutes(10));repository.saveAndFlush(a);return view(a,t.label());
 });}
 public List<Action> list(){return tx(()->{var actor=identity.actor();Long venue=identity.contextVenue();return repository.findTop30ByUserIdAndSessionHashOrderByCreatedAtDesc(actor.getId(),identity.session()).stream().filter(a->a.getVenueId().equals(venue)&&a.getRole().equals(actor.getRole().getCode())).map(a->{expire(a);return view(a);}).toList();});}
 public Action confirm(String id){
  // Authorization failures are not business failures and must never disclose another user's action.
  tx(()->{owned(repository.findById(id).orElseThrow(AgentIdentity::denied));return null;});
  Action result;
  try {result=tx(()->{
   var a=repository.lock(id).orElseThrow(AgentIdentity::denied);owned(a);expire(a);
   if("SUCCEEDED".equals(a.getStatus())||"EXPIRED".equals(a.getStatus()))return view(a);
   if(!"PENDING".equals(a.getStatus()))throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,"该操作已经结束，请重新预览");
   var args=read(a.getArgumentsJson());
   if(!read(a.getBusinessSnapshot()).equals(read(write(business.snapshot(a.getTool(),args,true)))))throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,"业务资料已变化，请重新生成并核对操作预览");
   business.preview(a.getTool(),args); // Refresh actor/target scope and user-visible preconditions under the action lock.
   var receipt=business.execute(a.getTool(),args,"agent_"+a.getId().replace("-",""));
   a.setResultJson(write(receipt));a.setStatus("SUCCEEDED");repository.saveAndFlush(a);return view(a);
  });}
  catch(RuntimeException error){
   // The entire write transaction has rolled back before its failure audit is recorded.
   if(error instanceof BusinessException b&&b.getErrorCode()==ErrorCode.FORBIDDEN)throw b;
   result=tx(()->{var a=repository.lock(id).orElseThrow(AgentIdentity::denied);owned(a);
    if("PENDING".equals(a.getStatus())){a.setStatus("FAILED");String message=error instanceof BusinessException?error.getMessage():"业务执行未完成，变更已回滚。请在业务页面核对后重新操作。";a.setError(message.substring(0,Math.min(message.length(),500)));repository.saveAndFlush(a);log.info("Agent action {} rolled back ({})",a.getId(),error.getClass().getSimpleName());}return view(a);
   });
   if(!List.of("FAILED","SUCCEEDED").contains(result.status()))throw error;
  }
  if("EXPIRED".equals(result.status()))throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,"确认已过期，请重新生成操作预览");
  return result;
 }
 public Action cancel(String id){return tx(()->{var a=repository.lock(id).orElseThrow(AgentIdentity::denied);owned(a);expire(a);if("PENDING".equals(a.getStatus()))a.setStatus("CANCELLED");return view(a);});}
 private void owned(AgentActionEntity a){var u=identity.actor();if(!u.getId().equals(a.getUserId())||!identity.contextVenue().equals(a.getVenueId())||!u.getRole().getCode().equals(a.getRole())||!identity.session().equals(a.getSessionHash()))throw denied();tools.get(a.getTool());}
 private void expire(AgentActionEntity a){if("PENDING".equals(a.getStatus())&&!a.getExpiresAt().isAfter(LocalDateTime.now()))a.setStatus("EXPIRED");}
 private Action view(AgentActionEntity a){return view(a,tools.get(a.getTool()).label());}
 private Action view(AgentActionEntity a,String title){return new Action(a.getId(),a.getTool(),title,a.getSummary(),read(a.getArgumentsJson()),a.getStatus(),a.getCreatedAt(),a.getExpiresAt(),a.getResultJson()==null?null:read(a.getResultJson()),a.getError());}
 private String write(JsonNode n){try{return json.writeValueAsString(n);}catch(Exception e){throw new IllegalStateException("无法保存操作快照",e);}}
 private JsonNode read(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException("无法读取操作快照",e);}}
}
