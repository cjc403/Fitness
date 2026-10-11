# 第3项：前台交班与日结设计

## 用户目标与执行范围

用户已批准新功能1—5，完成1续费回访、2潜客体验后要求开始下一项。原第3项为“前台交班与日结”：班次、实际收款核对、差异说明、交班确认及店长复核。2026-10-10用户选择**按门店统一班次，每店同时一个班次，核对本店全部收退款**，随后要求以后直接执行推荐方案。本轮据此直接实现，并持续本地开发、仅验桌面、保留所有未提交旧改动。不部署、提交、推送、重置账号或向实际业务库写测试数据。

选择增量交班子模块，复用payment_transaction和SalesOrder的门店归属；不改既有支付/退款、权益、财务报表及V1—V20。逐笔归班比只取时间窗口更可靠：退款允许补录过去的occurred_at，单纯按发生时间会遗漏。每店独立流水边界、渠道实核和审计快照；界面沿用WorkspaceHero/现有动漫主题。

## 班次与流水边界

- 新入口“交班与日结” `/cash-shifts`；RECEPTION、MANAGER、SUPER_ADMIN可读写，只有MANAGER/SUPER_ADMIN可复核。前台/店长限当前本店，总部可以跨店或选店；总部未选具体店可看日汇总，但不可开班。
- OPEN → SUBMITTED → APPROVED或RETURNED；RETURNED修正后再次SUBMITTED。每店最多一个OPEN，提交即关闭，后续新班不等待店长复核。无删除、撤销审核或自动生成收退款功能。
- 开班无需资金写入；记录门店、营业日（当前上海开班日期）、操作者、备注及开班时间。只有status=ACTIVE的真实门店可开新班；存量已停业门店的历史班次仍可处理。
- 第一次开班，在同一门店锁内记录**本店现有流水最大ID作为基线**。首次开班前的历史流水不自动追溯归班，不修改原记录，原财务报表继续可查。界面清楚显示“首班从开班后的新登记流水开始”。
- 后续开班沿用上一关闭班的cutoffPaymentId，包含上一交班后、这次开班前的新登记流水。开班同时记录openingPaymentId；基线之后且不大于openingPaymentId的候选标为carriedOver，明确显示“含开班前待交班流水”，实际核对也包括这些流水。
- 同门店成功PAYMENT/REFUND流水，按ID升序，baselinePaymentId < id ≤ cutoffPaymentId归班；按订单venueId归属，不按操作者归属或会员当前所在店过滤。发生时间仅展示。所有顾问/前台/会员自助渠道登记在该门店的成功收退款均可归集，不把权益消费/优惠抵扣当资金流水。
- 开班/交班与现有销售/续费/退款复用VenueMutationLock，保证边界取得时同店资金事务已完成；收款链已有member→venue锁序，交班只锁actor→venue→shift，不再拿member/order/entitlement锁，避免反向死锁。PostgreSQL锁采用现有FOR NO KEY UPDATE，兼容FK KEY SHARE。
- 提交前GET详情给出本次候选的ledgerFingerprint（ID、订单ID、类型、渠道、金额及发生时间的确定性SHA256）。提交时锁内重查，若流水已变化则拒绝并提示重新核对，保持OPEN。同内容成功重试在版本、状态、时间检查前重放，并重新验证权限。
- 提交时持久保存逐笔快照和渠道系统合计，结束边界固定。以后新增/补录流水归下一班；退回复核仅修正实际核对数/说明，不重新捕获资金快照，不动任何订单/退款。修改实际数的前后值保存在追加审计中。

## 实际核对与店长复核

- 每个渠道独立填写**实际收款、实际退款**，单位元，非负、最多2位小数、单值最多9999999999.99。整数分计算全部汇总；退款可大于收款，净额/差额允许负值。现金也按实际收退款合计登记，本期不新增备用金、现金送存或钱箱余额模型。
- 必须包含CASH、WECHAT、ALIPAY、CARD四行，另加入流水中出现的其他渠道（如SELF_SERVICE、UNKNOWN）。渠道名规范为原值trim，空值为UNKNOWN，与原财务报表一致；不得漏行、重复、添加不属于当前范围的渠道，不能由用户修改系统金额。
- 实际值全部要求明确输入（允许0），不能自动预填系统金额当作已点验。SELF_SERVICE标签说明现有自助购买为登记、未接支付；据实际情况填写核对数。
- receiptsDifference = actualReceipts − systemReceipts；refundsDifference = actualRefunds − systemRefunds；netDifference = actualNet − systemNet。只要任一渠道收款或退款有差异，即使净额或跨渠道差额相抵为0，也必须填写differenceReason（1—1000字）。handoverNote可空、最多1000字。
- OPEN由开班者提交；店长/总部可代交班，审计记录实际提交人。RETURNED可由原开班者、上一提交人或本店管理者修正。普通前台不可修改别人的班。
- 管理者可APPROVE或RETURN（退回必须1—1000字原因）；不能审核自己最后提交的班，防止同人自交自审。审核必须expectedVersion和请求键；无权角色即使知道ID或持有额外权限也不能进入此流程。
- 冻结系统金额、逐笔快照及关闭时间；退回/再提交/复核每次追加事件。审批不创建付款、退款、权益、财务流水，不冒充银行已到账。

## 日结与读取范围

- businessDate按**班次开班营业日**，跨午夜关闭仍归开班日；这与原财务报表按occurred_at发生日统计不同，页上说明。允许选择历史日期查看，不能回填过去的开班营业日。
- 日汇总包含当日已交班（SUBMITTED/RETURNED/APPROVED）唯一班次一次：系统收款、退款、净额、实际收款/退款/净额、渠道差异，以及OPEN/待审核/退回/通过计数。另给approvedNet用于已复核日结；待审/退回未冒充复核完成。开班班次只显示实时预览，不入已交班合计。
- 列表按当前可见范围、营业日、状态筛选，分页默认20/最多100；状态筛选和分页不改变日汇总分母。只读查询先按店过滤，不整库加载再过滤；用户/门店显示名用标量投影避免N+1。
- 具体门店选中时currentShift独立返回该店任何日期的OPEN班，避免隔日遗忘旧班；首班历史排除数、待归班数、carriedOver数在预览中可见。总部全店视图无currentShift，选店才可开班。
- 详情含shift、preview、channels、audit、payments第一页最多100条及paymentTotal；单独分页读取 `/id/payments` 避免大型弹窗一次加载全部流水。审计只含本流程人员显示名、动作、说明、前后核对值和时间，无会员手机号或账号敏感字段。

## 数据与接口契约

新增V21，四张表cash_shift、cash_shift_channel、cash_shift_payment、cash_shift_event；已有迁移字节不变。ID及关联采用BIGINT，金额用BIGINT分，上海TIMESTAMP(6)时间；OPEN active_venue_id=venue_id且UNIQUE，其他状态active_venue_id=NULL。事件(actor_id,request_key)唯一，渠道(shift_id,channel)唯一，逐笔payment_transaction_id全局唯一且保留原ID。索引覆盖venue/business_date/status与shift_id。版本由服务端每次成功动作+1。

后端基础 `/api/v1/cash-shifts`，前端 `/v1/cash-shifts`，Result包装：

| 方法/路径 | 输入 | data |
|---|---|---|
| GET /workbench | venueId?、businessDate?（默认上海今天）、status=ALL、page=1、size=20 | Workbench |
| GET /{id} | 无 | Detail |
| GET /{id}/payments | page=1、size=100 | PaymentPage（关闭班的固定快照） |
| POST / | OpenRequest | Detail |
| POST /{id}/submit | SubmitRequest | Detail |
| POST /{id}/review | ReviewRequest | Detail |

OpenRequest = {venueId?:number, note?:string, idempotencyKey:string(1—100)}。
SubmitRequest = {expectedVersion:number, ledgerFingerprint:string64, channels:[{channel:string, actualReceipts:number, actualRefunds:number}], differenceReason?:string, handoverNote?:string, idempotencyKey:string}。
ReviewRequest = {expectedVersion:number, decision:'APPROVE'|'RETURN', reason?:string, idempotencyKey:string}。

Workbench = {businessDate:string, venues:[{id,name}], selectedVenueId:number|null, currentShift:Shift|null, summary:DailySummary, channels:Channel[], shifts:{records:Shift[],total,page,size}}。
DailySummary = {shiftCount,openCount,pendingReviewCount,returnedCount,approvedCount,systemReceipts,systemRefunds,systemNet,actualReceipts,actualRefunds,actualNet,netDifference,approvedNet}（金额为元，其余整数）。
Shift = {id,shiftNo,venueId,venueName,businessDate,status,version,openedBy,openedName,openedAt,submittedBy:number|null,submittedName:string|null,closedAt:string|null,reviewedBy:number|null,reviewedName:string|null,reviewedAt:string|null,reviewReason:string|null,note:string|null,differenceReason:string|null,handoverNote:string|null,baselinePaymentId,openingPaymentId,cutoffPaymentId:number|null,ledgerFingerprint:string|null,systemReceipts,systemRefunds,systemNet,actualReceipts:number|null,actualRefunds:number|null,actualNet:number|null,netDifference:number|null}。
Channel = {channel,systemReceipts,systemRefunds,systemNet,actualReceipts:number|null,actualRefunds:number|null,actualNet:number|null,receiptsDifference:number|null,refundsDifference:number|null,netDifference:number|null}。
Detail = {shift:Shift, preview:{ledgerFingerprint,paymentCount,carriedOverCount,historicalExcludedCount,channels:Channel[],systemReceipts,systemRefunds,systemNet},channels:Channel[],audit:Audit[],payments:Payment[],paymentTotal:number}；OPEN使用实时preview/channels，关闭后使用固定快照，returned仍固定。
Payment = {id（快照id或OPEN原交易id）,paymentTransactionId,transactionNo,orderId,transactionType,channel,amount,occurredAt:string|null,operatorId:number|null,carriedOver:boolean}。
Audit = {id,action:'OPEN'|'SUBMIT'|'RESUBMIT'|'APPROVE'|'RETURN',actorName,content:string|null,changesJson:string|null,createdAt}。
PaymentPage = {records:Payment[],total,page,size}。workbench返回当前权限内完整venues，选店不会缩掉总部下次可选门店。

错误沿用1001参数、1003禁止、1004不存在、2600操作/版本/流水变化冲突；异常不暴露SQL、令牌或私密数据。按真实权限/会话校验后重放幂等，不准旧操作者降权/跨店后取回资料。

## 桌面界面与验证

布局：动漫欢迎区+门店/营业日+开班操作；日结统计卡；当前班预览及交班按钮；渠道汇总；分页班次表。详情对话框含系统/实核对比、逐笔分页、追加审计；提交表单明确空白实际金额、差异说明、流水变化刷新；退回班可修正，管理者显示复核按钮。普通前台与自己提交的管理者不显示审核入口，服务端同时拦截。

写入锁定目标/payload，安全随机请求键；失败同内容重试复用键，编辑后新键；版本/流水冲突停用保存并提供重新载入；会话/权限范围变化立即清空已加载财务数据、表单和请求结果，卸载后不刷新/发消息。日期按上海纯营业日和本地时间展示，金额由输入/预览整数分差额提示，不替代服务端校验。

验证：真实HTTP集成TDD覆盖收退款、负净额、抵消差异、未知渠道、空班、归店/角色、首班基线/班外新登记/补录旧发生时间、幂等与版本、提交中新增流水、冻结快照/退回修正审计、不能自审、不同操作者并发开班/同班提交、真实销售或退款与交班的锁屏障。H2全量构建、隔离PG18.6专项与V20→V21克隆迁移比对；前端SFC运行测试、构建、桌面前台/店长/总部浏览器。测试写入仅隔离PG55449/应用8189；真实5433仅备份、迁移、启用及读验收。验证通过再安全启用本地制品，关闭本轮临时服务，更新共享交接文档§0/§10。
