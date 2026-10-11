# 后端审查修正第 1 轮

状态：两项审查意见均已修正，后端专项 H2 / 隔离 PostgreSQL 通过。全量 package、审查复核、前端验收及本地启用由 root 执行，本报告不声明这些步骤完成。

## 本轮准确修改文件

1. `fitness-backend/src/main/java/com/gym/fitness/module/cashshift/dto/CashShiftDtos.java`
2. `fitness-backend/src/main/java/com/gym/fitness/module/cashshift/service/CashShiftLedger.java`
3. `fitness-backend/src/main/java/com/gym/fitness/module/cashshift/service/CashShiftService.java`
4. `fitness-backend/src/test/java/com/gym/fitness/CashShiftIntegrationTest.java`
5. 本报告 `docs/qa/cash-shifts-20261010/backend-fix-round1.md`

没有修改迁移、权限目录、其他业务模块或运行中 JAR；六个公开接口及全部返回字段不变。测试仅独立 TEMP 源副本、H2 内存库与白名单 cash_junit；未连接/写入真实 5433 或 root 的 migration_qa / 8189。

## 空渠道元素：先 RED，再 GREEN

新增两个真实 HTTP 测试：`channels=[null]`，以及四个有效必填渠道之后再加一个 null。修正前两个测试均收到 HTTP 500，验证了服务解引用空元素的问题。

将 SubmitRequest.channels 改为 `@NotNull List<@NotNull @Valid ActualChannel>`。修正后两个测试全部通过：HTTP 400 / code 1001，班次保持 OPEN、version=0、审计仅原 OPEN 事件。继续保留 ActualChannel 内金额和渠道约束。

## 有界读取：先 RED，再 GREEN

增加真实 Hibernate statistics 回归，而非按源码文本或 mock 调用验证：

- 151 条 OPEN 候选流水，请求第 2 页、每页 7 条。RED 实际加载 151 个 PaymentTransactionEntity，期望 7；GREEN 恰好加载 7 个，首尾 ID 与已登记 ID 字面位置一致，total=151。
- 当日 8 个关闭班和 1 个 OPEN，页面仅展示 2 行且当前班不在该页。RED 加载 9 个 CashShiftEntity，期望最多 3；GREEN 仅页内班次和独立 currentShift，且 CashShiftChannelEntity 加载数为 0。账号实体最多加载当前 JWT 校验所需的 1 个。

OPEN 流水分页在 CashShiftLedger 使用同一完整候选条件分别 count、setFirstResult、setMaxResults。候选仍按订单门店、SUCCESS PAYMENT/REFUND、登记 ID 边界及 ID 升序查询。空页保留正确 total。详情和提交的完整 candidates 查询未改为分页，指纹仍覆盖全部候选：测试断言详情 paymentCount=151、收款=151；追加第 152 条收款 2 后旧指纹返回 2600，刷新后全部 152 条按 153 元提交。

工作台的 dailySummary 改为按营业日及权限门店过滤的标量 SQL 聚合；dailyChannels 改为相同范围下按渠道 GROUP BY，且仅累计非 OPEN 班。班次实体只执行分页查询和独立 currentShift 查询；人员显示名只收集这些实际显示班次的 opened/submitted/reviewed ID 后批量标量读取。总部仍保留当前权限完整门店列表。OPEN 实时金额原有批量聚合继续执行。

字面结果跨分页、状态筛选、退回/再提交和门店范围保持正确：8 个关闭班各系统收款 10 元，系统合计 80；第一班退回后实核由 10 改 11，再复核通过，实核合计 81，净差 1，approvedNet=11。追加 OPEN 实时收款 99 后，shiftCount=9、openCount=1、pendingReviewCount=7、approvedCount=1、returnedCount=0；OPEN 的 99 不进入已交班合计。第 3 页 size=2 和 APPROVED size=1 均得到同样日汇总及 CASH 渠道合计；空的另一门店为 0，总部选择原门店仍为 80。

## 最终专项命令与结果

```powershell
$env:JAVA_HOME=(Resolve-Path .tools/jdk-17).Path
$testCopy=Join-Path $env:TEMP 'fitchain-cash-backend-20261010'
& .tools/maven/bin/mvn.cmd -f "$testCopy/pom.xml" '-Dtest=CashShiftIntegrationTest' test
& .tools/maven/bin/mvn.cmd -f "$testCopy/pom.xml" '-Dtest=CashShiftIntegrationTest' '-Dcash.test.pgurl=jdbc:postgresql://127.0.0.1:55449/cash_junit' test
```

- H2：26 total，20 执行通过，0 failures，0 errors，6 个真实 PG 锁语义测试 skipped；BUILD SUCCESS。
- PostgreSQL 18.6 / cash_junit / 55449：26 total，26 passed，0 failures，0 errors，0 skipped；BUILD SUCCESS。
- 原有真实并发开班、同 actor 重放/跨店请求键及真实销售/补录退款锁屏障测试均继续通过。

本轮使用既有隔离 PG 的 V21 模式，不修改 Flyway 历史或 root 克隆库。

## 私有日志与边界

日志可能包含测试 JWT，仅存 TEMP，不复制进仓库报告或交接文档：

- `C:/Users/CJC/AppData/Local/Temp/fitchain-cash-backend-20261010/round1-null-red.log`：2 failed，HTTP 500。
- `C:/Users/CJC/AppData/Local/Temp/fitchain-cash-backend-20261010/round1-null-green.log`：2 passed。
- `C:/Users/CJC/AppData/Local/Temp/fitchain-cash-backend-20261010/round1-hydration-red.log`：2 failed，151 对 7、9 对最多 3 的实体加载回归。
- `C:/Users/CJC/AppData/Local/Temp/fitchain-cash-backend-20261010/round1-hydration-green.log`：2 passed。
- `C:/Users/CJC/AppData/Local/Temp/fitchain-cash-backend-20261010/round1-h2-final.log`。
- `C:/Users/CJC/AppData/Local/Temp/fitchain-cash-backend-20261010/round1-pg-final.log`。

没有做负载吞吐 benchmark；本轮证明分页实体加载范围受限、聚合结果正确。完整 OPEN 详情仍读取全候选以计算可靠指纹，这是保留的绑定行为。
