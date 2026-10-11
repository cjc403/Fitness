# 潜客与独立体验预约：后端独立审查

日期：2026-10-09。范围：当前未提交文件内容（非仅 HEAD diff），包括 lead 模块、V20、LeadIntegrationTest、权限目录及 CommercialService 操作者锁。依据：项目 AGENTS、已批准设计和实施计划。审查过程仅阅读源码，未运行构建、未修改测试数据库、未触碰产品代码；实现与验证仍并行进行，因此本报告不是运行验收通过声明。

## 结论

未发现 Critical 级问题。核心访问检查、请求重放次序、事务回滚、阶段约束和退款净额统计的代码路径与批准设计一致。发现两项应修正的查询范围/查询量问题，以及一项总部跨店查询量测试缺口。建议修复并补针对性回归后再进行最终验收。

## Important / P2

### 1. 门店选项接口加载全平台账户后才筛门店

- 位置：`fitness-backend/src/main/java/com/gym/fitness/module/lead/service/LeadService.java:81-85`，尤其第 82 行。
- 证据：`venues.findAll()`、`users.findAll()`、`products.findAll()` 均先加载全部实体，再用 stream 限定 venue/status/role。`sys_user` 包含会员登录账号，并非仅顾问。
- 触发：任一普通顾问或店长请求 `/api/v1/leads/options`；即使本店仅一名顾问，也加载其他门店全部账户、商品、门店。
- 影响：选项接口的内存/延时随全平台账户数增长；返回内容目前仍正确过滤，未认定响应越权泄露。该实现不满足本轮避免全门店加载的要求。
- 修复建议：数据库查询直接限定当前门店、ACTIVE、SALES；使用 id/name/venueId 投影，产品查询限定在售及门店。总部无门店筛选时可查其允许的选项范围，但也不应读取会员账户。
- 回归：本店少量顾问 + 外店大量顾问/会员账号，清空一级缓存后请求 options；断言只载入本店需要的选项、外店实体无加载，返回不含手机号等信息。

### 2. 开卡候选对本店历史订单逐笔查询归因与会员

- 位置：同文件第 95-98 行，以及 `qualifies` 第 212-214 行（以复核时内容为准）。
- 证据：读取本店全部历史订单后调用 `qualifies`；满足基本状态和支付时间检查的每笔订单继续调用 `existsByConvertedOrderId` 和 `members.findById`。手机号匹配发生在这些查询之后。
- 触发：潜客留存一段时间，本店后续累计 N 笔不同会员的正常 NEW 订单，打开该潜客关联开卡弹窗。即使没有同手机号候选，也可产生约 2N 个额外查询。
- 影响：候选为空仍扫描和查验不相干订单，延时随门店新开卡数线性增长，不满足避免 N+1 的要求。
- 修复建议：先由数据库按门店、手机号、会员创建时间、必要顾问归属限定会员，再查询其 NEW/支付时间/正净实收订单；归因排除可用 NOT EXISTS，商品名称批量或投影获取。最终 LINK 写入仍须保留当前锁后资格复核。
- 回归：给同店建立大量不相干手机号的新会员/NEW 订单及少量合格候选，清缓存后测候选正确性和 SQL 数；增加不相干订单不能线性增加查询数量。

## Minor / P3

### 3. 总部跨店工作台的批量名称查询仍可能触发关联补查

- 位置：`LeadService.java:225-226`；`SysUserEntity` 的 role/venue 均为 `FetchType.EAGER`。
- 证据：`users.findAllById` 加载 owner 实体，而不是名称投影；Hibernate 可在返回该查询前为每个尚未缓存的门店发出补充查询，随后才执行显式 venues 批量查询。
- 影响：总部跨多店读取工作台时，查询数量可能随门店数增长。当前 `workbenchScopesQueriesAndBatchLoadsAssociationsForSmallPages` 只从顾问单店访问，不能覆盖此情况。此项属于静态推断，尚未在独立运行中测量 SQL。
- 建议：owner 名称投影避免无关 EAGER 关联，补总部跨至少十店且清一级缓存的查询量断言。

## 已审查的关键行为

- LEAD_READ/WRITE 仅总部、店长、顾问持有；所有 controller 路径使用对应权限。顾问访问同时限制本店与本人归属；重放仍先检查当前访问权限。
- 除创建外必填 expectedVersion；请求键与 actor 唯一，指纹含动作、目标和请求内容。重放在版本与未来日期检查之前，内容改变拒绝复用键；事件追加，无更新历史接口。
- PG 操作者 NO KEY UPDATE、顾问/接待人 KEY SHARE 与场馆 NO KEY UPDATE 相容；现有 lead→member→venue 顺序未发现倒置。NEW 使用真实 CommercialService 事务；LINK 在 member→venue 锁后刷新订单并重新检查资格。
- 同门店手机号唯一、转化订单唯一、单潜客 BOOKED 部分唯一索引；阶段与转化字段、预约时长/结果字段均有 V20 约束。未改旧迁移。
- DECLINED/CONVERT 要求无待处理体验，LOST 须 REOPEN；取消/未到店基于已有事实恢复阶段；ATTENDED/NO_SHOW 不允许在开始前登记。
- LINK 限同门店/手机号、新建会员、NEW 订单、支付不早于建档、当前正净实收；顾问必须归属相符；已归因订单排除。NEW 不开登录账号、不覆盖已有会员。
- 工作台先构造授权范围 cohort 再计算统计，列表筛选/分页不改变分母；部分退款使用净额、全额退款不计有效转化，同时保留 CONVERTED 历史。

## 验证边界

已阅读当前 HTTP 测试（包含真实退款路径、并发请求、预约交叉接待人、真实开卡与预约同时等待场馆锁的 PG 测试、失败回滚、身份与时间重放、单店工作台 SQL 计数）。本审查未重复执行测试；H2/PG 通过数量及 V19→V20 真迁移结论应引用实施者和协调者的新鲜执行记录。上述两项 P2 的返回值通常正确，因此现有功能断言不能替代加载范围/查询量回归。

## 限定复审（2026-10-09，修正原发现）

仅复审原三项发现及其修复影响范围，未重新扩大审查或运行构建/数据库测试。

- P2-1 **ADDRESSED**：`LeadLookupRepository.venues/advisors/products` 将门店、角色、在职/在售条件下推数据库，采用标量投影；`LeadService.options` 已改用这些查询，不再加载全平台账户实体。新增 `optionsAndConversionCandidatesUseBoundedScopedQueries` 清理缓存后检查账户加载量与 SQL 上限。
- P2-2 **ADDRESSED**：`conversionCandidates` 使用一个 JOIN 查询限定门店、手机号、会员创建时间、支付时间、NEW、订单状态、正净实收与 NOT EXISTS 已归因订单，直接带出订单/会员/产品，服务不再逐单调用 `qualifies`。新增测试放入本店/外店不相干订单，断言仅加载一个合格订单且 SQL 数有上限。owner 参数仅 SALES 时传 lead.ownerId；经理/总部传 null，与实际 LINK 写入的 `(!sales() || member.salesUserId == lead.ownerId)` 一致，也符合批准设计。写入端锁后复核保留。
- P3-3 **NOT ADDRESSED（本次读取时）**：共享目录 `loadBatch` 仍调用 `users.findAllById`，`LeadLookupRepository` 尚未包含 owner 名称投影；EAGER role/venue 的跨店补查可能性仍在。新增测试只检查 options/候选，未覆盖总部跨店工作台。本项不影响前述两项 P2 的修正判断；若修复仍在落盘，应再按最新内容确认。

限定范围内未发现修复引入的新业务资格差异。两项 Important 源码整改已完成；测试执行结果由协调者核对，当前复审不宣称 PG/全量构建已通过。

## P3 最终限定复审（2026-10-09）

P3-3 **ADDRESSED**。本次重新读取确认 `LeadLookupRepository.ownerNames(ids)` 与 `venueNames(ids)` 都使用 id/name 标量投影，空集合直接返回；`LeadService.loadBatch` 已改用两者，不再加载 owner 实体或触发其 EAGER role/venue 关联。读取范围仍来自已授权潜客集合的去重 owner/venue ID，未扩大可见范围。

新增 `headquartersWorkbenchBatchesTenVenuesWithoutEagerAccountAssociations` 建立十个门店，清一级缓存后用总部读取两条分页记录，检查 cohort 总数、SQL 上限 12 以及 SysUser 实体加载数不超过 1，覆盖原 P3 场景。已审读测试源码；实施者报告的 RED 16 SQL 与最终 25 项 PG GREEN 尚由协调者独立读取执行证据，本复审不据此重复宣称运行通过。

同时复核 `memberExists(venueId,phone)` 的 COUNT 查询与 NEW 转化调用：条件与原先本店会员列表的手机号匹配等价，不再把本店全部会员实体装入内存，未改变同手机号已有会员时转入 LINK/已有会员流程的行为。本次查询修正范围未发现新问题。

最终源码审查状态：原两项 P2、一项 P3 均 **ADDRESSED**；原发现全部关闭。构建、真实 PostgreSQL 和最终集成验收仍以协调者的新鲜执行证据为准。
