package com.gym.fitness.module.cashshift.service;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.constant.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Component @RequiredArgsConstructor
public class CashShiftScope {
 private final EntityManager em;
 public Long actor(){return SecurityUtil.getCurrentUserId();}
 public boolean manager(){return Set.of("MANAGER","SUPER_ADMIN").contains(SecurityUtil.getCurrentUserRole());}
 public void require(String authority){
  var u=SecurityUtil.getCurrentUser();
  if(u==null||!Set.of("RECEPTION","MANAGER","SUPER_ADMIN").contains(u.getRole())||("CASH_SHIFT_REVIEW".equals(authority)&&!manager())||u.getAuthorities().stream().noneMatch(a->authority.equals(a.getAuthority())))throw forbidden();
 }
 public Long venue(Long requested){require("CASH_SHIFT_READ");if(SecurityUtil.isSuperAdmin())return requested;Long own=SecurityUtil.getCurrentOrganizationId();if(requested!=null&&!requested.equals(own))throw forbidden();return own;}
 public void access(Long venue){venue(venue);}
 @Transactional(propagation=Propagation.MANDATORY)
 public void lockActor(String authority){
  require(authority);
  boolean pg=em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect() instanceof PostgreSQLDialect;
  var rows=em.createNativeQuery("select role_id,venue_id,status,session_version from sys_user where id=:id "+(pg?"FOR NO KEY UPDATE":"FOR UPDATE")).setParameter("id",actor()).getResultList();
  if(rows.isEmpty())throw forbidden();var r=(Object[])rows.get(0);
  String role=(String)em.createNativeQuery("select code from role where id=:id").setParameter("id",r[0]).getSingleResult();
  var u=SecurityUtil.getCurrentUser();if(!Objects.equals(role,u.getRole())||(!"SUPER_ADMIN".equals(role)&&((Number)r[1]).longValue()!=u.getOrganizationId())||!"ACTIVE".equals(r[2])||(r[3]!=null?((Number)r[3]).longValue():0)!=u.getSessionVersion())throw forbidden();
 }
 public static BusinessException forbidden(){return new BusinessException(ErrorCode.FORBIDDEN,"无权操作此班次");}
}
