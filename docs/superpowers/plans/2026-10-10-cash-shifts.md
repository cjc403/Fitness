# 前台交班与日结 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 每店一个班次，前台逐渠道核对实际收退款并提交，店长独立复核，保留可信的营业日日结及快照。
**Architecture:** 新cashshift子模块读原成功payment_transaction，订单门店定范围，登记ID边界及共享门店锁防漏归班；关闭班保存不可重算的资金快照，审计修正仅改变实际核对数。
**Tech Stack:** Java17/Spring Boot3.5.16/JPA/Flyway/PostgreSQL18.6/H2，Vue3/TS5.9/Element Plus/Vite7；不新增依赖。
**Spec:** `docs/superpowers/specs/2026-10-10-cash-shifts-design.md`（字段/API/状态/金额/归班口径为绑定契约）。

## Global Constraints

- 只在共享本地checkout开发；保留所有未提交旧功能，不提交/推送/部署；用户要求常规推荐方案直接执行，不再次询问。
- 真实业务库5433只备份/升级/启用/读验收；测试写入仅隔离PG55449和应用8189。JAR构建在临时源副本，不覆盖正在运行的JAR。
- 只新增V21四表；V1—V20字节不改。上海TIMESTAMP(6)微秒时间、整数分计算；系统金额、边界、阶段不能由前端决定。
- RECEPTION/MANAGER/SUPER_ADMIN读写，本店隔离；只有MANAGER/SUPER_ADMIN且不是最后提交人复核。OPEN→SUBMITTED→APPROVED/RETURNED→SUBMITTED。
- 按本店登记ID归集全部成功PAYMENT/REFUND；第一次开班排除既有历史，后续含前班关闭后的班前登记。固定快照和请求指纹重放，冲突刷新。
- 门店统一OPEN唯一；复用actor→VenueMutationLock→shift锁，无member/order反向锁；已有财务行为和资金账不改。
- 当前只验桌面；保留主题并即时清除会话/范围变化后的已载财务资料。

## Review Focus

- 补录过去退款和新渠道发生于预览与提交之间：拒绝旧指纹，刷新后完整归集。
- 两渠道差异或同渠道收退款差异净相抵为0：仍必须说明，不能伪装无差异。
- 已退回班期间新班发生交易：修正只用原快照，归班不重复且历史审计可还原。
- 不同操作者/同操作者跨店并发：幂等键隔离、唯一OPEN和流水唯一归班，不因FK锁死锁。
- 切账号/退出/总部切店及晚到请求：敏感已载资料立即隐藏/清空，所有弹层和写入结果作废。

### Task 1: 交班后端、迁移及真实HTTP测试

**Files:** 创建`fitness-backend/src/main/java/com/gym/fitness/module/cashshift/`：controller/CashShiftController.java、dto/CashShiftDtos.java、entity/CashShiftEntity.java、CashShiftChannelEntity.java、CashShiftPaymentEntity.java、CashShiftEventEntity.java；相应repository；service/CashShiftService.java、CashShiftLedger.java、CashShiftScope.java。修改`security/RolePermissionCatalog.java`。创建`db/migration/V21__cash_shifts_and_daily_close.sql`、测试`fitness-backend/src/test/java/com/gym/fitness/CashShiftIntegrationTest.java`。没有其他已有产品文件写权限；必要偏离先向root说明。

**Interfaces:** 提供Spec里的全部6个HTTP接口/DTO/response字段。本任务私有Java接口可据边界合理实现，ledger负责候选/指纹/合计，scope负责鉴权和FK兼容actor锁，service负责状态/固定快照/读模型；不把测试时钟或清理钩子塞进产品。role catalog常量CASH_SHIFT_READ/CASH_SHIFT_WRITE/CASH_SHIFT_REVIEW，前三角色相应持有；role+authority交集判断。

- [x] 写创建、首班基线、角色及跨店3个真实MockMvc测试，运行期望404/缺权限RED。测试类只允许PG URL `jdbc:postgresql://127.0.0.1:55449/cash_junit`，无参数走独立H2。
- [x] 实现V21、权限与开班读取最小闭环，以上3个GREEN；保存RED/GREEN日志到自己的TEMP，不构建真实target。
- [x] 新增后续归班/补录旧发生时间/取消净差、未知渠道/漏行/重复/非法精度、FLOW变化、版本、不可自审、退回固定快照及审计、重放/改payload/降权后权限复核的失败测试，再补实现并GREEN。手算字面期望：CASH收100退130系统净-30；实际收105退135净也-30却必须说明；WECHAT收200退10，实际收195退10差-5；整体系统160实际155。
- [x] 新增真实不同事务锁屏障与并发测试，覆盖开班不同操作者、交班重试及实际商业收款/退款与边界。同店写入通过共享venue锁；PG不能用sleep猜测竞争已发生，检查锁等待/屏障。校验原订单/权益/流水未被交班改写。
- [x] 最终专项H2和PG通过，报告测试命令、字面结果、锁/归班边界、自查与已知限制；root负责最终全量打包。

### Task 2: 桌面交班工作台、表单及运行测试

**Files:** 创建`fitness-frontend/src/api/modules/cashShifts.ts`、`src/views/admin/CashShiftCenter.vue`、`tests/cash-shifts-runtime.test.mjs`；修改`src/router/index.ts`、`src/layouts/AdminLayout.vue`。只改这些前端文件。

**Interfaces:** 消费Task1严格Spec HTTP契约。入口`/cash-shifts`“交班与日结”，菜单/路由都用角色+权限交集。工作台含完整总部门店选项、营业日、日汇总、任何日期当前OPEN、渠道和分页班次；详情预览/支付分页/审计；开班、提交/修正和APPROVE/RETURN。

- [x] 先写API/路由缺失与真实SFC提交运行RED；用现有leads-runtime的Vue编译运行方式，边界替身只代HTTP，不镜像产品函数计算期望。
- [x] 实现页面与接口；首次创建提示历史基线/班前流水、自助仅登记、营业日/发生日差别；预览金额清晰，实际收款/退款空白，0要显式输入，差异由分判断。
- [x] 增加Runtime RED→GREEN覆盖重复点击、相同失败重试键/编辑新键、版本与流水冲突重载、资金净差相抵仍要求说明、角色/不能自审、returned快照、分页/筛选不改统计、完整门店列表、上海时间及会话同步清空/旧成功失败晚到/卸载不刷新。
- [x] 完整`node --test tests/*.test.mjs`及`npm run build`退出0。报告新增测试数、证据路径、当前实际桌面未验收边界；不自行启用真实服务或写业务数据。

### Task 3: 独立复审、隔离验收与最终制品

**Files:** `docs/qa/cash-shifts-20261010/`的审查/HTTP/桌面/迁移/最终验证；本计划私有`.superpowers/sdd/2026-10-10-cash-shifts/`测试启动器/ledger/helper。
**Interfaces:** Task1/2合并的真实API/页面；最终制品供Task4启用。

- [x] Task1和Task2分别由独立只读审查者按差分包、brief、报告复审，关闭重要问题；最终整体再一次独立审查。缺陷修复遵循TDD和范围复审。
- [x] 实际库先只读基线并备份，恢复到隔离PG55449/migration_qa，V20→V21后所有历史61表数量/摘要一致，新四表空，ddl validate及健康UP。
- [x] Fresh隔离库cash_qa以prod,demo运行8189/9200，前端5175；HTTP验证完整开班→真实收退款→核对→提交→店长退回→修正→复核、原金额一致、角色限制与补登记下一班。
- [x] 桌面浏览器前台/店长/总部实际操作、控制台错误检查和截图；无手机检查，所有测试资料在隔离库。
- [x] 同步最新后端源到root TEMP执行`mvn -B clean package`，全量0失败且BUILD SUCCESS；前端最终测试/构建在代码末次修改后通过，源码与制品一致。

### Task 4: 本地启用与交接

**Files:** 本地backups、logs、测试通过JAR/dist，README、AGENTS、docs/当前功能与待办、docs/交班与日结_20261010.md、仓库外交接文档§0/§10。

- [x]  fresh实际库备份及61历史表摘要，备份旧JAR；核对项目PID/路径后停旧后端，安装测试通过JAR，启动prod，V21健康UP；对比所有61历史表未变、新表0。不可覆盖运行JAR。
- [x] 实际总部常规登录读新入口/旧接口，退出该次验证会话；`start-fitchain.ps1 -NoBrowser`退出0、真实localhost5173前端200。认证元数据变化发生在业务保留摘要之后，诚实分开记录。
- [x] 只关闭本轮自有测试进程，真实8080/9091/5173/5433保留；更新两AI共享交接和用户直接推荐方案/桌面优先偏好，凭据节保持原样。
- [x] git diff --check与证据JSON验证通过，完成说明和本地使用入口。无提交/推送/上线，也不开始第4项。
