package com.gym.fitness.module.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.*;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.finance.dto.FinanceDtos.*;
import com.gym.fitness.module.finance.entity.*;
import com.gym.fitness.module.finance.repository.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class EntitlementValueService {
    private final EntitlementValueAccountRepository accounts;
    private final EntitlementFundingRepository funding;
    private final EntitlementValueEventRepository events;
    private final EntitlementTermPeriodRepository periods;
    private final MemberEntitlementRepository entitlements;
    private final SalesOrderRepository orders;
    private final MembershipProductRepository products;
    private final EntitlementOperationRepository operations;
    private final VenueMutationLock venueLock;
    private final EntityManager em;
    private final ObjectMapper json;
    public static LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    public static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
    public static BusinessException fail(String message){return new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,message);}
    public void assertScope(Long venue){if(!SecurityUtil.isSuperAdmin()&&!Objects.equals(venue,SecurityUtil.getCurrentOrganizationId()))throw new BusinessException(ErrorCode.NOT_FOUND,"记录不存在");}
    /** Every caller must acquire venue before credit; locks refresh first-level cache snapshots. */
    public MemberEntitlementEntity lock(Long id){
        var initial=entitlements.findById(id).orElseThrow(()->fail("权益不存在"));assertScope(initial.getVenueId());venueLock.acquire(initial.getVenueId());
        var e=entitlements.findByIdForUpdate(id).orElseThrow();em.refresh(e);assertScope(e.getVenueId());return e;
    }
    public EntitlementValueAccountEntity account(MemberEntitlementEntity e){
        var found=accounts.findByEntitlementId(e.getId());
        if(found.isPresent()){var a=found.get();em.refresh(a,LockModeType.PESSIMISTIC_WRITE);return a;}
        var a=new EntitlementValueAccountEntity();a.setEntitlementId(e.getId());a.setMemberId(e.getMemberId());a.setVenueId(e.getVenueId());a.setBaselineRemainingUses(e.getRemainingUses());
        if("FROZEN".equals(e.getStatus())&&e.getFrozenAt()!=null)a.setPausedAt(e.getFrozenAt().toLocalDate());
        return accounts.saveAndFlush(a);
    }
    public String productType(MemberEntitlementEntity e){return accounts.findByEntitlementId(e.getId()).filter(a->!"UNKNOWN".equals(a.getProductType())).map(EntitlementValueAccountEntity::getProductType).orElseGet(()->products.findById(e.getProductId()).orElseThrow().getProductType());}
    public void assertMutable(MemberEntitlementEntity e){
        var a=account(e);if(a.getActiveRefundId()!=null)throw fail("权益退款办理中，请先完成或撤回申请");if("REFUNDED".equals(e.getStatus()))throw fail("权益已退款终止，不能恢复或使用");
    }
    public boolean memberHasActiveRefund(Long member){return accounts.findByMemberId(member).stream().anyMatch(a->a.getActiveRefundId()!=null);}
    public void assertBookableMember(Long member){if(memberHasActiveRefund(member))throw fail("会员有退款办理中的权益，暂不能新增预约或候补");}
    public void issue(MemberEntitlementEntity e,SalesOrderEntity order,String type){
        var a=account(e);a.setVerificationStatus("VERIFIED");a.setProductType(type);accounts.saveAndFlush(a);
        addFunding(e,order,e.getRemainingUses()==null?(int)ChronoUnit.DAYS.between(e.getValidFrom(),e.getValidUntil())+1:e.getRemainingUses(),e.getValidFrom(),"ISSUE");
    }
    public void addFunding(MemberEntitlementEntity e,SalesOrderEntity order,int units,LocalDate start,String eventType){
        var a=account(e);if(events.findByBusinessKey("ORDER:"+order.getId()).isPresent())return;
        if(units<=0)throw fail("新增服务数量必须大于零");long cents=Money.cents(order.getPaidAmount());boolean verified="VERIFIED".equals(a.getVerificationStatus());
        String type="UNKNOWN".equals(a.getProductType())?products.findById(e.getProductId()).orElseThrow().getProductType():a.getProductType();
        var f=new EntitlementFundingEntity();f.setEntitlementId(e.getId());f.setOrderId(order.getId());f.setVenueId(e.getVenueId());f.setReceivedCents(cents);f.setRemainingCents(cents);f.setProductType(type);f.setTotalUnits(units);f.setVerified(verified);funding.saveAndFlush(f);
        if(e.getRemainingUses()==null){var p=new EntitlementTermPeriodEntity();p.setEntitlementId(e.getId());p.setFundingId(f.getId());p.setTotalCents(cents);p.setTotalDays(units);p.setStartDate(start);p.setNextServiceDate(start);periods.save(p);}
        a.setBalanceCents(Money.add(a.getBalanceCents(),cents));a.setVersion(a.getVersion()+1);accounts.save(a);
        var original=event(a,eventType,"ORDER:"+order.getId(),cents,units,Map.of(order.getId(),cents));
        try{original.setDetailsJson(json.writeValueAsString(Map.of("productType",type,"totalUnits",units,"startDate",start.toString())));}catch(Exception ex){throw new IllegalStateException(ex);}events.save(original);
    }
    public BigDecimal consume(MemberEntitlementEntity e,String key,BigDecimal legacyPrice){
        assertMutable(e);var a=account(e);var prior=events.findByBusinessKey(key);if(prior.isPresent())return Money.yuan(-prior.get().getDeltaCents());
        if(!"VERIFIED".equals(a.getVerificationStatus())){a.setVersion(a.getVersion()+1);accounts.save(a);event(a,"UNVERIFIED_CONSUME",key,0,-1,Map.of());return legacyPrice;}
        long cents=Money.consume(a.getBalanceCents(),e.getRemainingUses());var shares=FundingAllocator.allocate(cents,balances(e));
        adjust(e,shares,-1);a.setBalanceCents(a.getBalanceCents()-cents);a.setVersion(a.getVersion()+1);accounts.save(a);event(a,"CONSUME",key,-cents,-1,shares);return Money.yuan(cents);
    }
    public void reverse(MemberEntitlementEntity e,String originalKey,String reversalKey){
        assertMutable(e);var a=account(e);if(events.findByBusinessKey(reversalKey).isPresent())return;
        var original=events.findByBusinessKey(originalKey);
        if(original.isEmpty()||!original.get().isVerified()){
            // Historic lesson snapshots have no exact source allocation. Reconciliation must happen after correction.
            if("VERIFIED".equals(a.getVerificationStatus()))throw fail("该历史消课缺少原始资金分摊，核对后的权益不能直接冲正，请先人工核验");
            a.setVersion(a.getVersion()+1);accounts.save(a);event(a,"UNVERIFIED_REVERSE",reversalKey,0,1,Map.of());return;
        }
        var old=original.get();Map<Long,Long> shares;try{shares=json.readValue(old.getAllocationsJson(),new TypeReference<Map<Long,Long>>(){});}catch(Exception ex){throw fail("原始资金分摊无法读取");}
        adjust(e,shares,1);a.setBalanceCents(Money.add(a.getBalanceCents(),-old.getDeltaCents()));a.setVersion(a.getVersion()+1);accounts.save(a);event(a,"REVERSE",reversalKey,-old.getDeltaCents(),1,shares);
    }
    public List<EntitlementFundingEntity> lockedFunding(MemberEntitlementEntity e){
        var rows=funding.findByEntitlementIdOrderByOrderIdAsc(e.getId());for(var f:rows)em.refresh(f,LockModeType.PESSIMISTIC_WRITE);return rows;
    }
    public SalesOrderEntity lockOrder(Long id){var o=orders.findById(id).orElseThrow(()->fail("来源订单不存在"));em.refresh(o,LockModeType.PESSIMISTIC_WRITE);return o;}
    private Map<Long,Long> balances(MemberEntitlementEntity e){var result=new TreeMap<Long,Long>();for(var f:lockedFunding(e))result.put(f.getOrderId(),f.getRemainingCents());return result;}
    private void adjust(MemberEntitlementEntity e,Map<Long,Long> shares,int sign){
        for(var f:lockedFunding(e)){long next=f.getRemainingCents()+sign*shares.getOrDefault(f.getOrderId(),0L);if(next<0||next>f.getReceivedCents())throw fail("来源资金分摊不一致");f.setRemainingCents(next);funding.save(f);}
    }
    public void refreshTime(MemberEntitlementEntity e){
        var a=account(e);if(e.getRemainingUses()!=null||a.getPausedAt()!=null||a.getActiveRefundId()!=null||!"VERIFIED".equals(a.getVerificationStatus())||"REFUNDED".equals(e.getStatus()))return;
        var byId=new HashMap<Long,EntitlementFundingEntity>();for(var f:lockedFunding(e))byId.put(f.getId(),f);
        for(var p:periods.findByEntitlementIdOrderByIdAsc(e.getId())){
            int days=(int)Math.min(p.getTotalDays()-p.getConsumedDays(),Math.max(0,ChronoUnit.DAYS.between(p.getNextServiceDate(),today())));if(days==0)continue;
            // Unknown historical accounts never reach this loop. Catch-up is bounded by complete
            // elapsed days of explicit verified periods, with one immutable event per service day.
            var f=byId.get(p.getFundingId());
            for(int i=0;i<days;i++){
                LocalDate serviceDate=p.getNextServiceDate();int consumed=p.getConsumedDays()+1;long cents=p.getTotalCents()/p.getTotalDays();if(consumed==p.getTotalDays())cents+=p.getTotalCents()%p.getTotalDays();
                if(f==null||f.getRemainingCents()<cents)throw fail("时间卡服务账不一致");f.setRemainingCents(f.getRemainingCents()-cents);funding.save(f);
                p.setConsumedDays(consumed);p.setNextServiceDate(serviceDate.plusDays(1));periods.save(p);
                a.setBalanceCents(a.getBalanceCents()-cents);a.setVersion(a.getVersion()+1);accounts.save(a);var daily=event(a,"TIME_CONSUME","TERM:"+p.getId()+":"+consumed,-cents,-1,Map.of(f.getOrderId(),cents));daily.setOccurredAt(serviceDate.atTime(23,59,59));
                try{daily.setDetailsJson(json.writeValueAsString(Map.of("serviceDate",serviceDate.toString(),"recordedAt",now().toString())));}catch(Exception ex){throw new IllegalStateException(ex);}events.save(daily);
            }
        }
    }
    public void pause(MemberEntitlementEntity e){refreshTime(e);var a=account(e);if(a.getPausedAt()==null){a.setPausedAt(today());a.setVersion(a.getVersion()+1);accounts.saveAndFlush(a);}}
    public long resume(MemberEntitlementEntity e){
        var a=account(e);if(a.getPausedAt()==null)return 0;long days=Math.max(0,ChronoUnit.DAYS.between(a.getPausedAt(),today()));
        for(var p:periods.findByEntitlementIdOrderByIdAsc(e.getId()))if(p.getConsumedDays()<p.getTotalDays()){p.setNextServiceDate(p.getNextServiceDate().plusDays(days));periods.save(p);}
        a.setPausedAt(null);a.setVersion(a.getVersion()+1);accounts.saveAndFlush(a);return days;
    }
    public void transferred(MemberEntitlementEntity e){var a=account(e);a.setMemberId(e.getMemberId());a.setTransferEvidence(null);a.setVersion(a.getVersion()+1);accounts.saveAndFlush(a);}
    public Quote verifyTransfer(Long id,TransferEvidenceRequest request){
        if(!SecurityUtil.isSuperAdmin())throw fail("仅总部可核验转让及退款受领依据");var e=lock(id);assertMutable(e);refreshTime(e);var a=account(e);
        if(e.getTransferredFromMemberId()==null)throw fail("该权益没有转让记录");if(!"VERIFIED".equals(a.getVerificationStatus()))throw fail("历史权益须先进行余额核对并保存转让依据");if(a.getVersion()!=request.version())throw fail("权益版本已变化，请重新预览");
        a.setTransferEvidence(request.transferEvidence());a.setVersion(a.getVersion()+1);accounts.saveAndFlush(a);
        var evidence=event(a,"VERIFY_TRANSFER","TRANSFER_EVIDENCE:"+id+":"+a.getVersion(),0,0,Map.of());
        try{evidence.setDetailsJson(json.writeValueAsString(Map.of("reason",request.reason(),"transferEvidence",request.transferEvidence(),"memberId",e.getMemberId(),"sourceOrderId",e.getSourceOrderId())));}catch(Exception ex){throw new IllegalStateException(ex);}events.save(evidence);return quoteLocked(e,List.of());
    }
    public List<String> valueBlocks(MemberEntitlementEntity e,EntitlementValueAccountEntity a){
        var reasons=new ArrayList<String>();if(!"VERIFIED".equals(a.getVerificationStatus()))reasons.add("历史权益待核对，未使用价值未知");
        if(a.getActiveRefundId()!=null)reasons.add("权益退款办理中");if("REFUNDED".equals(e.getStatus()))reasons.add("权益已退款终止");
        if(e.getTransferredFromMemberId()!=null&&(a.getTransferEvidence()==null||a.getTransferEvidence().isBlank()))reasons.add("转让权益须总部核验持有人、原收款凭证及退款受领依据");
        if("VERIFIED".equals(a.getVerificationStatus())&&a.getBalanceCents()<=0)reasons.add("没有可退款的未使用价值");return reasons;
    }
    public Quote quote(Long id){var e=lock(id);refreshTime(e);return quoteLocked(e,List.of());}
    public Quote quoteLocked(MemberEntitlementEntity e,List<String> additional){
        var a=account(e);var blocks=valueBlocks(e,a);blocks.addAll(additional);boolean verified="VERIFIED".equals(a.getVerificationStatus());
        var sources=lockedFunding(e).stream().map(f->new Funding(f.getOrderId(),orders.findById(f.getOrderId()).orElseThrow().getOrderNo(),Money.yuan(f.getRemainingCents()))).toList();
        Integer days=e.getRemainingUses()==null&&verified?periods.findByEntitlementIdOrderByIdAsc(e.getId()).stream().mapToInt(p->p.getTotalDays()-p.getConsumedDays()).sum():null;
        if(days!=null&&"REFUNDED".equals(e.getStatus()))days=0;
        return new Quote(e.getId(),a.getVerificationStatus(),a.getProductType(),verified?Money.yuan(a.getBalanceCents()):null,e.getRemainingUses(),days,a.getVersion(),blocks,a.getActiveRefundId(),sources);
    }
    public Quote reconcile(Long id,ReconcileRequest request){
        var e=lock(id);assertMutable(e);var a=account(e);if("VERIFIED".equals(a.getVerificationStatus()))throw fail("该权益已核对，不能再次改写基准");
        if(a.getVersion()!=request.version()||!Objects.equals(e.getRemainingUses(),request.remainingUses()))throw fail("权益库存或版本已变化，请重新预览");
        if(e.getReservedUses()>0)throw fail("有预占或待签课，请先处理");
        if(e.getTransferredFromMemberId()!=null&&(!SecurityUtil.isSuperAdmin()||request.transferEvidence()==null||request.transferEvidence().isBlank()))throw fail("转让权益须总部填写持有人、原凭证及退款受领核验依据");
        var history=operations.findByEntitlementIdOrderByOperatedAtDesc(id);if(history.stream().anyMatch(o->"REFUND".equals(o.getOperationType())))throw fail("历史退款归属无法确证，不能进行资金核对");
        var sourceIds=new HashSet<Long>();for(var s:request.sources())if(!sourceIds.add(s.orderId()))throw fail("来源订单重复");
        if(!sourceIds.contains(e.getSourceOrderId()))throw fail("必须包含原开卡来源订单");
        var existing=lockedFunding(e);for(var f:existing)if(!sourceIds.contains(f.getOrderId()))throw fail("必须合并已有明确续费来源，不能遗漏或重复分配");
        long knownRenewals=existing.stream().filter(f->!f.getOrderId().equals(e.getSourceOrderId())).count();long renewalOps=history.stream().filter(o->"RENEW".equals(o.getOperationType())).count();
        long submittedRenewals=request.sources().stream().filter(s->!s.orderId().equals(e.getSourceOrderId())).count();
        if(submittedRenewals<Math.max(knownRenewals,renewalOps))throw fail("历史续费来源不完整，不能核对");
        long sum=0;String type=null;var intervals=new ArrayList<LocalDate[]>();
        var sorted=new ArrayList<>(request.sources());sorted.sort(Comparator.comparing(Source::orderId));
        for(var source:sorted){
            var order=lockOrder(source.orderId());var old=funding.findByOrderId(order.getId());if(old.isPresent()&&!old.get().getEntitlementId().equals(id))throw fail("来源订单已归属其他权益");
            if(!Objects.equals(order.getVenueId(),e.getVenueId())||!Objects.equals(order.getProductId(),e.getProductId())||(!Objects.equals(order.getMemberId(),e.getMemberId())&&!Objects.equals(order.getMemberId(),e.getTransferredFromMemberId())))throw fail("来源订单会员、门店或商品不匹配");
            if(!Set.of("NEW","RENEW").contains(order.getOrderType())||!"PAID".equals(order.getStatus())||order.getRefundedAmount().signum()!=0)throw fail("来源订单状态或历史退款归属需人工核验");
            if(!order.getId().equals(e.getSourceOrderId())&&!"RENEW".equals(order.getOrderType()))throw fail("追加来源必须为续费订单");
            long cents=Money.cents(source.unusedAmount());long net=Money.cents(order.getPaidAmount().subtract(order.getRefundedAmount()));if(cents>net)throw fail("未使用金额超过来源订单净实收");
            var f=old.orElseGet(EntitlementFundingEntity::new);String sourceType=old.isPresent()?f.getProductType():source.productType();
            if(sourceType==null||sourceType.isBlank())throw fail("历史来源缺少不可变商品类型，请依据原交易凭证填写productType");
            boolean counted=Set.of("VISIT_CARD","TIMES_CARD","PRIVATE_PACKAGE").contains(sourceType);
            boolean term=Set.of("MONTH_CARD","MONTHLY_CARD","QUARTER_CARD","YEAR_CARD").contains(sourceType);
            if((e.getRemainingUses()==null&&!term)||(e.getRemainingUses()!=null&&!counted))throw fail("核验商品类型与权益库存形态不一致");
            if(type==null)type=sourceType;else if(!type.equals(sourceType))throw fail("资金来源权益类型不一致");
            if(old.isPresent()&&f.getReceivedCents()!=net)throw fail("已记录资金来源的原实收已变化，请人工核查订单");
            f.setEntitlementId(id);f.setOrderId(order.getId());f.setVenueId(e.getVenueId());f.setReceivedCents(net);f.setRemainingCents(cents);f.setProductType(sourceType);f.setVerified(true);if(old.isEmpty())f.setTotalUnits(source.totalUnits());funding.saveAndFlush(f);
            if(e.getRemainingUses()==null){
                if(cents==0&&Integer.valueOf(0).equals(source.totalUnits())){
                    // Explicit exhaustion has no future interval. Preserve any existing period as
                    // completed history rather than inventing a zero-day period or leaving days usable.
                    for(var p:periods.findByEntitlementIdOrderByIdAsc(id))if(p.getFundingId().equals(f.getId())){p.setConsumedDays(p.getTotalDays());periods.save(p);}
                    continue;
                }
                // Historical future-day allocation requires explicit service facts; monetary balance alone is insufficient.
                if(source.totalUnits()==null||source.totalUnits()<1||source.startDate()==null||source.startDate().isBefore(today()))throw fail("时间卡核对须逐来源填写剩余服务天数及不早于今天的起始日");
                long pausedDays=a.getPausedAt()==null?0:Math.max(0,ChronoUnit.DAYS.between(a.getPausedAt(),today()));
                LocalDate limit=e.getValidUntil().plusDays(pausedDays);LocalDate end=source.startDate().plusDays(source.totalUnits()-1L);
                if(source.startDate().isBefore(e.getValidFrom())||end.isAfter(limit))throw fail("核验服务日超出可证明的剩余有效期，不得延长历史服务期");
                for(var interval:intervals)if(!end.isBefore(interval[0])&&!source.startDate().isAfter(interval[1]))throw fail("来源服务日区间重叠，不能重复核验同一服务日");intervals.add(new LocalDate[]{source.startDate(),end});
                // The declared start is the service date if resumed today. Keep the stored cursor
                // relative to the original pause so resume adds elapsed days exactly once.
                var p=periods.findByEntitlementIdOrderByIdAsc(id).stream().filter(x->x.getFundingId().equals(f.getId())).findFirst().orElseGet(EntitlementTermPeriodEntity::new);p.setEntitlementId(id);p.setFundingId(f.getId());p.setTotalDays(source.totalUnits());p.setTotalCents(cents);p.setConsumedDays(0);p.setStartDate(source.startDate());p.setNextServiceDate(source.startDate().minusDays(pausedDays));periods.save(p);
            }
            sum=Money.add(sum,cents);
        }
        if(e.getRemainingUses()!=null&&e.getRemainingUses()<=0&&sum>0)throw fail("无剩余服务次数，不能核对正数未使用价值");
        long prior=a.getBalanceCents();a.setBalanceCents(sum);a.setProductType(type);a.setVerificationStatus("VERIFIED");a.setBaselineRemainingUses(e.getRemainingUses());a.setReconciledBy(SecurityUtil.getCurrentUserId());a.setReconciledAt(now());a.setReconciliationReason(request.reason());a.setTransferEvidence(request.transferEvidence());a.setVersion(a.getVersion()+1);accounts.saveAndFlush(a);
        var baseline=event(a,"RECONCILE","RECONCILE:"+id,sum-prior,0,balances(e));var details=new LinkedHashMap<String,Object>();details.put("reason",request.reason());details.put("transferEvidence",request.transferEvidence());details.put("memberId",e.getMemberId());details.put("remainingUses",e.getRemainingUses());details.put("sources",request.sources());try{baseline.setDetailsJson(json.writeValueAsString(details));}catch(Exception ex){throw new IllegalStateException(ex);}events.save(baseline);return quoteLocked(e,List.of());
    }
    public EntitlementValueEventEntity event(EntitlementValueAccountEntity a,String type,String key,long delta,Integer units,Map<Long,Long> shares){
        var ev=new EntitlementValueEventEntity();ev.setEntitlementId(a.getEntitlementId());ev.setVenueId(a.getVenueId());ev.setBusinessKey(key);ev.setEventType(type);ev.setDeltaCents(delta);ev.setBalanceAfterCents(a.getBalanceCents());ev.setVerified("VERIFIED".equals(a.getVerificationStatus()));ev.setUnitsDelta(units);ev.setOperatorId(SecurityUtil.getCurrentUserId());ev.setOccurredAt(now());try{ev.setAllocationsJson(json.writeValueAsString(shares));}catch(Exception ex){throw new IllegalStateException(ex);}return events.save(ev);
    }
}
