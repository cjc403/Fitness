# 第4项训练计划与执行记录：最终整体独立审查

审查日期：2026-10-11。审查方式：实现只读，仅新增本报告；未运行测试套件、启动服务、连接数据库或云端，未改动 Git 状态、实现代码或交接凭据。

## Scope and evidence

- 绑定要求：`docs/superpowers/specs/2026-10-11-training-plans-design.md`；实施计划：`docs/superpowers/plans/2026-10-11-training-plans.md`。
- 精确范围：`.superpowers/sdd/2026-10-11-training-plans/all-review-170603/package.json` 的29项源码/测试变更。Base/head 均为 `b75b1ee`，实际基线是包中保存的共享脏工作区快照；没有以 HEAD diff 混入此前119项改动。
- 阅读5项既有文件的精确补丁与24项新文件全文。新文件包以全文保存，按统一行尾和末尾换行比较，24项均与当前内容一致；源码 manifest 所列后端290项及前端89项的当前 SHA-256 均一致。对5项既有文件仅评审本轮入口/权限增量。
- 已读取前后端 round 1 / round 2 报告，并独立核对最终权限重试、会员状态检查、创建候选后续赋值和冲突重载 loading 所有权的代码及回归。
- 已读取 `backend-build.json`、`frontend-build.json`、`postgres-tests.json`、`migration-proof.json`、`http-workflow.json`，并核对关联构建/测试日志末尾：后端 `mvn -B clean package` 共243项，230通过、13默认专项跳过、0失败/错误；前端181/181、0跳过，编译构建成功；PG专项7/7、0跳过，使用隔离 PostgreSQL 18.6、Flyway V22及 Hibernate validate。没有把全套 H2 验证称为全套 PostgreSQL 验证。
- 隔离旧库迁移证据显示V21→V22前后65张历史表数量/摘要一致，新增5表为空；隔离HTTP证据记录18项业务断言通过。以上是已执行证据的复核，不是本审查重新执行。

## Strengths

- **权限范围在读写和重试路径一致。** `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanAccess.java:25` 约束计划教练、服务门店和当前关系；`repository/TrainingPlanRepository.java:9` 让分页与统计使用相同范围，会员查询排除草稿。`service/TrainingPlanService.java:77` 在CREATE重试返回原计划前重新核验其当前权限，`:93` 在等待会员锁后重新检查ACTIVE/黑名单。店长/总部监督归档保留明确的范围与原因校验，不获得记录/修改权限。
- **并发与幂等有服务层和数据库保护。** `service/TrainingPlanService.java:50`、`:78`、`:80` 先锁会员并刷新当前资料；成功请求重放位于版本/状态冲突检查之前，规范摘要包含操作与目标。V22的有效计划键、有效预约键和事件键都有唯一约束；失败不会被捕获后继续使用已失败事务。真实PG专项覆盖跨教练发布、同预约登记、同key创建、反馈/作废竞争，以及真实锁等待后的授权变化。
- **训练历史与既有业务有清晰边界。** `service/TrainingPlanService.java:83` 校验日期、全部动作恰好一次、实际值及至少一个完成项，并重新验证ATTENDED预约；`:89` 保存动作目标/名称/模式快照；`:92` 作废保留反馈与原预约ID并释放有效占用。未发现向预约、资金、权益或体测的写入。详情历史分页、最近100条审计及eventTotal均实现，会员DTO没有员工审计。
- **前端请求和动作生命周期得到实际保护。** `fitness-frontend/src/components/TrainingPlanPanel.vue:50`、`:52` 清除私密数据，读写均捕获scope与各自generation；`:84` 在初始候选请求返回后检查原创建动作身份及候选generation，并保留人工选择；`:136` 冻结payload/版本/目标/key且阻止重复提交；`:155` 区分详情loading所有权与动作响应接受条件。关闭、身份变化、切店筛选及401刷新后的旧响应不能重放或污染当前操作。
- **接口、可见操作与测试贴合要求。** 前端全部请求透传`isRequestCurrent`，会员/员工共用面板同时使用角色、权限与服务端can*；训练/体测入口说明清楚。测试运行真实编译SFC、API和共享Axios刷新路径；后端使用真实JWT/MockMvc与独立事务，断言实际记录、版本、事件和权限结果，而非仅匹配源码文本。

## Issues

### Critical (Must Fix)

无。

### Important (Should Fix)

无。此前后端两项P1以及前端I1/M1均已关闭，本次整体复核未发现新的阻断缺陷。

### Minor (Nice to Have)

1. **M1 — 工作台分页中的逐计划查询可以后续合并。** `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:69` 为每个计划获取会员/教练/门店、动作数、有效训练数、最近训练和教练关系；`canArchive`还可能重复关系查询。虽然分页最大100且持久化上下文可复用部分实体，汇总及关系查询仍随计划数增长。建议在实测响应时间需要优化时，以批量统计/投影和复用权限判断减少查询，保持现有范围条件；目前没有延迟失败证据，属于非阻断优化。

## Recommendations

- 接受本轮实现审查，继续按根控制器既定门槛完成桌面业务验收、真实本地库备份及V22启用、制品一致性核验和文档收口；这些是计划内待执行交付工作，不是缺少实现。
- 最终交付应分别写明自动化、隔离PG、隔离HTTP、桌面及真实本地只读验收的实际结果。当前报告不宣称真实`fitness_db`已经迁移，也不代替尚在进行的桌面验收。
- 前端构建仍有主包超过500 kB的既有提示，日志未达到“零警告”；它不构成本轮训练功能阻断，不建议为消除提示扩大本次修改范围。

## Declined to judge

- **整个既有预约/排课模块所有交错操作的死锁自由性**：新记录路径采用会员→预约→排课锁，专项验证的是本模块明确列出的竞争场景；没有涵盖所有既有排课编辑与预约操作组合。已知签到/爽约仅处理BOOKED、取消拒绝ATTENDED，而本模块只关联ATTENDED，因此不能仅凭锁顺序差异认定存在可达死锁，也不能据此宣称全跨模块无死锁。本次未建立具体失败交错，不列推测性阻断；该全模块性质超出本轮29项变更的审查范围。
- **手机布局及触控交互**：规格明确仅验桌面，本次没有评价或测试手机。

## Assessment

**Ready to merge? Yes — implementation review approved.**

**Reasoning:** 本轮29项实现与绑定规格一致，权限、锁后校验、幂等、快照及UI生命周期的关键路径有代码和已通过的行为证据支持，未发现Critical/Important问题。该结论允许进入根控制器剩余交付门槛，不表示已经提交/合并、启用真实V22或完成尚未报告的桌面与本地启动验收。
