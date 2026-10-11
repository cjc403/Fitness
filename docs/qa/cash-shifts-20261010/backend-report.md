# 交班与日结后端专项报告

状态：后端专项已通过，等待 root 的只读审查、全量构建和本地启用。本报告不声明全量回归或启用完成。

## 修改范围

- `fitness-backend/src/main/java/com/gym/fitness/module/cashshift/`：六个 HTTP 接口、请求 DTO、四个实体及其 repository、CashShiftScope/CashShiftLedger/CashShiftService。
- `fitness-backend/src/main/java/com/gym/fitness/security/RolePermissionCatalog.java`：三项 CASH_SHIFT 权限；前台读写，店长/总部读写复核。
- `fitness-backend/src/main/resources/db/migration/V21__cash_shifts_and_daily_close.sql`：只新增四表及索引、外键、唯一约束；未修改 V1—V20。
- `fitness-backend/src/test/java/com/gym/fitness/CashShiftIntegrationTest.java`：真实 Spring/MockMvc 集成测试。

保留共享 checkout 既有改动；没有提交、推送、部署、修改运行中制品，也没有向真实 5433 业务库写入测试数据。构建与长日志均位于独立 TEMP 源副本。

## 接口与资金口径

实现 `/api/v1/cash-shifts` 下 workbench、detail、payments、open、submit、review 六个接口，输入和返回字段遵循绑定设计。公开契约未改变。

现金口径与原 FinanceReportService 一致：按订单门店归属，累计登记的 SUCCESS PAYMENT/REFUND，保留历史未关联价值分摊的退款。流水渠道 trim，空值 UNKNOWN，不声称银行验证到账。基线和截止 ID、系统金额、营业日及状态均由服务端决定。

第一次开班排除现有本店登记流水；后续从上一关闭班的 cutoffPaymentId 延续，包含班外新登记。迟到/补录退款即使 occurredAt 早于开班，也按登记 ID 入候选。首班历史排除数以该门店第一班基线统计；关闭班逐笔快照及系统渠道金额固定。详情最多加载快照第一页 100 条，分页接口单独读取；已关闭班的总数、carriedOver 计数直接查询数据库，不加载全部逐笔记录。

Workbench 的营业日汇总独立于状态筛选和分页，OPEN 不入已交班金额；currentShift 独立寻找该门店任意日期的 OPEN，实时金额以一批聚合查询补充。可选门店保持当前权限的完整清单，显示名为标量查询，避免加载会员/账号关联。

金额用整数分；实际收款/退款均明确要求输入，校验非负、至多两位小数和单值上限。固定四渠道加登记出现渠道必须逐行包含；漏行、重复及额外渠道均拒绝。任意收款或退款差异都必须说明，包括净额抵消情形。空班可明确填四行零提交。

## 权限、重放和锁

CashShiftScope 在服务内执行角色和 authority 交集验证，范围验证在重放之前再次执行；写请求还在账号行锁内重新核对当前账号角色、状态、会话版本及非总部门店。总部切店令牌遵循现有 JWT 规则，账号的原门店不会错误阻止总部写选定门店。

写入锁序为 actor → 既有 VenueMutationLock → shift；PostgreSQL actor 与 venue 使用 FOR NO KEY UPDATE，兼容其他事务外键 KEY SHARE；不取 member/order/entitlement 反向锁。先用标量读取门店，取得共享门店锁后才读取/锁班次，避免使用锁等待前的过期 JPA 班次快照。

OPEN 由开班者或本店管理者提交；RETURNED 可由开班者、上次提交人或管理者修正。复核仅管理者且不得是最后提交人。动作版本每次加一，流水变化和版本变化返回 2600。成功重放在版本/状态检查前返回，先检查当下权限、范围、动作身份并匹配完整请求指纹；同 actor 的请求键跨动作、班次或门店不混用。渠道修正审计追加 before/after 核对值和说明，不覆盖原审计。

## TDD 与专项结果

初始 RED：3 个创建/基线/权限测试全部收到 404；补开班与读取闭环后 3/3 GREEN。

扩展 RED：新增提交、复核、归班、快照与分页行为后，12 项中新增 9 项因缺少动作端点失败；补实现后 H2 与 PostgreSQL 均 12/12 GREEN。

加固 RED：总部切店被错误拒绝、OPEN 实时金额为零、关闭详情加载 103 条而非 100 条。修正后专项通过。首班历史数的测试使用独立门店，避免初始化示例账本的 16 条成功流水影响字面预期。

最终命令（PowerShell，仓库根目录）：

```powershell
$env:JAVA_HOME=(Resolve-Path .tools/jdk-17).Path
$testCopy=Join-Path $env:TEMP 'fitchain-cash-backend-20261010'
& .tools/maven/bin/mvn.cmd -f "$testCopy/pom.xml" '-Dtest=CashShiftIntegrationTest' test
& .tools/maven/bin/mvn.cmd -f "$testCopy/pom.xml" '-Dtest=CashShiftIntegrationTest' '-Dcash.test.pgurl=jdbc:postgresql://127.0.0.1:55449/cash_junit' test
```

- H2：22 total，0 failures，0 errors，6 skipped；16 项执行通过，6 项仅针对真实 PG 锁语义，BUILD SUCCESS。
- PostgreSQL 18.6 / 55449 / cash_junit：22 total，0 failures，0 errors，0 skipped，BUILD SUCCESS；Flyway V1→V21 与 Hibernate validate 成功。
- 测试 URL 严格白名单，只接受 `jdbc:postgresql://127.0.0.1:55449/cash_junit`；未给参数只用独立 H2 内存库。

手算字面断言：CASH 系统收 100 退 130，净 -30；实核收 105 退 135，净也 -30，但收、退分别差 +5，必须说明。WECHAT 系统收 200 退 10，实核收 195 退 10，净差 -5；全班系统净 160，实核净 155。另验证未知渠道、SELF_SERVICE、空白渠道、非法精度、负额、单值上限、漏行/重复、固定第一页和审计前后值。

真实独立事务并发测试直接等待 `pg_stat_activity.wait_event_type='Lock'` 的指定表等待者达到数量后才释放 SQL 屏障；轮询仅用于观察真实锁等待，不以 sleep 猜测竞争成立：

1. 不同操作者并发开班：两个 venue 等待者，最终一成功、一 2600，门店仅一个 OPEN。
2. 同操作者重复交班：一个 venue 等待者加一个 actor 等待者，两个成功响应，仅一个 SUBMIT 事件，无 FK 死锁。
3. 同操作者跨店使用同键：第二请求等 actor 锁，首店成功，第二店 payload 不同返回 1001，不生成第二店班次。
4. 实际 CommercialController 销售先排到门店锁：销售提交后交班重查拒绝旧指纹，刷新后收款 20 全部归集。
5. 交班先排到门店锁：空班关闭，随后实际销售成功；下一班 carriedOver=1，收款 20，唯一归班。
6. 实际退款申请/他人批准/退款登记链：补录 2020 occurredAt 的退款先排锁，提交拒绝旧指纹，刷新得到净 -20，正确归集。

商业收款测试在交班前后比较原订单 paid/refunded/status 及权益 remaining/status；退款测试比较原订单退款结果，交班不改写这些资金/权益数据。

## 私有证据位置与限制

原始 MockMvc 日志可能包含测试 JWT，仅在 TEMP 留存，不放入报告或共享交接文档：

- `%TEMP%/fitchain-cash-backend-20261010/red-initial.log`
- `%TEMP%/fitchain-cash-backend-20261010/green-initial.log`
- `%TEMP%/fitchain-cash-backend-20261010/red-extended.log`
- `%TEMP%/fitchain-cash-backend-20261010/green-extended.log`
- `%TEMP%/fitchain-cash-backend-20261010/red-hardening.log`
- `%TEMP%/fitchain-cash-backend-20261010/pg-concurrency.log`
- `%TEMP%/fitchain-cash-backend-20261010/h2-final.log`
- `%TEMP%/fitchain-cash-backend-20261010-lock-mutant/lock-mutation-red.log`

真实事务并发测试提交的数据全部属于独立测试门店，仅留在隔离 cash_junit，不进入实际业务库。独立 TEMP mutant 副本删除 cashshift 的门店锁后，真实销售屏障测试确实 RED：1 项失败、0 errors，原因是交班不再成为第二个 venue 锁等待者；它证明并发测试能捕获缺失共享门店锁。主工作区及最终 GREEN 源副本未采用该变异。root 负责全量 H2 package、只读审查、V20 克隆迁移比对、前端及最终本地启用。
