package com.gym.fitness.module.agent;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component @RequiredArgsConstructor @Transactional(readOnly=true)
public class AgentIdentity {
 private final SysUserRepository users;
 private final com.gym.fitness.module.auth.repository.RevokedAuthSessionRepository revokedSessions;
 private final jakarta.persistence.EntityManager em;
 private final HttpServletRequest request;
 public SysUserEntity actor(){
  var p=SecurityUtil.getCurrentUser();
  if(p==null)throw denied();
  var a=users.findById(p.getId()).filter(u->"ACTIVE".equals(u.getStatus())).orElseThrow(AgentIdentity::denied);
  em.refresh(a);
  if(!"ACTIVE".equals(a.getStatus())||a.getRole()==null||!Objects.equals(a.getRole().getCode(),p.getRole())||(!"SUPER_ADMIN".equals(p.getRole())&&!Objects.equals(a.getVenueId(),p.getOrganizationId()))||a.currentSessionVersion()!=p.getSessionVersion()||(p.getSessionId()!=null&&revokedSessions.existsById(p.getSessionId())))throw denied();
  return a;
 }
 public String role(){return actor().getRole().getCode();}
 public Long contextVenue(){actor();return SecurityUtil.getCurrentOrganizationId();}
 public String session(){
  var p=SecurityUtil.getCurrentUser();if(p==null)throw denied();
  String session=p.getSessionId();
  if(session==null){session=request.getHeader("Authorization");if(session==null||session.isBlank())throw denied();}
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((p.getId()+":"+p.getSessionVersion()+":"+session).getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static BusinessException denied(){return new BusinessException(ErrorCode.FORBIDDEN,"此角色、门店或登录会话无权使用该操作");}
 public static BusinessException invalid(String s){return new BusinessException(ErrorCode.PARAM_ERROR,s);}
}
