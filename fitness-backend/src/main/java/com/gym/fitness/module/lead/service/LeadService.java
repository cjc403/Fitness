package com.gym.fitness.module.lead.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.commercial.dto.CommercialDtos.SaleRequest;
import com.gym.fitness.module.commercial.entity.SalesOrderEntity;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.commercial.service.CommercialService;
import com.gym.fitness.module.finance.service.EntitlementValueService;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.module.lead.dto.LeadDtos.*;
import com.gym.fitness.module.lead.entity.*;
import com.gym.fitness.module.lead.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LeadService {
    private final SalesLeadRepository leads;
    private final LeadEventRepository events;
    private final LeadTrialRepository trials;
    private final LeadLookupRepository lookup;
    private final SysUserRepository users;
    private final VenueRepository venues;
    private final MemberRepository members;
    private final SalesOrderRepository orders;
    private final MembershipProductRepository products;
    private final CommercialService commercial;
    private final EntitlementValueService values;
    private final VenueMutationLock venueLock;
    private final EntityManager em;
    private final ObjectMapper json;
    private Clock businessClock=Clock.system(ZoneId.of("Asia/Shanghai"));
    private static final List<String> SOURCES=List.of("WALK_IN","REFERRAL","DOUYIN","WECHAT","OTHER");
    private static final List<String> STAGES=List.of("CONSULTING","VISITED","TRIAL_BOOKED","TRIAL_COMPLETED","LOST","CONVERTED");

    @Transactional(readOnly=true)
    public Map<String,Object> workbench(Long requestedVenue,String stage,String filter,String keyword,int page,int size) {
        Long venue=scope(requestedVenue);
        choice(stage,concat("ALL",STAGES),"阶段");choice(filter,List.of("ALL","TODAY","OVERDUE","TRIAL_TODAY"),"筛选");
        if(page<1||size<1||size>100)throw invalid("分页参数不正确");
        LocalDate today=now().toLocalDate();
        var visible=leads.findScoped(venue,sales()?actor():null);
        var batch=loadBatch(visible);
        long count=visible.size(),converted=visible.stream().filter(l->net(l,batch).signum()>0).count();
        var summary=map("totalLeads",count,"newThisMonth",visible.stream().filter(l->!l.getCreatedAt().toLocalDate().isBefore(today.withDayOfMonth(1))).count(),
            "todayFollowUps",visible.stream().filter(l->contactable(l)&&on(l.getNextFollowAt(),today)).count(),
            "overdueFollowUps",visible.stream().filter(l->contactable(l)&&l.getNextFollowAt()!=null&&l.getNextFollowAt().toLocalDate().isBefore(today)).count(),
            "todayTrials",visible.stream().filter(l->trialToday(l,today,batch)).count(),"convertedLeads",converted,"conversionRate",rate(converted,count));
        var channels=new ArrayList<Map<String,Object>>();
        for(String source:SOURCES) {
            var channel=visible.stream().filter(l->source.equals(l.getSource())).toList();long conversions=channel.stream().filter(l->net(l,batch).signum()>0).count();
            channels.add(map("source",source,"total",channel.size(),"visited",channel.stream().filter(l->l.getVisitedAt()!=null).count(),
                "trialCompleted",channel.stream().filter(l->l.getTrialCompletedAt()!=null).count(),"converted",conversions,"conversionRate",rate(conversions,channel.size())));
        }
        String search=keyword==null?"":keyword.trim().toLowerCase(Locale.ROOT);
        var filtered=visible.stream().filter(l->"ALL".equals(stage)||stage.equals(l.getStage()))
            .filter(l->search.isEmpty()||l.getName().toLowerCase(Locale.ROOT).contains(search)||l.getPhone().contains(search))
            .filter(l->switch(filter) {case "TODAY"->contactable(l)&&on(l.getNextFollowAt(),today);case "OVERDUE"->contactable(l)&&l.getNextFollowAt()!=null&&l.getNextFollowAt().toLocalDate().isBefore(today);case "TRIAL_TODAY"->trialToday(l,today,batch);default->true;}).toList();
        long offset=(long)(page-1)*size;
        var records=filtered.stream().skip(offset).limit(size).map(l->leadView(l,batch)).toList();
        return map("summary",summary,"channels",channels,"leads",map("records",records,"total",filtered.size(),"page",page,"size",size));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> options(Long requestedVenue) {
        Long venue=scope(requestedVenue);
        var vs=lookup.venues(venue).stream().map(v->map("id",v[0],"name",v[1])).toList();
        var advisors=lookup.advisors(venue).stream().map(u->map("id",u[0],"name",u[1],"venueId",u[2])).toList();
        var ps=lookup.products(venue).stream().map(p->map("id",p[0],"name",p[1],"price",p[2],"venueId",p[3],"productType",p[4])).toList();
        return map("venues",vs,"advisors",advisors,"products",ps);
    }

    @Transactional(readOnly=true) public Map<String,Object> detail(Long id) { return detail(access(id)); }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> conversionOptions(Long id) {
        var lead=access(id);
        if("CONVERTED".equals(lead.getStage()))return List.of();
        return lookup.conversionCandidates(lead,sales()?lead.getOwnerId():null).stream().map(row->{
            var o=(SalesOrderEntity)row[0];var m=(MemberEntity)row[1];var p=(com.gym.fitness.module.commercial.entity.MembershipProductEntity)row[2];
            return map("orderId",o.getId(),"orderNo",o.getOrderNo(),"memberId",m.getId(),"memberNo",m.getMemberNo(),"memberName",m.getRealName(),"productName",p.getName(),"netAmount",orderNet(o),"paidAt",o.getPaidAt());
        }).toList();
    }

    @Transactional
    public Map<String,Object> create(CreateRequest request) {
        lockOperator();String fingerprint=fingerprint("CREATE",null,request);
        var replay=events.findByActorIdAndRequestKey(actor(),request.idempotencyKey());
        if(replay.isPresent()) {var lead=lock(replay.get().getLeadId());assertFingerprint(replay.get(),fingerprint);return detail(lead);}
        Long venue=scope(request.venueId()); if(venue==null)throw invalid("请选择所属门店");
        String name=text(request.name(),50,"姓名"),phone=text(request.phone(),20,"手机号");
        if(!phone.matches("^1[3-9][0-9]{9}$"))throw invalid("请填写有效的手机号");
        choice(request.source(),SOURCES,"来源");choice(request.intent(),List.of("HIGH","MEDIUM","LOW"),"意向");
        if(sales()&&!Objects.equals(request.ownerId(),actor()))throw forbidden();
        advisor(request.ownerId(),venue,true);note(request.note());future(request.nextFollowAt());
        venueLock.acquire(venue);
        if(leads.existsByVenueIdAndPhone(venue,phone))throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS,"该手机号已在本店建档");
        var lead=new SalesLeadEntity();lead.setName(name);lead.setPhone(phone);lead.setSource(request.source());lead.setIntent(request.intent());
        lead.setOwnerId(request.ownerId());lead.setVenueId(venue);lead.setStage("CONSULTING");lead.setNote(request.note());lead.setNextFollowAt(request.nextFollowAt());
        lead.setCreatedAt(now());lead.setUpdatedAt(now());leads.saveAndFlush(lead);
        event(lead,"CREATED","潜客建档",null,request.nextFollowAt(),request.idempotencyKey(),fingerprint);
        return detail(lead);
    }

    @Transactional
    public Map<String,Object> profile(Long id,ProfileRequest request) {
        lockOperator();var lead=lock(id);String fp=fingerprint("PROFILE",id,request);
        if(replay(lead,request.idempotencyKey(),fp))return detail(lead);version(lead,request.expectedVersion());
        String name=text(request.name(),50,"姓名");choice(request.intent(),List.of("HIGH","MEDIUM","LOW"),"意向");note(request.note());
        if(sales()&&!Objects.equals(lead.getOwnerId(),request.ownerId()))throw forbidden();
        advisor(request.ownerId(),lead.getVenueId(),true);venueLock.acquire(lead.getVenueId());
        lead.setName(name);lead.setIntent(request.intent());lead.setOwnerId(request.ownerId());lead.setNote(request.note());
        finish(lead,"PROFILE","更新潜客资料及归属",null,null,request.idempotencyKey(),fp);return detail(lead);
    }

    @Transactional
    public Map<String,Object> follow(Long id,FollowRequest request) {
        lockOperator();var lead=lock(id);String fp=fingerprint("FOLLOW",id,request);
        if(replay(lead,request.idempotencyKey(),fp))return detail(lead);version(lead,request.expectedVersion());notConverted(lead);
        choice(request.method(),List.of("CALL","WECHAT","VISIT","OTHER"),"联系渠道");text(request.content(),1000,"跟进内容");
        choice(request.outcome(),List.of("CONTACTED","NO_CONTACT","VISITED","INTERESTED","DECLINED","REOPEN"),"跟进结果");future(request.nextFollowAt());
        if("LOST".equals(lead.getStage())&&!"REOPEN".equals(request.outcome()))throw operation("已流失潜客请先重新跟进");
        if("REOPEN".equals(request.outcome())&&!"LOST".equals(lead.getStage()))throw operation("只有已流失潜客可以重新跟进");
        venueLock.acquire(lead.getVenueId());
        if("DECLINED".equals(request.outcome())) {noPending(lead);lead.setStage("LOST");}
        else {if("VISITED".equals(request.outcome())&&lead.getVisitedAt()==null)lead.setVisitedAt(now());lead.setStage(factualStage(lead));}
        lead.setNextFollowAt("DECLINED".equals(request.outcome())?null:request.nextFollowAt());
        finish(lead,"FOLLOW_UP",request.method()+"："+request.content(),request.outcome(),lead.getNextFollowAt(),request.idempotencyKey(),fp);return detail(lead);
    }

    @Transactional
    public Map<String,Object> bookTrial(Long id,TrialRequest request) {
        lockOperator();var lead=lock(id);String fp=fingerprint("TRIAL",id,request);
        if(replay(lead,request.idempotencyKey(),fp))return detail(lead);version(lead,request.expectedVersion());notConverted(lead);
        if("LOST".equals(lead.getStage()))throw operation("已流失潜客请先重新跟进");
        if(request.startsAt()==null||request.endsAt()==null)throw invalid("请填写体验时间");future(request.startsAt());
        Duration duration=Duration.between(request.startsAt(),request.endsAt());if(duration.compareTo(Duration.ofMinutes(15))<0||duration.compareTo(Duration.ofMinutes(180))>0)throw invalid("体验时长须为15至180分钟");
        String content=text(request.content(),1000,"体验内容");var host=advisor(request.hostId(),lead.getVenueId(),false);
        venueLock.acquire(lead.getVenueId());noPending(lead);
        boolean overlap=trials.findByVenueIdAndHostIdAndStatus(lead.getVenueId(),host.getId(),"BOOKED").stream()
            .anyMatch(t->t.getStartsAt().isBefore(request.endsAt())&&request.startsAt().isBefore(t.getEndsAt()));
        if(overlap)throw operation("接待顾问在此时间已有体验预约，请调整时间或接待人");
        var trial=new LeadTrialEntity();trial.setLeadId(id);trial.setVenueId(lead.getVenueId());trial.setHostId(host.getId());trial.setHostName(host.getRealName());
        trial.setStartsAt(request.startsAt());trial.setEndsAt(request.endsAt());trial.setContent(content);trial.setStatus("BOOKED");trial.setCreatedAt(now());trials.saveAndFlush(trial);
        lead.setStage("TRIAL_BOOKED");finish(lead,"TRIAL_BOOKED",content,"BOOKED",null,request.idempotencyKey(),fp);return detail(lead);
    }

    @Transactional
    public Map<String,Object> trialResult(Long id,Long trialId,TrialResultRequest request) {
        lockOperator();var lead=lock(id);String fp=fingerprint("TRIAL_RESULT",List.of(id,trialId),request);
        if(replay(lead,request.idempotencyKey(),fp))return detail(lead);version(lead,request.expectedVersion());
        choice(request.status(),List.of("ATTENDED","NO_SHOW","CANCELLED"),"体验结果");text(request.note(),1000,"结果说明");
        venueLock.acquire(lead.getVenueId());var trial=trials.findById(trialId).filter(t->Objects.equals(t.getLeadId(),id)).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"体验预约不存在"));
        em.refresh(trial);if(!"BOOKED".equals(trial.getStatus()))throw operation("此体验预约已处理");
        if(!"CANCELLED".equals(request.status())&&trial.getStartsAt().isAfter(now()))throw operation("体验开始后才能登记到场结果");
        trial.setStatus(request.status());trial.setResultNote(request.note());trial.setRecordedAt(now());trials.saveAndFlush(trial);
        if("ATTENDED".equals(request.status())) {if(lead.getVisitedAt()==null)lead.setVisitedAt(now());if(lead.getTrialCompletedAt()==null)lead.setTrialCompletedAt(now());}
        lead.setStage(factualStage(lead));finish(lead,"TRIAL_RESULT",request.note(),request.status(),null,request.idempotencyKey(),fp);return detail(lead);
    }

    @Transactional
    public Map<String,Object> convert(Long id,ConvertRequest request) {
        lockOperator();var lead=lock(id);String fp=fingerprint("CONVERT",id,request);
        if(replay(lead,request.idempotencyKey(),fp))return detail(lead);version(lead,request.expectedVersion());notConverted(lead);
        if("LOST".equals(lead.getStage()))throw operation("已流失潜客请先重新跟进");
        choice(request.mode(),List.of("NEW","LINK"),"转化方式");noPending(lead);
        Long memberId,orderId;
        if("NEW".equals(request.mode())) {
            if(request.productId()==null||request.paidAmount()==null||request.paidAmount().compareTo(new BigDecimal("0.01"))<0||request.paidAmount().stripTrailingZeros().scale()>2)throw invalid("请填写开卡商品及至少0.01元的实收金额，最多两位小数");
            choice(request.paymentMethod(),List.of("CASH","WECHAT","ALIPAY","CARD"),"支付渠道");
            // A scalar precheck does not put the product into JPA's first-level cache. The
            // commercial transaction first loads its status/price after acquiring venue, so
            // an off-shelf or price change committed while we wait cannot use an old entity.
            if(!lookup.activeProductExists(request.productId(),lead.getVenueId()))throw invalid("请选择本店在售商品");
            // The operator lock is compatible with advisor FK KEY SHARE checks. CommercialService
            // re-enters this same lock, then the freshly created member lock, then venue lock.
            advisor(lead.getOwnerId(),lead.getVenueId(),false);
            if(lookup.memberExists(lead.getVenueId(),lead.getPhone()))throw operation("该手机号已有会员，请关联合格开卡订单；无合格订单时使用已有会员流程");
            var m=new MemberEntity();m.setMemberNo("LEAD-"+id);m.setRealName(lead.getName());m.setPhone(lead.getPhone());m.setVenueId(lead.getVenueId());
            m.setSalesUserId(lead.getOwnerId());m.setStatus("ACTIVE");m.setBlacklistFlag(false);m.setCreatedBy(actor());members.saveAndFlush(m);
            var sale=new SaleRequest();sale.setMemberId(m.getId());sale.setProductId(request.productId());sale.setPaymentMethod(request.paymentMethod());sale.setPaidAmount(request.paidAmount());
            sale.setRemark("潜客开卡 #"+id);sale.setIdempotencyKey("LEAD-"+id+"-"+fp.substring(0,32));
            var order=commercial.createSale(sale);memberId=m.getId();orderId=order.id();
        } else {
            if(request.orderId()==null)throw invalid("请选择待关联开卡订单");
            var order=orders.findById(request.orderId()).orElseThrow(()->operation("订单不符合潜客转化条件"));
            // Member -> venue ordering is shared with sale/refund operations. Validate only after
            // locking so a simultaneous refund or ownership change cannot bypass qualification.
            values.lockMemberServices(order.getMemberId());venueLock.acquire(lead.getVenueId());em.refresh(order);
            if(!qualifies(lead,order))throw operation("订单不符合潜客转化条件（门店、手机号、归属、建档时间或净实收）");
            memberId=order.getMemberId();orderId=order.getId();
        }
        lead.setConvertedMemberId(memberId);lead.setConvertedOrderId(orderId);lead.setConvertedAt(now());lead.setStage("CONVERTED");lead.setNextFollowAt(null);
        finish(lead,"CONVERTED","NEW".equals(request.mode())?"已按实际收款记录开卡":"关联已支付的开卡订单",request.mode(),null,request.idempotencyKey(),fp);return detail(lead);
    }

    private boolean qualifies(SalesLeadEntity lead,SalesOrderEntity order) {
        if(!Objects.equals(lead.getVenueId(),order.getVenueId())||!"NEW".equals(order.getOrderType())||!List.of("PAID","PARTIALLY_REFUNDED").contains(order.getStatus())||orderNet(order).signum()<=0||order.getPaidAt()==null||order.getPaidAt().isBefore(lead.getCreatedAt())||leads.existsByConvertedOrderId(order.getId()))return false;
        var m=members.findById(order.getMemberId()).orElse(null);
        return m!=null&&Objects.equals(m.getVenueId(),lead.getVenueId())&&lead.getPhone().equals(m.getPhone())&&m.getCreatedAt()!=null&&!m.getCreatedAt().isBefore(lead.getCreatedAt())&&(!sales()||Objects.equals(m.getSalesUserId(),lead.getOwnerId()));
    }
    private Map<String,Object> detail(SalesLeadEntity lead) {
        return map("lead",leadView(lead),"events",events.findByLeadIdOrderByIdDesc(lead.getId()).stream().map(e->map("id",e.getId(),"type",e.getType(),"actorName",e.getActorName(),"content",e.getContent(),"outcome",e.getOutcome(),"nextFollowAt",e.getNextFollowAt(),"createdAt",e.getCreatedAt())).toList(),
            "trials",trials.findByLeadIdOrderByIdDesc(lead.getId()).stream().map(this::trialView).toList());
    }
    private record ReadBatch(Map<Long,SalesOrderEntity> orders,Map<Long,LeadTrialEntity> pending,Map<Long,String> owners,Map<Long,String> venues) {}
    private ReadBatch loadBatch(List<SalesLeadEntity> scoped) {
        if(scoped.isEmpty())return new ReadBatch(Map.of(),Map.of(),Map.of(),Map.of());
        var orderMap=new HashMap<Long,SalesOrderEntity>();orders.findAllById(scoped.stream().map(SalesLeadEntity::getConvertedOrderId).filter(Objects::nonNull).distinct().toList()).forEach(o->orderMap.put(o.getId(),o));
        var pending=new HashMap<Long,LeadTrialEntity>();trials.findByLeadIdInAndStatus(scoped.stream().map(SalesLeadEntity::getId).toList(),"BOOKED").forEach(t->pending.put(t.getLeadId(),t));
        var ownerNames=new HashMap<Long,String>();lookup.ownerNames(scoped.stream().map(SalesLeadEntity::getOwnerId).distinct().toList()).forEach(u->ownerNames.put((Long)u[0],(String)u[1]));
        var venueNames=new HashMap<Long,String>();lookup.venueNames(scoped.stream().map(SalesLeadEntity::getVenueId).distinct().toList()).forEach(v->venueNames.put((Long)v[0],(String)v[1]));
        return new ReadBatch(orderMap,pending,ownerNames,venueNames);
    }
    private Map<String,Object> leadView(SalesLeadEntity l) {return leadView(l,loadBatch(List.of(l)));}
    private Map<String,Object> leadView(SalesLeadEntity l,ReadBatch batch) {
        return map("id",l.getId(),"version",l.getVersion(),"name",l.getName(),"phone",l.getPhone(),"source",l.getSource(),"intent",l.getIntent(),
            "ownerId",l.getOwnerId(),"ownerName",batch.owners().getOrDefault(l.getOwnerId(),"离职顾问"),"venueId",l.getVenueId(),"venueName",batch.venues().getOrDefault(l.getVenueId(),"未知门店"),
            "stage",l.getStage(),"note",l.getNote(),"nextFollowAt",l.getNextFollowAt(),"visitedAt",l.getVisitedAt(),"trialCompletedAt",l.getTrialCompletedAt(),
            "convertedMemberId",l.getConvertedMemberId(),"convertedOrderId",l.getConvertedOrderId(),"convertedAt",l.getConvertedAt(),"conversionNetAmount",net(l,batch),"createdAt",l.getCreatedAt(),"updatedAt",l.getUpdatedAt(),
            "nextTrial",batch.pending().containsKey(l.getId())?trialView(batch.pending().get(l.getId())):null);
    }
    private Map<String,Object> trialView(LeadTrialEntity t) {return map("id",t.getId(),"leadId",t.getLeadId(),"hostId",t.getHostId(),"hostName",t.getHostName(),"startsAt",t.getStartsAt(),"endsAt",t.getEndsAt(),"content",t.getContent(),"status",t.getStatus(),"resultNote",t.getResultNote(),"recordedAt",t.getRecordedAt(),"createdAt",t.getCreatedAt());}
    private BigDecimal net(SalesLeadEntity l,ReadBatch batch) {var order=batch.orders().get(l.getConvertedOrderId());return order!=null&&"NEW".equals(order.getOrderType())&&List.of("PAID","PARTIALLY_REFUNDED").contains(order.getStatus())?orderNet(order):BigDecimal.ZERO;}
    private BigDecimal orderNet(SalesOrderEntity o) {return o.getPaidAmount().subtract(o.getRefundedAmount()==null?BigDecimal.ZERO:o.getRefundedAmount()).max(BigDecimal.ZERO);}
    private void finish(SalesLeadEntity l,String type,String content,String outcome,LocalDateTime next,String key,String fp) {l.setVersion(l.getVersion()+1);l.setUpdatedAt(now());leads.saveAndFlush(l);event(l,type,content,outcome,next,key,fp);}
    private void event(SalesLeadEntity l,String type,String content,String outcome,LocalDateTime next,String key,String fp) {
        var e=new LeadEventEntity();e.setLeadId(l.getId());e.setActorId(actor());e.setActorName(users.findById(actor()).orElseThrow().getRealName());
        e.setType(type);e.setContent(content);e.setOutcome(outcome);e.setNextFollowAt(next);e.setRequestKey(key);e.setRequestFingerprint(fp);e.setCreatedAt(now());events.saveAndFlush(e);
    }
    private boolean replay(SalesLeadEntity l,String key,String fp) {var e=events.findByActorIdAndRequestKey(actor(),key);if(e.isEmpty())return false;assertFingerprint(e.get(),fp);if(!Objects.equals(e.get().getLeadId(),l.getId()))throw invalid("该请求键已用于其他潜客");return true;}
    private void assertFingerprint(LeadEventEntity e,String fp) {if(!fp.equals(e.getRequestFingerprint()))throw invalid("该请求键已用于不同内容，请使用新的请求键");}
    private String fingerprint(String action,Object id,Object request) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(Arrays.asList(action,id,request))));}catch(Exception e){throw new IllegalStateException("Cannot fingerprint lead request",e);}}
    private void version(SalesLeadEntity l,Long expected) {if(expected==null||expected!=l.getVersion())throw operation("资料已更新，请刷新后重试");}
    private void notConverted(SalesLeadEntity l) {if("CONVERTED".equals(l.getStage()))throw operation("该潜客已经开卡");}
    private void noPending(SalesLeadEntity l) {if(trials.existsByLeadIdAndStatus(l.getId(),"BOOKED"))throw operation("请先处理待体验预约");}
    private String factualStage(SalesLeadEntity l) {if(trials.existsByLeadIdAndStatus(l.getId(),"BOOKED"))return "TRIAL_BOOKED";if(l.getTrialCompletedAt()!=null)return "TRIAL_COMPLETED";if(l.getVisitedAt()!=null)return "VISITED";return "CONSULTING";}
    private boolean trialToday(SalesLeadEntity l,LocalDate date,ReadBatch batch) {var trial=batch.pending().get(l.getId());return trial!=null&&on(trial.getStartsAt(),date);}
    private boolean contactable(SalesLeadEntity l) {return !List.of("CONVERTED","LOST").contains(l.getStage());}
    private boolean on(LocalDateTime time,LocalDate date) {return time!=null&&time.toLocalDate().equals(date);}
    private BigDecimal rate(long part,long all) {return all==0?BigDecimal.ZERO:BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(all),2,RoundingMode.HALF_UP);}
    // Hibernate's VM-generated member/order timestamps truncate to timestamp(6). Match that
    // precision before storing/returning lead times so DB rounding cannot move a lead after
    // a member created at the very same clock tick. The Shanghai time basis stays unchanged.
    private LocalDateTime now() {return LocalDateTime.now(businessClock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
    private void future(LocalDateTime time) {if(time!=null&&!time.isAfter(now()))throw invalid("安排时间必须晚于当前时间");}
    private String text(String text,int max,String name) {if(text==null||text.isBlank()||text.length()>max)throw invalid(name+"不能为空且最多"+max+"字");return text.trim();}
    private void note(String value) {if(value!=null&&value.length()>1000)throw invalid("备注最多1000字");}
    private void choice(String value,List<String> values,String name) {if(value==null||!values.contains(value))throw invalid(name+"不正确");}
    private List<String> concat(String first,List<String> rest) {var list=new ArrayList<String>();list.add(first);list.addAll(rest);return list;}
    private Long scope(Long requested) {if(admin())return requested;Long own=SecurityUtil.getCurrentOrganizationId();if(own==null||requested!=null&&!requested.equals(own))throw forbidden();return own;}
    private boolean visible(SalesLeadEntity l) {return admin()||Objects.equals(l.getVenueId(),SecurityUtil.getCurrentOrganizationId())&&(!sales()||Objects.equals(l.getOwnerId(),actor()));}
    private SalesLeadEntity access(Long id) {var l=leads.findById(id).orElseThrow(()->new BusinessException(ErrorCode.NOT_FOUND,"潜客不存在"));if(!visible(l))throw forbidden();return l;}
    private SalesLeadEntity lock(Long id) {access(id);var l=leads.findByIdForUpdate(id).orElseThrow();em.refresh(l);if(!visible(l))throw forbidden();return l;}
    private boolean pg() {return em.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect() instanceof PostgreSQLDialect;}
    private void lockOperator() {if(pg()){if(em.createNativeQuery("SELECT id FROM sys_user WHERE id=:id FOR NO KEY UPDATE").setParameter("id",actor()).getResultList().isEmpty())throw forbidden();}else users.findByIdForUpdate(actor()).orElseThrow(LeadService::forbidden);}
    private SysUserEntity advisor(Long id,Long venue,boolean forbiddenVenue) {
        if(id==null)throw invalid("请选择本店在职会籍顾问");
        // Acquire FK-compatible advisor locks before venue. Another advisor's sale can keep its
        // operator mutex without preventing our insert's foreign-key KEY SHARE check.
        if(pg())em.createNativeQuery("SELECT id FROM sys_user WHERE id=:id FOR KEY SHARE").setParameter("id",id).getResultList();
        var u=users.findById(id).orElseThrow(()->invalid("请选择本店在职会籍顾问"));em.refresh(u);
        if(!Objects.equals(venue,u.getVenueId())) {if(forbiddenVenue)throw forbidden();throw invalid("接待顾问必须属于本店");}
        if(!"ACTIVE".equals(u.getStatus())||!"SALES".equals(u.getRole().getCode()))throw invalid("请选择本店在职会籍顾问");return u;
    }
    private Long actor() {return SecurityUtil.getCurrentUserId();}
    private boolean sales() {return SecurityUtil.hasRole("SALES");}
    private boolean admin() {return SecurityUtil.hasRole("SUPER_ADMIN");}
    private static BusinessException invalid(String m) {return new BusinessException(ErrorCode.PARAM_ERROR,m);}
    private static BusinessException operation(String m) {return new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,m);}
    private static BusinessException forbidden() {return new BusinessException(ErrorCode.FORBIDDEN,"无权访问该门店或潜客");}
    private static Map<String,Object> map(Object... entries) {var m=new LinkedHashMap<String,Object>();for(int i=0;i<entries.length;i+=2)m.put((String)entries[i],entries[i+1]);return m;}
}
