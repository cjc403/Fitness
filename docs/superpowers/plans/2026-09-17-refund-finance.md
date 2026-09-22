# Refund Finance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付剩余服务价值退款审批、精确余额和按实际流水时间统计的财务对账，验证后更新云服务器。

**Architecture:** commercial 和 training 保留现有职责，调用独立 finance 价值服务；退款生命周期服务负责审批与登记，不连接真实支付网关。finance 报表服务使用同一范围查询输出报表与 CSV。新增 V9，旧权益待核对，原历史不改写。

**Tech Stack:** Java17 / SpringBoot3.3.13 / JPA / PostgreSQL18 / Flyway / Vue3 / TypeScript / ElementPlus。

**Spec:** docs/superpowers/specs/2026-09-17-refund-accounting-design.md（用户已批准第一批）。

## Global Constraints

- 第一批交付：剩余价值核算、退款申请/审核/支付登记、流水对账与导出。第二批教练已审核月结调整单不在本批。
- 金额以整数“分”作为内部余额和分摊单位；API 展示人民币两位小数。
- 新增 Flyway V9，仅加字段和新表；V1–V8 校验和不变。
- 已有账号、订单、会员、消费快照和权益次数不修改；不擅自修改账号密码或生产密钥。
- 旧退款接口不能绕过审批；默认退款手续费与违约扣款均为 0；退款终止整个剩余权益。
- 操作人和访问范围由服务端会话确定；前台不得审核/支付，教练不得读会员财务，会员仅看自己脱敏进度。
- 门店 → 排课 → 教练（必要时）→ 权益 → 价值账/申请/资金来源/订单；财务代码不反向获得教练锁。
- 生产只验登录、角色权限、只读数据与资源，不对真实会员做消费/退款验收。
- 本项目非 Git 仓库；不初始化、不提交。源码备份与文件差异包代替 Git 工作树/提交/审查脚本；保留过程记录，不删除唯一记录。
- 使用 apply_patch 编辑。密码/API Key/令牌不进入源码、报告和日志。付费 AI 在测试中禁用。

## File structure and contracts

后端根目录 `fitness-backend/src/main/java/com/gym/fitness/`。
新增 `module/finance/{entity,repository,dto,service,controller}`，entity 每表一类，service 拆 `EntitlementValueService`、`RefundService`、`FinanceReportService`；金额纯函数 `Money` 与 `FundingAllocator` 单独类。
新增七张表（设计第6节）；价值事件可额外保存来源分摊 JSON 快照，用于精确消费冲正。时间服务期每个资金来源独立保存金额/总日数/已消费日数/下一服务日，不能通过当前标价重算。
所有 JSON 使用现有 `Result` 包装（code=0 成功）；接口前缀 `/api/v1/finance`，前端 request 使用 `/v1/finance`。

协议（record 字段可加，不可无记录重命名；前端交付前核对实际 DTO）：

```text
GET /entitlements/{id}/quote -> entitlementId, verificationStatus, productType,
  refundableAmount, remainingUses, remainingServiceDays, version,
  blockedReasons:string[], activeRefundId?, funding:[orderId,orderNo,unusedAmount]
POST /entitlements/{id}/reconcile -> {version,remainingUses,reason,
  sources:[{orderId,unusedAmount}],transferEvidence?}; 返回 quote
POST /entitlements/{id}/verify-transfer -> {version,reason,transferEvidence};
  HQ仅核实本次转卡退款受领依据，不改余额/来源/次数；返回 quote
POST /refunds -> {entitlementId,version,reason,idempotencyKey}; 返回 Refund
GET /refunds -> Refund[]（按会话范围）
GET /me/refunds -> MemberRefund[]（本人，独立 DTO）
POST /refunds/{id}/approve|reject|cancel -> {reason}; 返回 Refund
POST /refunds/{id}/pay -> {amount,channel,externalReference,occurredAt,note}; 返回 Refund
Refund: id,refundNo,entitlementId,entitlementNo,memberId,memberName,venueId,
  status,amount,reason,requestedBy,requestedAt,approvedBy?,approvedAt?,paidAt?,
  decisionReason?,allocations:[orderId,orderNo,amount],audit:[action,reason,occurredAt]
MemberRefund: id,refundNo,entitlementNo,status,amount,requestedAt,approvedAt?,paidAt?
GET /report?from=YYYY-MM-DD&to=YYYY-MM-DD -> from,to,receipts,refunds,netCash,
  unusedVerifiedValue,unverifiedEntitlements,serviceConsumed,channels:[],differences:[]
GET /report/export?from=...&to=... -> UTF8 CSV，使用相同查询范围和口径
```

### Task 1: 价值账、业务挂钩和退款生命周期

**Files:**
- Create: finance 金额/分摊类、7实体与仓库、`FinanceDtos.java`、`EntitlementValueService.java`、`RefundService.java`、`FinanceController.java`。
- Create: `fitness-backend/src/main/resources/db/migration/V9__refund_value_accounting.sql`。
- Modify: `module/commercial/service/CommercialService.java`、`module/training/service/TrainingService.java`、预约/会员预约服务、`security/RolePermissionCatalog.java`。
- Test: `fitness-backend/src/test/java/com/gym/fitness/RefundFinanceIntegrationTest.java`、金额分摊单测。

**Interfaces:** Produces 上述 quote/reconcile/refunds API 与字段；价值服务提供开卡/续费/冻结/解冻/转让检查、余额刷新、消费与按原事件冲正、退款办理检查。调用端持有门店/权益锁后进入价值服务；锁刷新必须读取数据库最新状态。报告任务消费价值实体仓库，不自行重新算余额。

- [ ] Step 1：写真实 HTTP 集成测试，先证实新路由缺失与旧绕过行为，独立金额 fixture。

```java
@Test void oldRefundCannotBypassApproval() throws Exception {
    var r=postJson("/api/v1/commercial/entitlements/"+entitlementId+"/refund",
      "admin",Map.of("amount",10,"paymentMethod","CASH","reason","退剩余"));
    assertNotEquals(0,r.path("code").asInt());
    assertEquals(new BigDecimal("0.00"),orders.findById(orderId).orElseThrow().getRefundedAmount().setScale(2));
}
// 10元/3次：实际消费依次3.33、3.34、3.33；合计10.00、余额0。
// 配置10元/3次销售，真实扫描IN三次；检查价值事件和权益次数。
```

- [ ] Step 2：运行 `mvn -Dtest=RefundFinanceIntegrationTest test -q`，记录 RED 的业务断言；不能用编译失败代替行为证据。
- [ ] Step 3：实现精确金额纯函数、按资金剩余比例最大余数分摊，再补仓库和服务。核心金额规则：

```java
static long cents(BigDecimal amount) {
  if(amount==null||amount.signum()<0) throw new IllegalArgumentException("非法金额");
  long cents=amount.movePointRight(2).longValueExact();
  if(cents>999999999999L) throw new IllegalArgumentException("金额超限");
  return cents;
}
static long consume(long balance,int remaining) {
  if(remaining<=0) throw new IllegalArgumentException("无剩余次数");
  return remaining==1?balance:BigDecimal.valueOf(balance)
    .divide(BigDecimal.valueOf(remaining),0,RoundingMode.HALF_UP).longValueExact();
}
```

精确消费事件存每个来源扣分；逆操作只恢复该分摊，不能用当前平均。时间卡以 Asia/Shanghai today 之前的完整服务日消费、当天可退；每期按分均摊、最后日收尾。冻结/退款期间暂停，解冻/撤回只顺延完整暂停日。
旧记录不存在价值账时懒创建 UNVERIFIED，V9 给真实已有记录标记同样状态；测试 create-drop/demo 也不会假设历史已核对。未核对正常服务保留旧价快照，同时追加不可当作已核实金额的事件。核对请求锁后比较 version/remainingUses，来源净实收上限、唯一归属、同门店会员、历史续费与退款归属、转卡 HQ 凭证依据。不确定阻止核对，不靠备注猜关联。
新续费给 UNVERIFIED 增加明确资金记录仍不整体自动核实；核对须合并已明确来源而非重复分配。商品类型固定 snapshot，后续商品编辑不能改原资金种类。历史新来源须明确人工确认类型而非默认取现价商品类型；时间剩余服务期须符合可证实权益区间，不能为普通已过期卡编造未来天数。次卡剩余次数0时不能核对正数未使用余额。已VERIFIED后转卡不废弃精确价值账，HQ通过独立verify-transfer记录持有人/原凭证/受领依据与原因，余额不改，每次后续转卡清除该核实依据。
PENDING/APPROVED 唯一有效单：可用 `active_entitlement_id` 仅活动状态非空、唯一约束，避免 H2 与 PG 部分索引差异；解除时设空，审计保留。外部渠道+凭证唯一。幂等键范围至少会话申请人+键，重复键不同内容拒绝。退款资金分摊先锁来源与订单再核对，不超退款总上限。
申请检查本人全部未完成服务预约（含团课）和待签/预占；预约新建（含团课）也检查会员退款中的相关权益，不使提交后出现新预约。批准不能自审；撤回 pending 本申请人，approved 仅HQ带原因；支付仅HQ/manager，需要 amount 精确匹配、时间不得未来、不改变已付原凭证；PAID 重试只有相同凭证相同金额返回原结果。
旧退款 API 返回明确业务错误，不写钱。旧测试针对该入口的成功预期只改为“拒绝且无写入”，不删除原测试的其他商业断言。
- [ ] Step 4：增加消费后续费/冲正、来源多单、冻结未来期、legacy一次核对、转卡依据、办理锁、无自审、无审核人、审核后撤回、零/小数/溢出、并发申请/登记、跨门店/会员脱敏测试；焦点测试 GREEN 后一次完整 `mvn test package -q`，原27项除旧入口有意变更的断言全部仍运行。
- [ ] Step 5：自查并写 task-1-report，包含 RED/GREEN 命令/日志、实际 DTO/hooks 供后续任务；源码与报告纳入差异审查包。非 Git 无提交。

### Task 2: 按流水日期的对账和 CSV

**Files:** Create `FinanceReportService.java`、`FinanceReportController.java`、报表 DTO/CSV helper、`FinanceReportIntegrationTest.java`；Modify 支付仓库与 CommercialService.overview（共享现金查询）。

**Interfaces:** consumes Task1 已核对价值账户/事件/退款分摊，produces report/export API。receipts/refunds/netCash 仅 SUCCESS PAYMENT/REFUND，occurredAt ∈ [from当天00:00,to次日00:00)。会话门店范围由订单venueId关联确定，channel 空缺显示UNKNOWN；不猜未知余额。

- [ ] Step1：写跨月真实流水 fixture：8月收100、9月退30，则9月 receipts=0/refunds=30/net=-30；旧订单月份不影响结果。另插入门店2款50，店长门店1不可见。

```java
assertEquals("-30.00", report.path("netCash").decimalValue().setScale(2).toPlainString());
assertTrue(csv.contains("'=HYPERLINK")); // 实际文本渠道起始公式，CSV单元格转义
```

- [ ] Step2：跑报表测试证实 RED；按协议新增查询和聚合。
- [ ] Step3：现金、核实余额、未核对数量、期间消费额单列。对账明列订单金额与所有历史支付流水、已核实账户余额与事件、资金来源和账户余额、已PAID申请与分摊/订单/关联支付的差异。未知金额用未核对提示而非0掩盖。查询不得改变业务数据；时间价值采用只读预估或独立短写刷新后只读查询并说明，避免报表自动退款。
CSV 最多10000流水行，超限业务错误缩短范围；from>to/跨度过大非法。转义代码：

```java
String cell(String s) {
  if(s==null)s="";
  if(!s.isEmpty()&&(s.charAt(0)<=32||"=+-@".indexOf(s.charAt(0))>=0))s="'"+s;
  return "\""+s.replace("\"","\"\"")+"\"";
}
```

- [ ] Step4：报告/渠道/差异/CSV内容及权限 tests GREEN，完整后端打包；写 task2report。
- [ ] Step5：审查差异，禁止扩大成会计总账或自动修账；保留旧流水查阅。

### Task 3: 五角色界面闭环

**Files:** Create `fitness-frontend/src/api/modules/finance.ts`、`src/components/RefundPanel.vue`、`src/components/FinanceReportPanel.vue`；Modify `src/views/admin/CommercialCenter.vue`、`src/views/member/MemberHome.vue`、`src/utils/request.ts`（CSV Blob 分支，保持现有401刷新/403节流/自动退出）。

**Interfaces:** consumes Task1/2 actual DTO/API; permissions 由 auth 返回，按钮不能以角色字符串代替后端授权。无需新增财务路由，CommercialCenter 新退款审批/对账 tabs，原卡“退款”进入 quote dialog；会员页只展示本人退款状态不出现申请按钮。教练不增加财务入口。

- [ ] Step1：运行基线构建，浏览器原卡退款入口观察旧逻辑；在纯展示金额/幂等键交互逻辑写可执行单测（项目若无前端测试依赖使用node原生test与纯js helper），独立期待值证明money文本不会计算可退金额。
- [ ] Step2：添加 typed request。每次用户新申请 UUID 幂等键，超时重试保留同键；金额预览由API，不前端决定。balance 未核对显示待核对并提供HQ/manager核对dialog：选择来源订单、每源未使用金额/原因/转卡依据，带quoteversion+uses提交；不默认用原价替用户核对。
- [ ] Step3：退款列表显示状态/金额/范围/时间/审计。批准/拒绝/撤回必须原因，支付登记明确“线下已完成后登记，不会真实打款”、渠道/凭证/amount/occurredAt；已登记禁二次操作。无自审按钮，后台仍校验。application pending card明显提示，冻结/续费/转卡受阻原因可见。
- [ ] Step4：报表日期、渠道、净现金/余额/未核对/消费与差异表、CSV下载（blob通过现有request拦截器保留401刷新/业务错误处理）。列表错误与重试入口，不把失败展示成空成功。所有异步按钮loading防双击，弹窗失败保持输入允许重试。
- [ ] Step5：`npm run build` 类型检查/构建通过；测试浏览器员工申请、另人审批、支付提示、会员只读、无权限入口、故障提示。写task3report和用户使用文档。

### Task 4: PostgreSQL 实验、审查与发布

**Files:** Create 固定版本 finance stage/release/acceptance 脚本于 `deploy/linux`、`docs/REFUND_FINANCE.md`、`docs/REFUND_FINANCE_VERIFICATION_20260917.md`；Modify README/deployREADME。

- [ ] Step1：复制上一版部署流程但使用全新固定 finance DB/目录/服务名，stage禁生产写。创建 protected 随机test凭证，仅stdin/临时文件流转；生产凭证文件不输出。克隆prod DB验证V9，开启isolatedstage仅127.0.0.1:18080/19091。
- [ ] Step2：在PG验证真实迁移/unique/FK/同权益并发申请/重复支付/来源分摊/用户范围和审核流程。mutableacceptance必须精确stageDB+port guard；不得对生产创建订单或申请。生产与旧迁移校验和/业务行数/用户指纹比对保留。
- [ ] Step3：全局代码审查整改，再新跑 backend test package、frontend build，sha256制品上传，stage针对最终制品再次验收，并写与摘要绑定的STAGING_ACCEPTED。
- [ ] Step4：发布脚本验证hash/nginxconfig/PG健康；备份prod.dump/旧jar/旧前端path；临时停watchdog、切换、健康检查、V9success/原数据指纹/真实5角色登录/只读新API/publicSPA验收；异常trap恢复旧应用并恢复watchdog，不自动覆盖生产数据库。
- [ ] Step5：关闭stage/tunnel/browser/testroleLOGIN，清除临时密码文件，生产healthcheck和timers最终检查；记录证据/回滚位置/限制，简洁交付可使用功能和网址。

## Preflight self-review

设计3–5节由Task1覆盖；6/7由Task1锁/约束与Task4 PG覆盖；8由Task2覆盖；9由Task1–4所有验收覆盖。Task1→2共享价值DTO/实体，Task2→3共享报表协议，Task1→3共享退款协议，均按完成报告校对，不并行改共享文件。旧 refund 成功测试的预期与新强制审批冲突，按批准设计调整为不能绕过，仍保留其余商业测试。本批明确没有教练已审核结算调整功能。
