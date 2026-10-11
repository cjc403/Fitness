package com.gym.fitness.module.cashshift.service;
import com.gym.fitness.module.commercial.entity.PaymentTransactionEntity;
import com.gym.fitness.module.cashshift.entity.CashShiftPaymentEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.math.*;
import java.time.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
@Component @RequiredArgsConstructor
public class CashShiftLedger {
 private final EntityManager em;
 public long max(Long venue){return ((Number)em.createQuery("select coalesce(max(p.id),0) from PaymentTransactionEntity p, SalesOrderEntity o where o.id=p.orderId and o.venueId=:v").setParameter("v",venue).getSingleResult()).longValue();}
 public long historical(Long venue,long baseline){return ((Number)em.createQuery("select count(p.id) from PaymentTransactionEntity p, SalesOrderEntity o where o.id=p.orderId and o.venueId=:v and p.id<=:b and p.status='SUCCESS' and p.transactionType in ('PAYMENT','REFUND')").setParameter("v",venue).setParameter("b",baseline).getSingleResult()).longValue();}
 private static final String CANDIDATE_FROM=" from PaymentTransactionEntity p, SalesOrderEntity o where o.id=p.orderId and o.venueId=:v and p.id>:b and p.id<=:c and p.status='SUCCESS' and p.transactionType in ('PAYMENT','REFUND')";
 private jakarta.persistence.TypedQuery<PaymentTransactionEntity> candidateQuery(Long venue,long baseline,long cutoff){return em.createQuery("select p"+CANDIDATE_FROM+" order by p.id",PaymentTransactionEntity.class).setParameter("v",venue).setParameter("b",baseline).setParameter("c",cutoff);}
 private CashShiftPaymentEntity snapshot(PaymentTransactionEntity p,long opening){var x=new CashShiftPaymentEntity();x.setPaymentTransactionId(p.getId());x.setTransactionNo(p.getTransactionNo());x.setOrderId(p.getOrderId());x.setTransactionType(p.getTransactionType());x.setChannel(channel(p.getPaymentMethod()));x.setAmountCents(p.getAmount().movePointRight(2).longValueExact());x.setOccurredAt(p.getOccurredAt());x.setOperatorId(p.getOperatorId());x.setCarriedOver(p.getId()<=opening);return x;}
 public List<CashShiftPaymentEntity> candidates(Long venue,long baseline,long opening,long cutoff){return candidateQuery(venue,baseline,cutoff).getResultList().stream().map(p->snapshot(p,opening)).toList();}
 public record CandidatePage(List<CashShiftPaymentEntity> records,long total) {}
 public CandidatePage candidatePage(Long venue,long baseline,long opening,long cutoff,int page,int size){
  long count=em.createQuery("select count(p.id)"+CANDIDATE_FROM,Long.class).setParameter("v",venue).setParameter("b",baseline).setParameter("c",cutoff).getSingleResult();long offset=(long)(page-1)*size;
  if(offset>=count)return new CandidatePage(List.of(),count);
  var rows=candidateQuery(venue,baseline,cutoff).setFirstResult(Math.toIntExact(offset)).setMaxResults(size).getResultList();return new CandidatePage(rows.stream().map(p->snapshot(p,opening)).toList(),count);
 }
 public static String channel(String c){return c==null||c.trim().isEmpty()?"UNKNOWN":c.trim();}
 public static LinkedHashMap<String,long[]> totals(List<CashShiftPaymentEntity> ps){var result=new LinkedHashMap<String,long[]>();for(String c:List.of("CASH","WECHAT","ALIPAY","CARD"))result.put(c,new long[2]);for(var p:ps){var a=result.computeIfAbsent(p.getChannel(),k->new long[2]);int i="PAYMENT".equals(p.getTransactionType())?0:1;a[i]=Math.addExact(a[i],p.getAmountCents());}return result;}
 public static String fingerprint(List<CashShiftPaymentEntity> ps){var s=new StringBuilder();for(var p:ps){for(Object v:List.of(p.getPaymentTransactionId(),p.getOrderId(),p.getTransactionType(),p.getChannel(),p.getAmountCents(),p.getOccurredAt()==null?"null":p.getOccurredAt().toString())){String t=v.toString();s.append(t.length()).append(':').append(t);}s.append(';');}return sha(s.toString());}
 public static String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
