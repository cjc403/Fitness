package com.gym.fitness.module.finance.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.commercial.entity.PaymentTransactionEntity;
import com.gym.fitness.module.commercial.entity.SalesOrderEntity;
import com.gym.fitness.module.commercial.repository.MemberEntitlementRepository;
import com.gym.fitness.module.commercial.repository.PaymentTransactionRepository;
import com.gym.fitness.module.commercial.repository.SalesOrderRepository;
import com.gym.fitness.module.finance.dto.FinanceDtos.*;
import com.gym.fitness.module.finance.entity.*;
import com.gym.fitness.module.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FinanceReportService {
    private static final int MAX_DAYS=366, MAX_EXPORT_ROWS=10_000;
    private final PaymentTransactionRepository payments; private final SalesOrderRepository orders; private final RefundAllocationRepository allocations; private final RefundApplicationRepository refunds;
    private final EntitlementValueAccountRepository accounts; private final EntitlementValueEventRepository events; private final EntitlementFundingRepository funding; private final EntitlementTermPeriodRepository periods; private final MemberEntitlementRepository entitlements;
    public Report report(LocalDate from,LocalDate to){Range range=range(from,to);Long venue=scope();Cash cash=cash(range,null);List<EntitlementValueAccountEntity> accountRows=venue==null?accounts.findAll():accounts.findByVenueId(venue);Map<Long,EntitlementValueAccountEntity> byEntitlement=accountRows.stream().collect(Collectors.toMap(EntitlementValueAccountEntity::getEntitlementId,a->a,(a,b)->a));List<Long> ids=new ArrayList<>(byEntitlement.keySet());Map<Long,Long> chains=ids.isEmpty()?Map.of():events.findByEntitlementIdInOrderByEntitlementIdAscIdAsc(ids).stream().collect(Collectors.groupingBy(EntitlementValueEventEntity::getEntitlementId,LinkedHashMap::new,Collectors.summingLong(EntitlementValueEventEntity::getDeltaCents)));List<Difference> differences=new ArrayList<>();long verified=0;int unverified=0;for(var a:accountRows){if("VERIFIED".equals(a.getVerificationStatus()))verified=Math.addExact(verified,a.getBalanceCents());else unverified++;long chain=chains.getOrDefault(a.getEntitlementId(),0L);if(chain!=a.getBalanceCents())differences.add(diff("ACCOUNT_CHAIN",a.getEntitlementId(),a.getBalanceCents(),chain,"价值账户余额与全部事件 delta 不一致"));}List<com.gym.fitness.module.commercial.entity.MemberEntitlementEntity> entitlementRows=venue==null?entitlements.findAllByOrderByIdDesc():entitlements.findByVenueIdOrderByIdDesc(venue);for(var e:entitlementRows)if(!byEntitlement.containsKey(e.getId())){unverified++;differences.add(new Difference("MISSING_ACCOUNT",e.getId(),null,null,"权益没有价值账户，余额未知"));}differences.addAll(reconcile(venue,accountRows));differences.addAll(refundEntitlementAndReuse(venue));differences.addAll(cash.differences());Valuation valuation=serviceConsumed(range,venue,entitlementRows,byEntitlement);return new Report(from,to,Money.yuan(cash.receipts()),Money.yuan(cash.refunds()),Money.yuan(cash.receipts()-cash.refunds()),Money.yuan(verified-valuation.unposted()),unverified,Money.yuan(valuation.consumed()),cash.channels(),differences,valuation.date());}
    public byte[] export(LocalDate from,LocalDate to) {
        Cash cash=cash(range(from,to),MAX_EXPORT_ROWS+1);
        if(cash.rawRows().size()>MAX_EXPORT_ROWS)throw fail("导出流水超过10000条，请缩短日期范围");
        // 导出给人看的：中文表头 + 中文取值，行首保留 UTF-8 BOM（没有 BOM 时 Excel/WPS 打开会乱码）。
        // 以后新增导出请沿用同一约定：中文表头 + 中文取值 + BOM。
        StringBuilder csv=new StringBuilder("\uFEFF");
        csv.append("\"发生时间\",\"交易编号\",\"交易类型\",\"订单号\",\"金额(元)\",\"支付渠道\",\"支付流水号\",\"对账状态\"\n");
        for(var p:cash.rows()) csv.append(Csv.cell(stampText(p.getOccurredAt()))).append(',')
            .append(Csv.cell(p.getTransactionNo())).append(',').append(Csv.cell(typeText(p.getTransactionType()))).append(',')
            .append(Csv.cell(String.valueOf(p.getOrderId()))).append(',').append(Csv.cell(Money.yuan(Money.cents(p.getAmount())).toPlainString())).append(',')
            .append(Csv.cell(channelText(p.getPaymentMethod()))).append(',').append(Csv.cell(String.valueOf(p.getId()))).append(',')
            .append(Csv.cell(reconciliationText(cash.integrity().get(p.getId())))).append('\n');
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 导出 Excel（.xlsx）：概要 / 渠道现金 / 流水明细 / 完整性差异 四张表。
     *
     * <p>与页面展示**同源**（都走 report()），保证"导出看到的"和"界面看到的"完全一致；
     * 中文表头 + 中文取值，列宽与表头样式都已设置，打开即可直接用，不需要手动拉列宽。</p>
     */
    public byte[] exportExcel(LocalDate from, LocalDate to) {
        Report report = report(from, to);
        Range range = range(from, to);
        Cash cash = cash(range, null);
        try (com.gym.fitness.common.export.XlsxBuilder xlsx = new com.gym.fitness.common.export.XlsxBuilder()) {
            // ① 概要
            xlsx.sheet("概要", "项目", "数值", "说明")
                    .row("统计区间", report.from() + " 至 " + report.to(), "")
                    .row("收款", report.receipts(), "区间内已支付流水合计")
                    .row("退款", report.refunds(), "区间内已退款流水合计")
                    .row("净现金", report.netCash(), "收款 - 退款")
                    .row("未使用服务价值", report.unusedVerifiedValue(), "已核对权益的剩余价值")
                    .row("待核对权益", report.unverifiedEntitlements(), "尚未核对资金归属的权益数量")
                    .row("服务消耗", report.serviceConsumed(), "截至 " + report.valuationDate() + "（含只读预估，不写入账务）")
                    .width(0, 18).width(1, 18).width(2, 46);

            // ② 渠道现金
            xlsx.sheet("渠道现金", "支付渠道", "收款", "退款", "净现金")
                    .width(0, 16).width(1, 16).width(2, 16).width(3, 16);
            for (Channel c : report.channels()) {
                xlsx.row(channelText(c.channel()), c.receipts(), c.refunds(), c.netCash());
            }
            if (xlsx.isEmptySheet()) xlsx.row("（该区间没有收退款流水）", null, null, null);

            // ③ 流水明细
            xlsx.sheet("流水明细", "发生时间", "交易编号", "交易类型", "订单号", "金额(元)", "支付渠道", "支付流水号", "对账状态")
                    .width(0, 20).width(1, 26).width(2, 12).width(3, 10)
                    .width(4, 14).width(5, 12).width(6, 14).width(7, 14);
            for (var p : cash.rows()) {
                xlsx.row(p.getOccurredAt(), p.getTransactionNo(), typeText(p.getTransactionType()),
                        p.getOrderId(), Money.yuan(Money.cents(p.getAmount())),
                        channelText(p.getPaymentMethod()), p.getId(),
                        reconciliationText(cash.integrity().get(p.getId())));
            }
            if (xlsx.isEmptySheet()) xlsx.row("（该区间没有流水）", null, null, null, null, null, null, null);

            // ④ 完整性差异
            xlsx.sheet("完整性差异", "差异类型", "对象", "账户余额", "事件余额", "说明")
                    .width(0, 20).width(1, 30).width(2, 16).width(3, 16).width(4, 62);
            for (Difference d : report.differences()) {
                xlsx.row(differenceTypeText(d.type()), subjectText(d),
                        d.accountBalance(), d.eventBalance(), d.message());
            }
            if (xlsx.isEmptySheet()) xlsx.row("当前范围内没有对账差异", "—", null, null, null);

            return xlsx.toBytes();
        }
    }

    /** 差异类型的中文说法（未收录的保留原码，避免误译）。 */
    private static String differenceTypeText(String type) {
        if (type == null) return "—";
        return switch (type) {
            case "ACCOUNT_CHAIN" -> "账户与事件链不一致";
            case "MISSING_ACCOUNT" -> "权益缺少价值账户";
            case "FUNDING_ACCOUNT" -> "资金来源与账户不一致";
            case "PAID_REFUND" -> "已支付退款不一致";
            default -> type;
        };
    }

    /** 差异涉及的对象，写成"订单 #1 · 流水 #2"这种能直接看懂的形式。 */
    private static String subjectText(Difference d) {
        String subject = switch (d.subjectType() == null ? "" : d.subjectType()) {
            case "ENTITLEMENT" -> "权益";
            case "ORDER" -> "订单";
            case "PAYMENT_TRANSACTION" -> "支付流水";
            case "REFUND" -> "退款单";
            default -> d.subjectType() == null ? "—" : d.subjectType();
        };
        StringBuilder sb = new StringBuilder(subject);
        if (d.subjectId() != null) sb.append(" #").append(d.subjectId());
        if (d.orderId() != null) sb.append(" · 订单 #").append(d.orderId());
        if (d.paymentTransactionId() != null) sb.append(" · 流水 #").append(d.paymentTransactionId());
        if (d.refundId() != null) sb.append(" · 退款单 #").append(d.refundId());
        return sb.toString();
    }
    public BigDecimal netCash(LocalDate from,LocalDate to){Cash cash=cash(range(from,to),null);return Money.yuan(cash.receipts()-cash.refunds());}
    private List<Difference> reconcile(Long venue,List<EntitlementValueAccountEntity> accountRows){List<SalesOrderEntity> orderRows=venue==null?orders.findAllByOrderByCreatedAtDesc():orders.findByVenueIdOrderByCreatedAtDesc(venue);List<Long> orderIds=orderRows.stream().map(SalesOrderEntity::getId).toList();List<PaymentTransactionEntity> historical=orderIds.isEmpty()?List.of():payments.findByOrderIdInOrderByOccurredAtDesc(orderIds).stream().filter(p->"SUCCESS".equals(p.getStatus())&&Set.of("PAYMENT","REFUND").contains(p.getTransactionType())).toList();Map<Long,List<PaymentTransactionEntity>> paymentByOrder=historical.stream().collect(Collectors.groupingBy(PaymentTransactionEntity::getOrderId));List<Difference> result=new ArrayList<>();for(var order:orderRows){long receipt=sum(paymentByOrder.get(order.getId()),"PAYMENT"),refund=sum(paymentByOrder.get(order.getId()),"REFUND");if(receipt!=Money.cents(order.getPaidAmount())||refund!=Money.cents(order.getRefundedAmount()))result.add(orderDifference(order.getId(),Money.cents(order.getPaidAmount())-Money.cents(order.getRefundedAmount()),receipt-refund,"订单金额与全部历史成功支付流水不一致"));}List<EntitlementFundingEntity> sourceRows=venue==null?funding.findAll():funding.findByVenueId(venue);Map<Long,Long> sourceBalance=sourceRows.stream().collect(Collectors.groupingBy(EntitlementFundingEntity::getEntitlementId,Collectors.summingLong(EntitlementFundingEntity::getRemainingCents)));for(var a:accountRows){long sources=sourceBalance.getOrDefault(a.getEntitlementId(),0L);if(sources!=a.getBalanceCents())result.add(diff("FUNDING_ACCOUNT",a.getEntitlementId(),a.getBalanceCents(),sources,"资金来源余额与价值账户余额不一致"));}List<RefundApplicationEntity> paid=(venue==null?refunds.findAllByOrderByIdDesc():refunds.findByVenueIdOrderByIdDesc(venue)).stream().filter(r->"PAID".equals(r.getStatus())).toList();if(paid.isEmpty())return result;List<RefundAllocationEntity> allocationRows=allocations.findByRefundIdIn(paid.stream().map(RefundApplicationEntity::getId).toList());Map<Long,List<RefundAllocationEntity>> allocationByRefund=allocationRows.stream().collect(Collectors.groupingBy(RefundAllocationEntity::getRefundId));Map<Long,EntitlementFundingEntity> sourceById=funding.findAllById(allocationRows.stream().map(RefundAllocationEntity::getFundingId).distinct().toList()).stream().collect(Collectors.toMap(EntitlementFundingEntity::getId,f->f));Map<Long,SalesOrderEntity> allocationOrders=orders.findAllById(allocationRows.stream().map(RefundAllocationEntity::getOrderId).distinct().toList()).stream().collect(Collectors.toMap(SalesOrderEntity::getId,o->o));Map<Long,PaymentTransactionEntity> paymentById=payments.findAllById(allocationRows.stream().map(RefundAllocationEntity::getPaymentTransactionId).filter(Objects::nonNull).distinct().toList()).stream().collect(Collectors.toMap(PaymentTransactionEntity::getId,p->p));for(var r:paid){List<RefundAllocationEntity> rows=allocationByRefund.getOrDefault(r.getId(),List.of());boolean bad=rows.isEmpty()||rows.stream().mapToLong(RefundAllocationEntity::getAmountCents).sum()!=r.getAmountCents();for(var a:rows){var f=sourceById.get(a.getFundingId());var o=allocationOrders.get(a.getOrderId());var p=a.getPaymentTransactionId()==null?null:paymentById.get(a.getPaymentTransactionId());if(f==null||o==null||p==null||!Objects.equals(f.getOrderId(),a.getOrderId())||!Objects.equals(f.getEntitlementId(),r.getEntitlementId())||!Objects.equals(f.getVenueId(),r.getVenueId())||!Objects.equals(o.getVenueId(),r.getVenueId())||!Objects.equals(p.getOrderId(),a.getOrderId())||!"SUCCESS".equals(p.getStatus())||!"REFUND".equals(p.getTransactionType())||Money.cents(p.getAmount())!=a.getAmountCents())bad=true;}if(bad)result.add(refundDifference(r.getId(),r.getAmountCents(),rows.stream().mapToLong(RefundAllocationEntity::getAmountCents).sum(),"已支付退款与分摊、订单、资金来源或退款流水不一致"));}return result;}
    private Cash cash(Range range,Integer limit) {
        Long venue=scope();
        List<PaymentTransactionEntity> raw=limit==null?payments.findSuccessfulCashTransactions(range.from(),range.until(),venue):payments.findSuccessfulCashTransactions(range.from(),range.until(),venue,org.springframework.data.domain.PageRequest.of(0,limit));
        List<Long> ids=raw.stream().map(PaymentTransactionEntity::getId).toList();
        Map<Long,List<RefundAllocationEntity>> byPayment=ids.isEmpty()?Map.of():allocations.findByPaymentTransactionIdIn(ids).stream().collect(Collectors.groupingBy(RefundAllocationEntity::getPaymentTransactionId));
        List<RefundAllocationEntity> links=byPayment.values().stream().flatMap(List::stream).toList();
        Map<Long,RefundApplicationEntity> refundById=refunds.findAllById(links.stream().map(RefundAllocationEntity::getRefundId).distinct().toList()).stream().collect(Collectors.toMap(RefundApplicationEntity::getId,r->r));
        Map<Long,com.gym.fitness.module.commercial.entity.MemberEntitlementEntity> entitlementById=entitlements.findAllById(refundById.values().stream().map(RefundApplicationEntity::getEntitlementId).distinct().toList()).stream().collect(Collectors.toMap(com.gym.fitness.module.commercial.entity.MemberEntitlementEntity::getId,e->e));
        Map<Long,Long> allocatedByRefund=refundById.isEmpty()?Map.of():allocations.findByRefundIdIn(new ArrayList<>(refundById.keySet())).stream().collect(Collectors.groupingBy(RefundAllocationEntity::getRefundId,Collectors.summingLong(RefundAllocationEntity::getAmountCents)));
        Map<Long,EntitlementFundingEntity> sourceById=funding.findAllById(links.stream().map(RefundAllocationEntity::getFundingId).distinct().toList()).stream().collect(Collectors.toMap(EntitlementFundingEntity::getId,f->f));
        Map<Long,SalesOrderEntity> orderById=orders.findAllById(raw.stream().map(PaymentTransactionEntity::getOrderId).distinct().toList()).stream().collect(Collectors.toMap(SalesOrderEntity::getId,o->o));
        long receipts=0,refundCash=0;
        Map<String,long[]> totals=new TreeMap<>();
        Map<Long,String> integrity=new HashMap<>();
        List<Difference> differences=new ArrayList<>();
        for(var p:raw) {
            long cents=Money.cents(p.getAmount()); boolean receipt="PAYMENT".equals(p.getTransactionType());
            if(!receipt) {
                List<RefundAllocationEntity> linked=byPayment.getOrDefault(p.getId(),List.of());
                String state=linked.isEmpty()?"LEGACY_OR_UNLINKED":"LINKED";
                if(!linked.isEmpty()) {
                    var a=linked.get(0); var r=refundById.get(a.getRefundId()); var f=sourceById.get(a.getFundingId()); var o=orderById.get(p.getOrderId());
                    var e=r==null?null:entitlementById.get(r.getEntitlementId());
                    if(linked.size()!=1||r==null||f==null||o==null||e==null||!"PAID".equals(r.getStatus())||a.getAmountCents()!=cents||
                        allocatedByRefund.getOrDefault(r.getId(),0L)!=r.getAmountCents()||!Objects.equals(e.getVenueId(),r.getVenueId())||!Objects.equals(e.getMemberId(),r.getMemberId())||
                        !Objects.equals(a.getOrderId(),p.getOrderId())||!Objects.equals(f.getOrderId(),p.getOrderId())||
                        !Objects.equals(f.getEntitlementId(),r.getEntitlementId())||!Objects.equals(f.getVenueId(),o.getVenueId())||
                        !Objects.equals(r.getVenueId(),o.getVenueId())) state="LINK_INCONSISTENT";
                }
                integrity.put(p.getId(),state);
                if(!"LINKED".equals(state)) differences.add(new Difference("REFUND_ALLOCATION","PAYMENT_TRANSACTION",p.getId(),null,p.getOrderId(),p.getId(),null,null,Money.yuan(cents),
                    state.equals("LEGACY_OR_UNLINKED")?"LEGACY_OR_UNLINKED：历史或未关联退款现金，未建立价值分摊":"LINK_INCONSISTENT：退款现金的现有分摊关联不一致"));
            }
            long[] channel=totals.computeIfAbsent(channel(p.getPaymentMethod()),k->new long[2]);
            if(receipt){receipts=Math.addExact(receipts,cents);channel[0]=Math.addExact(channel[0],cents);}
            else {refundCash=Math.addExact(refundCash,cents);channel[1]=Math.addExact(channel[1],cents);}
        }
        List<Channel> channels=totals.entrySet().stream().map(e->new Channel(e.getKey(),Money.yuan(e.getValue()[0]),Money.yuan(e.getValue()[1]),Money.yuan(e.getValue()[0]-e.getValue()[1]))).toList();
        return new Cash(receipts,refundCash,channels,differences,raw,raw,integrity);
    }
    private Valuation serviceConsumed(Range range,Long venue,List<com.gym.fitness.module.commercial.entity.MemberEntitlementEntity> entitlementRows,Map<Long,EntitlementValueAccountEntity> accountByEntitlement){List<EntitlementValueEventEntity> rows=venue==null?events.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByIdAsc(range.from(),range.until()):events.findByVenueIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByIdAsc(venue,range.from(),range.until());LocalDate valuationDate=LocalDate.now(ZoneId.of("Asia/Shanghai"));long total=0,unposted=0;for(var e:rows)if(e.isVerified()&&Set.of("CONSUME","TIME_CONSUME","REVERSE").contains(e.getEventType()))total=Math.addExact(total,-e.getDeltaCents());List<Long> ids=entitlementRows.stream().map(com.gym.fitness.module.commercial.entity.MemberEntitlementEntity::getId).toList();if(ids.isEmpty())return new Valuation(total,0,valuationDate);Map<Long,com.gym.fitness.module.commercial.entity.MemberEntitlementEntity> entitlementById=entitlementRows.stream().collect(Collectors.toMap(com.gym.fitness.module.commercial.entity.MemberEntitlementEntity::getId,e->e));Map<Long,EntitlementFundingEntity> sourceById=funding.findByEntitlementIdIn(ids).stream().collect(Collectors.toMap(EntitlementFundingEntity::getId,f->f));for(var p:periods.findByEntitlementIdInOrderByEntitlementIdAscIdAsc(ids)){var e=entitlementById.get(p.getEntitlementId());var a=accountByEntitlement.get(p.getEntitlementId());var f=sourceById.get(p.getFundingId());if(e==null||a==null||f==null||e.getRemainingUses()!=null||a.getPausedAt()!=null||a.getActiveRefundId()!=null||!"VERIFIED".equals(a.getVerificationStatus())||"REFUNDED".equals(e.getStatus()))continue;int days=(int)Math.min(p.getTotalDays()-p.getConsumedDays(),Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(p.getNextServiceDate(),valuationDate)));long available=f.getRemainingCents();for(int i=0;i<days;i++){int consumed=p.getConsumedDays()+i+1;long cents=p.getTotalCents()/p.getTotalDays();if(consumed==p.getTotalDays())cents+=p.getTotalCents()%p.getTotalDays();LocalDate serviceDate=p.getNextServiceDate().plusDays(i);if(available<cents)break;available-=cents;unposted=Math.addExact(unposted,cents);if(!serviceDate.isBefore(range.from().toLocalDate())&&serviceDate.isBefore(range.until().toLocalDate()))total=Math.addExact(total,cents);}}return new Valuation(total,unposted,valuationDate);}
    private static Difference orderDifference(Long id,long stored,long actual,String message){return new Difference("ORDER_PAYMENTS","ORDER",id,null,id,null,null,Money.yuan(stored),Money.yuan(actual),message);}
    private static Difference refundDifference(Long id,long stored,long actual,String message){return new Difference("PAID_REFUND","REFUND",id,null,null,null,id,Money.yuan(stored),Money.yuan(actual),message);}
    private static long sum(List<PaymentTransactionEntity> rows,String type){return rows==null?0:rows.stream().filter(p->type.equals(p.getTransactionType())).mapToLong(p->Money.cents(p.getAmount())).sum();} private static Difference diff(String type,Long entitlementId,long account,long event,String message){return new Difference(type,entitlementId,Money.yuan(account),Money.yuan(event),message);} private Long scope(){return SecurityUtil.isSuperAdmin()?null:SecurityUtil.getCurrentOrganizationId();} private Range range(LocalDate from,LocalDate to){if(from==null||to==null||from.isAfter(to)||from.plusDays(MAX_DAYS).isBefore(to))throw fail("日期范围非法或超过366天");return new Range(from.atStartOfDay(),to.plusDays(1).atStartOfDay());} private static String channel(String value){return value==null||value.trim().isEmpty()?"UNKNOWN":value.trim();}     // ---------------------------------------------------------------- 导出用中文文案
    // 与前端界面用词保持一致，避免同一份数据在两处叫法不同。

    /** 交易类型：PAYMENT=收款、REFUND=退款。 */
    private static String typeText(String type) {
        if (type == null || type.isBlank()) return "";
        return switch (type.trim().toUpperCase()) {
            case "PAYMENT" -> "收款";
            case "REFUND" -> "退款";
            default -> type.trim();
        };
    }

    /** 支付渠道中文名。 */
    private static String channelText(String value) {
        String code = channel(value);
        return switch (code.toUpperCase()) {
            case "WECHAT" -> "微信";
            case "ALIPAY" -> "支付宝";
            case "CARD" -> "银行卡";
            case "CASH" -> "现金";
            case "SELF_SERVICE" -> "会员自助";
            case "UNKNOWN" -> "未知";
            default -> code;
        };
    }

    /** 对账状态：无差异记录时是 NOT_APPLICABLE，其余情况本身就是中文说明。 */
    private static String reconciliationText(String value) {
        if (value == null || value.isBlank()) return "不适用";
        return "NOT_APPLICABLE".equalsIgnoreCase(value.trim()) ? "不适用" : value;
    }

    /** 时间写成 yyyy-MM-dd HH:mm:ss，Excel/WPS 能直接按日期排序筛选。 */
    private static String stampText(java.time.LocalDateTime time) {
        return time == null ? "" : time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

private static BusinessException fail(String message){return new BusinessException(ErrorCode.PARAM_ERROR,message);} private record Range(LocalDateTime from,LocalDateTime until){} private record Cash(long receipts,long refunds,List<Channel> channels,List<Difference> differences,List<PaymentTransactionEntity> rawRows,List<PaymentTransactionEntity> rows,Map<Long,String> integrity){} private record Valuation(long consumed,long unposted,LocalDate date){}
    private List<Difference> refundEntitlementAndReuse(Long venue){
        List<RefundApplicationEntity> paid=(venue==null?refunds.findAllByOrderByIdDesc():refunds.findByVenueIdOrderByIdDesc(venue)).stream().filter(r->"PAID".equals(r.getStatus())).toList();
        if(paid.isEmpty())return List.of();
        List<RefundAllocationEntity> rows=allocations.findByRefundIdIn(paid.stream().map(RefundApplicationEntity::getId).toList());
        Map<Long,List<RefundAllocationEntity>> byRefund=rows.stream().collect(Collectors.groupingBy(RefundAllocationEntity::getRefundId));
        Map<Long,com.gym.fitness.module.commercial.entity.MemberEntitlementEntity> entitlementById=entitlements.findAllById(paid.stream().map(RefundApplicationEntity::getEntitlementId).distinct().toList()).stream().collect(Collectors.toMap(com.gym.fitness.module.commercial.entity.MemberEntitlementEntity::getId,e->e));
        Map<Long,Long> paymentUses=rows.stream().filter(a->a.getPaymentTransactionId()!=null).collect(Collectors.groupingBy(RefundAllocationEntity::getPaymentTransactionId,Collectors.counting()));
        Set<Long> paymentIds=paymentUses.keySet();
        Set<Long> existingPayments=payments.findAllById(paymentIds).stream().map(PaymentTransactionEntity::getId).collect(Collectors.toSet());
        List<Difference> result=new ArrayList<>();
        for(var refund:paid){var entitlement=entitlementById.get(refund.getEntitlementId());List<RefundAllocationEntity> allocationsForRefund=byRefund.getOrDefault(refund.getId(),List.of());boolean bad=entitlement==null||!Objects.equals(entitlement.getVenueId(),refund.getVenueId())||!Objects.equals(entitlement.getMemberId(),refund.getMemberId());for(var allocation:allocationsForRefund){Long paymentId=allocation.getPaymentTransactionId();if(paymentId==null||!existingPayments.contains(paymentId)||paymentUses.getOrDefault(paymentId,0L)!=1L)bad=true;}if(bad)result.add(refundDifference(refund.getId(),refund.getAmountCents(),allocationsForRefund.stream().mapToLong(RefundAllocationEntity::getAmountCents).sum(),"退款权益归属或退款流水复用不一致"));}
        return result;
    }
}
