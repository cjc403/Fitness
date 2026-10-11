package com.gym.fitness.module.finance.service;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.finance.dto.FinanceDtos.*;
import com.gym.fitness.module.finance.entity.*;
import com.gym.fitness.module.finance.repository.*;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.training.repository.PrivateLessonRepository;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.util.*;
import static com.gym.fitness.module.finance.service.EntitlementValueService.*;

@Service @RequiredArgsConstructor @Transactional
public class RefundService {
    /** 阻断项标签里的时间格式。 */
    private static final java.time.format.DateTimeFormatter BLOCK_TIME = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private final EntitlementValueService values;
    private final RefundApplicationRepository refunds;
    private final RefundAllocationRepository allocations;
    private final RefundAuditRepository audits;
    private final EntitlementValueAccountRepository accounts;
    private final EntitlementFundingRepository funding;
    private final SalesOrderRepository orders;
    private final MemberEntitlementRepository entitlements;
    private final PaymentTransactionRepository payments;
    private final BookingRepository bookings;
    private final PrivateLessonRepository lessons;
    private final MemberRepository members;
    private final SysUserRepository users;
    private final EntityManager em;
    private final com.gym.fitness.module.notice.service.NotificationService notifications;
    private final com.gym.fitness.module.booking.service.BookingService bookingService;
    public Quote quote(Long id){var e=lockEntitlement(id);values.refreshTime(e);return values.quoteLocked(e,serviceBlocks(e));}
    private List<String> serviceBlocks(MemberEntitlementEntity e){
        var blocks=new ArrayList<String>();if(e.getReservedUses()>0||lessons.findByMemberIdOrderByReservedAtDesc(e.getMemberId()).stream().anyMatch(l->Set.of("RESERVED","PENDING").contains(l.getStatus())))blocks.add("会员有预占课次或待签课，请先处理");
        if(bookings.findByMemberIdOrderByBookedAtDesc(e.getMemberId()).stream().anyMatch(b->Set.of("BOOKED","WAITLISTED").contains(b.getStatus())||("ATTENDED".equals(b.getStatus())&&b.getSchedule().getScheduledEndTime().isAfter(now()))))blocks.add("会员有未完成服务预约（含团课），请先正常取消或完成");
        return blocks;
    }
    private void validateSources(MemberEntitlementEntity e,EntitlementValueAccountEntity a,long expected){
        if(!Objects.equals(a.getMemberId(),e.getMemberId())||!Objects.equals(a.getVenueId(),e.getVenueId()))throw fail("权益持有人或门店已变化");
        long sum=0;for(var f:values.lockedFunding(e)){var o=values.lockOrder(f.getOrderId());if(!f.isVerified()||!Objects.equals(f.getVenueId(),e.getVenueId())||!Objects.equals(o.getVenueId(),e.getVenueId())||!Set.of("PAID","PARTIALLY_REFUNDED").contains(o.getStatus()))throw fail("来源资金未核对、订单状态或归属不一致");long net=Money.cents(o.getPaidAmount().subtract(o.getRefundedAmount()));if(f.getRemainingCents()>net)throw fail("来源订单可退款余额不足");sum=Money.add(sum,f.getRemainingCents());}
        if(sum!=a.getBalanceCents()||sum!=expected)throw fail("价值余额与来源资金不一致，请重新核对");
    }
    public Refund request(RefundRequest request){
        var initial=entitlements.findById(request.entitlementId()).orElseThrow(()->fail("权益不存在"));
        if(SecurityUtil.hasRole("MEMBER"))ownEntitlement(initial.getId());else values.assertScope(initial.getVenueId());
        Long memberId=initial.getMemberId();values.lockMemberServices(memberId);
        var e=lockEntitlement(request.entitlementId());
        if(!Objects.equals(memberId,e.getMemberId()))throw fail("权益持有人已变化，请重新办理");String fingerprint=fingerprint(request);Long who=SecurityUtil.getCurrentUserId();
        var existing=refunds.findByRequestedByAndIdempotencyKey(who,request.idempotencyKey());
        if(existing.isPresent()){if(!existing.get().getRequestFingerprint().equals(fingerprint))throw fail("幂等键已用于不同申请内容");return response(existing.get());}
        values.refreshTime(e);var a=values.account(e);if(a.getVersion()!=request.version())throw fail("权益版本已变化，请重新预览");var blocks=values.valueBlocks(e,a);blocks.addAll(serviceBlocks(e));if(!blocks.isEmpty())throw fail(String.join("；",blocks));
        var reviewers=users.findAll().stream().filter(u->!u.getId().equals(who)&&"ACTIVE".equals(u.getStatus())&&("SUPER_ADMIN".equals(u.getRole().getCode())||("MANAGER".equals(u.getRole().getCode())&&Objects.equals(u.getVenueId(),e.getVenueId())))).toList();if(reviewers.isEmpty())throw fail("没有其他具备范围权限的有效审核人，请联系总部配置审核人");
        validateSources(e,a,a.getBalanceCents());values.pause(e);a=values.account(e);
        var r=new RefundApplicationEntity();r.setRefundNo("RF"+UUID.randomUUID().toString().replace("-",""));r.setEntitlementId(e.getId());r.setActiveEntitlementId(e.getId());r.setMemberId(e.getMemberId());r.setVenueId(e.getVenueId());r.setStatus("PENDING");r.setAmountCents(a.getBalanceCents());r.setValueVersion(a.getVersion());r.setRemainingUsesSnapshot(e.getRemainingUses());r.setReason(request.reason());r.setRequestedBy(who);r.setRequestedAt(now());r.setIdempotencyKey(request.idempotencyKey());r.setRequestFingerprint(fingerprint);refunds.saveAndFlush(r);
        a.setActiveRefundId(r.getId());a.setVersion(a.getVersion()+1);accounts.saveAndFlush(a);audit(r,"REQUEST",request.reason());reviewers.forEach(u->notifications.notifyUser(u.getId(),null,r.getVenueId(),"REFUND","有新的退款申请待审核","退款单 "+r.getRefundNo()+"（¥"+Money.yuan(r.getAmountCents())+"）已提交，请及时审核。","REFUND",r.getId()));return response(r);
    }
    private String fingerprint(RefundRequest r){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((r.entitlementId()+"|"+r.version()+"|"+r.reason()).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private MemberEntitlementEntity lockEntitlement(Long id){return SecurityUtil.hasRole("MEMBER")?values.lockForCurrentMember(id):values.lock(id);}
    private void assertRefundScope(RefundApplicationEntity r){
        if(SecurityUtil.hasRole("MEMBER")){if(!Objects.equals(r.getMemberId(),currentMemberId()))throw fail("只能办理本人退款申请");}
        else values.assertScope(r.getVenueId());
    }
    private RefundApplicationEntity locked(Long id){var r=refunds.findById(id).orElseThrow(()->fail("退款申请不存在"));assertRefundScope(r);lockEntitlement(r.getEntitlementId());em.refresh(r,LockModeType.PESSIMISTIC_WRITE);assertRefundScope(r);return r;}
    private MemberEntitlementEntity active(RefundApplicationEntity r){var e=entitlements.findById(r.getEntitlementId()).orElseThrow();var a=values.account(e);if(!Objects.equals(a.getActiveRefundId(),r.getId())||!Objects.equals(e.getMemberId(),r.getMemberId())||!Objects.equals(e.getVenueId(),r.getVenueId())||!Objects.equals(e.getRemainingUses(),r.getRemainingUsesSnapshot()))throw fail("权益库存、申请或归属已变化");validateSources(e,a,r.getAmountCents());return e;}
    public Refund approve(Long id,String reason){var r=locked(id);if(Objects.equals(r.getRequestedBy(),SecurityUtil.getCurrentUserId()))throw fail("申请人不能审核自己的退款");if(!"PENDING".equals(r.getStatus()))throw fail("仅待审核申请可以审核");var e=active(r);if(!serviceBlocks(e).isEmpty())throw fail("存在未完成服务，不能审核");r.setStatus("APPROVED");r.setApprovedBy(SecurityUtil.getCurrentUserId());r.setApprovedAt(now());r.setDecisionReason(reason);refunds.save(r);audit(r,"APPROVE",reason);notifications.notifyMember(r.getMemberId(),r.getVenueId(),"REFUND","退款申请已通过审核","退款单 "+r.getRefundNo()+"（¥"+Money.yuan(r.getAmountCents())+"）已通过审核，等待门店登记退款支付。","REFUND",r.getId());return response(r);}
    public Refund reject(Long id,String reason){var r=locked(id);if(Objects.equals(r.getRequestedBy(),SecurityUtil.getCurrentUserId()))throw fail("申请人不能审核自己的退款");if(!"PENDING".equals(r.getStatus()))throw fail("仅待审核申请可以拒绝");return release(r,"REJECTED","REJECT",reason);}
    public Refund cancel(Long id,String reason){var r=locked(id);if("PENDING".equals(r.getStatus())){if(!Objects.equals(r.getRequestedBy(),SecurityUtil.getCurrentUserId()))throw fail("待审核申请仅申请人可撤回");}else if("APPROVED".equals(r.getStatus())){if(!SecurityUtil.isSuperAdmin())throw fail("已审核未支付申请仅总部可撤回");}else throw fail("当前状态不能撤回");return release(r,"CANCELLED","CANCEL",reason);}
    private Refund release(RefundApplicationEntity r,String status,String action,String reason){
        var e=entitlements.findById(r.getEntitlementId()).orElseThrow();var a=values.account(e);if(!Objects.equals(a.getActiveRefundId(),r.getId()))throw fail("退款办理标记不一致");
        a.setActiveRefundId(null);a.setVersion(a.getVersion()+1);accounts.saveAndFlush(a);
        if(!"FROZEN".equals(e.getStatus())){long days=values.resume(e);e.setValidUntil(e.getValidUntil().plusDays(days));if("EXPIRED".equals(e.getStatus())&&!e.getValidUntil().isBefore(today()))e.setStatus("ACTIVE");entitlements.save(e);}
        r.setActiveEntitlementId(null);r.setStatus(status);r.setDecisionReason(reason);refunds.saveAndFlush(r);audit(r,action,reason);
        if("REJECTED".equals(status))notifications.notifyMember(r.getMemberId(),r.getVenueId(),"REFUND","退款申请未通过","退款单 "+r.getRefundNo()+" 未通过审核："+(reason==null||reason.isBlank()?"未填写原因":reason),"REFUND",r.getId());
        return response(r);
    }
    public Refund pay(Long id,PayRequest request){
        var r=locked(id);long cents=Money.cents(request.amount());String channel=request.channel().trim().toUpperCase(Locale.ROOT);String reference=request.externalReference().trim();
        if("PAID".equals(r.getStatus())){if(r.getAmountCents()==cents&&Objects.equals(r.getChannel(),channel)&&Objects.equals(r.getExternalReference(),reference))return response(r);throw fail("已登记退款不能修改金额或原凭证");}
        if(!"APPROVED".equals(r.getStatus()))throw fail("仅审核同意的申请可以登记支付");if(cents!=r.getAmountCents()||cents<=0)throw fail("登记金额必须精确等于审核金额");if(request.occurredAt().isAfter(now()))throw fail("退款发生时间不能在未来");
        if(refunds.findByChannelAndExternalReference(channel,reference).isPresent())throw fail("该渠道凭证已用于其他退款");
        var e=active(r);var a=values.account(e);var sources=values.lockedFunding(e);var balances=new TreeMap<Long,Long>();sources.forEach(f->balances.put(f.getOrderId(),f.getRemainingCents()));var shares=FundingAllocator.allocate(cents,balances);
        for(var f:sources){long share=shares.get(f.getOrderId());if(share==0)continue;var order=values.lockOrder(f.getOrderId());order.setRefundedAmount(order.getRefundedAmount().add(Money.yuan(share)));order.setStatus(order.getRefundedAmount().compareTo(order.getPaidAmount())==0?"REFUNDED":"PARTIALLY_REFUNDED");orders.save(order);
            var payment=new PaymentTransactionEntity();payment.setTransactionNo("TX"+UUID.randomUUID().toString().replace("-",""));payment.setOrderId(order.getId());payment.setTransactionType("REFUND");payment.setAmount(Money.yuan(share));payment.setPaymentMethod(channel);payment.setExternalReference(reference);payment.setStatus("SUCCESS");payment.setOccurredAt(request.occurredAt());payment.setOperatorId(SecurityUtil.getCurrentUserId());payments.saveAndFlush(payment);
            var allocation=new RefundAllocationEntity();allocation.setRefundId(id);allocation.setFundingId(f.getId());allocation.setOrderId(order.getId());allocation.setAmountCents(share);allocation.setPaymentTransactionId(payment.getId());allocations.save(allocation);f.setRemainingCents(f.getRemainingCents()-share);funding.save(f);
        }
        a.setBalanceCents(0);a.setActiveRefundId(null);a.setVersion(a.getVersion()+1);accounts.save(a);values.event(a,"REFUND","REFUND:"+id,-cents,e.getRemainingUses()==null?null:-e.getRemainingUses(),shares);
        e.setStatus("REFUNDED");if(e.getRemainingUses()!=null)e.setRemainingUses(0);entitlements.save(e);r.setStatus("PAID");r.setActiveEntitlementId(null);r.setChannel(channel);r.setExternalReference(reference);r.setPaidBy(SecurityUtil.getCurrentUserId());r.setPaidAt(request.occurredAt());r.setPaymentNote(request.note());refunds.saveAndFlush(r);audit(r,"PAY",request.note());notifications.notifyMember(r.getMemberId(),r.getVenueId(),"REFUND","退款已登记支付","退款单 "+r.getRefundNo()+" 已登记退款支付（¥"+Money.yuan(cents)+"），请留意到账。","REFUND",r.getId());return response(r);
    }
    @Transactional(readOnly=true) public List<Refund> list(){return toResponses(SecurityUtil.isSuperAdmin()?refunds.findAllByOrderByIdDesc():refunds.findByVenueIdOrderByIdDesc(SecurityUtil.getCurrentOrganizationId()));}
    @Transactional(readOnly=true) public List<MemberRefund> mine(){Long id=currentMemberId();return refunds.findByMemberIdOrderByIdDesc(id).stream().map(r->new MemberRefund(r.getId(),r.getRefundNo(),entitlements.findById(r.getEntitlementId()).orElseThrow().getEntitlementNo(),r.getStatus(),Money.yuan(r.getAmountCents()),r.getRequestedAt(),r.getApprovedAt(),r.getPaidAt())).toList();}
    private Long currentMemberId(){return members.findByUserId(SecurityUtil.getCurrentUserId()).orElseThrow(()->fail("当前账号未绑定会员")).getId();}
    /**
     * 会员自助退款的归属校验：values.lock 只校验门店范围，不校验会员本人，
     * 所以必须显式确认该权益属于当前登录会员，否则同门店会员之间可以互相发起退款。
     */
    private MemberEntitlementEntity ownEntitlement(Long entitlementId){
        var e=entitlements.findById(entitlementId).orElseThrow(()->fail("权益不存在"));
        if(!Objects.equals(e.getMemberId(),currentMemberId()))throw fail("只能对自己名下的权益申请退款");
        return e;
    }
    /** 列出挡住退款的未完成预约与待签课，并标出哪些能一键自助取消。 */
    @Transactional(readOnly=true)
    public List<Blocker> memberBlockers(Long entitlementId){var e=ownEntitlement(entitlementId);return blockers(e,now());}
    private List<Blocker> blockers(MemberEntitlementEntity e,LocalDateTime at){
        List<Blocker> out=new ArrayList<>();LocalDateTime cutoff=at.plusHours(2);
        for(var b:bookings.findByMemberIdOrderByBookedAtDesc(e.getMemberId())){
            String st=b.getStatus();var s=b.getSchedule();
            boolean blocking=Set.of("BOOKED","WAITLISTED").contains(st)||("ATTENDED".equals(st)&&s!=null&&s.getScheduledEndTime().isAfter(at));
            if(!blocking)continue;
            boolean open=Set.of("BOOKED","WAITLISTED").contains(st);
            // 已结束却仍是“已预约”的历史记录：既不是 2 小时规则，也不是会员能自助取消的，必须分开说明
            boolean past=s!=null&&s.getScheduledStartTime().isBefore(at),inTime=s!=null&&s.getScheduledStartTime().isAfter(cutoff);
            String course=s==null||s.getCourse()==null?"预约":"《"+s.getCourse().getName()+"》";
            String label=course+(s==null?"":" "+BLOCK_TIME.format(s.getScheduledStartTime()));
            String detail=!open?"已签到且课程未结束，无法取消":past?"课程已结束但未签到，需前台核销或标记爽约后即可退款":inTime?"开课时间充裕，可直接自助取消":"开课前 2 小时内，需联系前台或店长办理";
            out.add(new Blocker("BOOKING",b.getId(),null,label,detail,open&&inTime&&!past));
        }
        // RESERVED 的私教课随所属预约一起取消并释放，所以这里只单列“已提交待签课”（会员无权取消）
        for(var l:lessons.findByMemberIdOrderByReservedAtDesc(e.getMemberId())){
            if(!"PENDING".equals(l.getStatus()))continue;
            out.add(new Blocker("LESSON",l.getBookingId(),l.getId(),"私教课 · 已提交待签课","教练已提交授课，需本人签课或由门店冲正",false));
        }
        return out;
    }
    /**
     * 一键取消可自助取消的阻断项，让会员满足退款前置条件。
     *
     * <p>刻意<b>不加 @Transactional</b>：每条取消都在它自己的事务里执行（cancelForCurrentMember 本身是 @Transactional），
     * 这样某一条因业务规则失败（例如开课前 2 小时内不能自助取消）只影响它自己。
     * 若这里也开事务，内层抛出的异常会把事务标记为 rollback-only，整批都会失败。</p>
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ClearBlockers memberClearBlockers(Long entitlementId){
        var e=ownEntitlement(entitlementId);
        List<ClearBlockers.Item> cancelled=new ArrayList<>(),skipped=new ArrayList<>();
        for(var b:blockers(e,now())){
            if(!"BOOKING".equals(b.type())||!b.cancellable()){skipped.add(new ClearBlockers.Item(b.label(),b.detail()));continue;}
            try{bookingService.cancelForCurrentMember(b.bookingId());cancelled.add(new ClearBlockers.Item(b.label(),"已取消，预占次数已释放"));}
            catch(RuntimeException ex){skipped.add(new ClearBlockers.Item(b.label(),ex.getMessage()));}
        }
        return new ClearBlockers(cancelled,skipped,serviceBlocks(entitlements.findById(e.getId()).orElse(e)));
    }
    /** 会员自助报价：退回金额仍由服务端按价值账余额核定，会员无法指定金额。 */
    public Quote memberQuote(Long entitlementId){return quote(ownEntitlement(entitlementId).getId());}
    /** 会员自助申请：校验归属后走与员工侧完全相同的申请逻辑（含幂等键、价值账版本、服务阻断校验）。 */
    public Refund memberRequest(RefundRequest request){ownEntitlement(request.entitlementId());return request(request);}
    /** 会员撤回本人的退款申请（仅待审核可撤回，复用 cancel 的原有状态校验）。 */
    public Refund memberCancel(Long id,String reason){
        var r=refunds.findById(id).orElseThrow(()->fail("退款申请不存在"));
        if(!Objects.equals(r.getMemberId(),currentMemberId()))throw fail("只能撤回本人的退款申请");
        return cancel(id,reason);
    }
    private void audit(RefundApplicationEntity r,String action,String reason){var a=new RefundAuditEntity();a.setRefundId(r.getId());a.setAction(action);a.setReason(reason);a.setOperatorId(SecurityUtil.getCurrentUserId());a.setOccurredAt(now());audits.save(a);}
    private Refund response(RefundApplicationEntity r){return toResponses(List.of(r)).get(0);}
    /**
     * 批量装配退款单：此前每条记录都要 findById 取权益、会员，再按分摊逐条查订单，
     * 列表页是最典型的 N+1（N 条记录 ≈ 2N + 分摊数次查询）。改成批量捞 + Map 组装。
     */
    private List<Refund> toResponses(List<RefundApplicationEntity> rows){
        if(rows.isEmpty())return List.of();
        List<Long> ids=rows.stream().map(RefundApplicationEntity::getId).toList();
        Map<Long,MemberEntitlementEntity> entMap=entitlements.findAllById(rows.stream().map(RefundApplicationEntity::getEntitlementId).distinct().toList()).stream().collect(java.util.stream.Collectors.toMap(MemberEntitlementEntity::getId,e->e));
        Map<Long,com.gym.fitness.module.member.entity.MemberEntity> memberMap=members.findAllById(rows.stream().map(RefundApplicationEntity::getMemberId).distinct().toList()).stream().collect(java.util.stream.Collectors.toMap(com.gym.fitness.module.member.entity.MemberEntity::getId,m->m));
        Map<Long,List<RefundAllocationEntity>> allocMap=allocations.findByRefundIdInOrderByRefundIdAscOrderIdAsc(ids).stream().collect(java.util.stream.Collectors.groupingBy(RefundAllocationEntity::getRefundId));
        Map<Long,List<RefundAuditEntity>> auditMap=audits.findByRefundIdInOrderByRefundIdAscIdAsc(ids).stream().collect(java.util.stream.Collectors.groupingBy(RefundAuditEntity::getRefundId));
        Map<Long,SalesOrderEntity> orderMap=orders.findAllById(allocMap.values().stream().flatMap(List::stream).map(RefundAllocationEntity::getOrderId).distinct().toList()).stream().collect(java.util.stream.Collectors.toMap(SalesOrderEntity::getId,o->o));
        return rows.stream().map(r->{
            var e=entMap.get(r.getEntitlementId());var m=memberMap.get(r.getMemberId());
            return new Refund(r.getId(),r.getRefundNo(),r.getEntitlementId(),e==null?null:e.getEntitlementNo(),r.getMemberId(),m==null?null:m.getRealName(),r.getVenueId(),r.getStatus(),Money.yuan(r.getAmountCents()),r.getReason(),r.getRequestedBy(),r.getRequestedAt(),r.getApprovedBy(),r.getApprovedAt(),r.getPaidAt(),r.getDecisionReason(),
                    allocMap.getOrDefault(r.getId(),List.of()).stream().map(x->{var o=orderMap.get(x.getOrderId());return new Allocation(x.getOrderId(),o==null?null:o.getOrderNo(),Money.yuan(x.getAmountCents()));}).toList(),
                    auditMap.getOrDefault(r.getId(),List.of()).stream().map(x->new Audit(x.getAction(),x.getReason(),x.getOccurredAt())).toList());
        }).toList();
    }
}
