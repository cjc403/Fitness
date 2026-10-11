# 潜客后端实施与回归记录

日期：2026-10-09。仅本地开发；未提交、推送、发布或操作实际5433业务数据。首次构建使用隔离快照 `C:/Users/CJC/AppData/Local/Temp/fitchain-leads-build-20261009`；商品竞争修复使用另一快照 `C:/Users/CJC/AppData/Local/Temp/fitchain-leads-review-fix-20261009`，没有覆盖协调者正在使用的原构建目录或运行中的JAR。

## 实现文件

- 新增 `fitness-backend/src/main/java/com/gym/fitness/module/lead/`：三个实体、LeadDtos、四个repository（含LeadLookupRepository投影）、LeadService、LeadController。
- 新增 `fitness-backend/src/main/resources/db/migration/V20__leads_and_trial_appointments.sql`；V1—V19未修改。
- 新增 `fitness-backend/src/test/java/com/gym/fitness/LeadIntegrationTest.java`（最终27项）。
- 修改 `fitness-backend/src/main/java/com/gym/fitness/security/RolePermissionCatalog.java`：LEAD_READ/WRITE只授予SALES、MANAGER、SUPER_ADMIN。
- 修改 `fitness-backend/src/main/java/com/gym/fitness/module/commercial/service/CommercialService.java` 的 `lockOperator`：PG使用FOR NO KEY UPDATE，其他数据库仍用原repository锁；原文件此前已有修改全部保留。

## 业务与自查

潜客不创建会员或订单。跟进事件追加；体验预约独立，限制未来开始、15—180分钟、单潜客一个BOOKED及接待顾问半开时间区间不重叠。取消/未到店根据到店、已体验事实恢复阶段；LOST必须先REOPEN，转换前处理待体验。请求键必填，除创建外版本必填，原样重放先于时间/版本校验且重新鉴权，改变内容或操作拒绝复用键。

NEW复用CommercialService，在同一事务中生成会员、NEW订单、权益、价值账、支付流水和合同/收据；超价等失败一并回滚，不生成登录账号或覆盖旧会员。LINK写入在member→venue锁后刷新订单复核门店、手机号、会员创建/订单支付时间、NEW与正净实收及唯一归因；SALES额外要求会员归属与潜客顾问一致，MANAGER/总部按授权门店处理。全额退款保留CONVERTED历史但移除有效转化；统计分母独立于列表筛选/分页。

PG写锁顺序为operator→lead→member（涉及转换时）→venue；顾问/接待人FK兼容KEY SHARE在venue前获取。操作者NO KEY UPDATE避免阻塞接待人的外键检查，不在取得venue后升级operator锁。预约冲突在venue串行化后检查。商品预检只查询COUNT，不把商品实体加载进一级缓存；商业链在取得venue后首次读取当前商品状态与价格。

工作台按门店/归属直接查询，订单/待体验批量读取，顾问及门店名称使用标量投影。options按数据库授权范围/角色/状态投影。候选使用单条资格JOIN及NOT EXISTS已归因订单；NEW已有会员检测采用门店/手机号COUNT。测试量化了单店/总部十店工作台、选项与候选的查询上限及实体加载范围。

## RED / GREEN执行证据

以下命令均使用仓库 `.tools/jdk-17` 与 `.tools/maven/bin/mvn.cmd`，在相应临时快照运行；日志不复制至仓库，以免保存MockMvc输出中的临时测试令牌。

|阶段|命令/日志|已读取结果|
|---|---|---|
|初始创建/隔离RED|`mvn -B -Dtest=LeadIntegrationTest test`；原快照 `leads-red.log`|2项失败、0错误；新增端点404|
|完整基础RED|同命令；`leads-red-all.log`|13项失败、0错误；新增端点404|
|基础GREEN|同命令；`leads-green-base.log`|13/13通过|
|加载范围/长度RED|同命令；`leads-red-batch.log`|17项中2失败：跨店载入24而非12；1000字跟进前缀导致500|
|批量读取GREEN|同命令；`leads-green-batch.log`，`leads-postgres-first.log`|H2、PG各17/17通过|
|选项投影RED|`-Dtest=LeadIntegrationTest#optionsAndConversionCandidatesUseBoundedScopedQueries`；`leads-red-options.log`|加载21个sys_user，预期最多1|
|总部查询RED|`-Dtest=LeadIntegrationTest#headquartersWorkbenchBatchesTenVenuesWithoutEagerAccountAssociations`；`leads-red-headquarters.log`|16条SQL，超过上限12|
|锁回归反向验证|仅隔离快照临时恢复旧FOR UPDATE，PG单测；`leads-lock-mutation-red.log`|场馆屏障回归失败，两个操作者无法同时到达venue；正式源码始终保持新锁，快照已恢复|
|前一轮最终PG|`mvn -B -Dtest=LeadIntegrationTest -Dleads.test.pgurl=jdbc:postgresql://127.0.0.1:55439/leads_junit test`；原快照 `leads-green-final-pg.log`|25/25通过，0失败/错误/跳过；V1—V20迁移并Hibernate validate成功|
|前一轮全量|`mvn -B clean package`；原快照 `leads-full-package.log`|189项，1失败：旧RetentionIntegrationTest.otherVenueRenewalsDoNotExposeAmountsToLocalStaff时间精度fixture，expected888实际0；新25项均通过。协调者负责修正并重跑，本报告不将该全量标为通过|
|商品缓存竞争RED|新快照 `mvn -B -Dtest=LeadIntegrationTest#newConversionRechecks* -Dleads.test.pgurl=jdbc:postgresql://127.0.0.1:55439/leads_junit test`；`product-red-pg.log`|2失败、0错误：锁等待期间已下架仍code0（预期2600），已降价仍code0（预期1001）|
|商品修复最终PG GREEN|新快照 `mvn -B -Dtest=LeadIntegrationTest -Dleads.test.pgurl=jdbc:postgresql://127.0.0.1:55439/leads_junit test`；`product-green-pg.log`|27/27通过，0失败/错误/跳过；18:52:34完成|
|商品修复最终H2 GREEN|新快照 `mvn -B -Dtest=LeadIntegrationTest test`；`product-green-h2.log`|27/27通过，0失败/错误/跳过；18:52:55完成|

新增两个商品竞争测试用实际数据库venue锁屏障：先确认NEW转换已阻塞于venue，再在持锁连接内更新商品并提交，之后等待转换返回。分别验证下架、降价均拒绝转换；会员、订单、权益数量不变，潜客仍CONSULTING/version0且只有建档事件。

测试数据库URL仅允许 `127.0.0.1:55439/leads_junit`；不允许改成实际本地库。数据库查询确认V20迁移成功，前一轮测试结束sales_lead为0。没有新样例数据写入实际数据库。

## 交付边界

后端最终27项H2/PG回归均通过。该商品竞争修复只改变LeadLookupRepository、LeadService和LeadIntegrationTest；无API契约、旧迁移、Retention产品代码或前端改动。已自查商品在锁前不再进入JPA缓存，状态/价格由商业链锁后读取，锁顺序及事务边界未变化。

协调者仍负责同步最新源码、最终全量clean package、独立复审、浏览器验收和本地制品激活；本报告不声明这些尚未由本执行者完成的步骤已通过。

## 19:03 时间精度复审补充（最终潜客29项）

协调者全量191项发现潜客LINK候选测试偶发expected1实际0；本执行者独立诊断，并未以放宽资格或加等待规避。临时打印探针只存在于fix TEMP：LeadPrecisionProbeTest.java；precision-probe.log不打印JWT。固定同一真实Hibernate时钟tick12:00:00.123456900，读取到lead API/内存.123456900、DB舍入.123457；Hibernate @CreationTimestamp生成member/order created_at为.123456，DB保持.123456；订单paid_at完整纳秒也舍入.123457。诊断结合当前Hibernate6.6.53字节码ClockHelper精度6的Clock.tick1000ns行为，确认新lead手工时间与既有Hibernate时间精度不一致。

新增两项确定性测试，测试专用可控Hibernate clock并在finally恢复，不在产品中加入测试钩子：leadGeneratedTimesMatchTheirStoredMicrosecondPrecision验证API生成的created/updated/event/visited/trialCompleted/recorded时间与真实SQL存储一致；sameInstantHibernateMemberAndPaidOrderRemainEligibleAtDatabasePrecision验证同瞬间Hibernate实际生成的会员微秒时间与精确微秒支付时间仍能进入候选并LINK，convertedAt亦与存储一致。先RED：precision-red-h2.log，2失败/0错误（API期望.123456实际.123456900；候选期望1实际0）。

最小产品修复只有LeadService.now()追加truncatedTo(MICROS)，时区仍为Asia/Shanghai；V20字节、旧迁移、历史记录及全部LINK门店/归属/NEW/净实收/时间资格均未改变。

最终最新源码验证（均在fix TEMP）：
- mvn -B -Dtest=LeadIntegrationTest -Dleads.test.pgurl=jdbc:postgresql://127.0.0.1:55439/leads_junit test → precision-green-pg.log，29/29通过，0失败/错误/跳过，19:03:03 BUILD SUCCESS。
- mvn -B -Dtest=LeadIntegrationTest test → precision-green-h2.log，29/29通过，0失败/错误/跳过，19:03:22 BUILD SUCCESS。

这是潜客29项H2/PG通过声明，不能替代协调者最新193项全量构建；后者仍由协调者执行。源码及测试已稳定，可同步。未操作实际5433、服务器或原build快照。

## 协调者最终门槛与本地启用

最新源码临时副本于19:04:38完成`mvn -B clean package`：193项、0失败/错误/跳过、BUILD SUCCESS。此前旧Retention精度fixture的偶发失败已用固定且先于asOf的付款时间解决，finally恢复测试时钟，未改RetentionService产品代码。最终JAR SHA256为2AE22EF9C4F44EF9E5B7807DE28C8D38EDFFA104ED9709845D501147DA7EFAF8。

隔离V19→V20迁移保留全部58张历史表数量与摘要，HTTP17项通过。本机启动器已先行应用V20；备份后停本项目旧后端，再启用测试通过制品，真实PG5433启用前后61表数量/摘要完全一致（认证前）。健康UP，启动器退出0，实际总部登录后潜客工作台/选项与旧续费回访GET均通过。只做常规验证登录及当前会话退出，没有业务测试写入或账号重置。最终汇总见verification.json；未连接或部署线上。
