### Spec Compliance

- ❌ Issues found：`fitness-frontend/src/components/TrainingPlanPanel.vue:83` 的创建操作在等待候选学员后继续读取共享候选集合并赋值，但未校验原操作是否仍有效。旧操作可以改动重开后的新表单，违反规格的动作 generation、关闭/重开及跨会员表单隔离要求。见 Important I1。
- ✅ 八个变更文件均有对应实现：类型和全部 13 个请求变体位于 `fitness-frontend/src/api/modules/trainingPlans.ts:1`、`:20`；员工菜单 `fitness-frontend/src/layouts/AdminLayout.vue:93`，路由 `fitness-frontend/src/router/index.ts:69`；教练入口 `fitness-frontend/src/views/coach/CoachWorkbench.vue:28`、`:66`；会员页签 `fitness-frontend/src/views/member/MemberHome.vue:65`；入口查询提示解析 `fitness-frontend/src/views/admin/TrainingPlanCenter.vue:7`；共享面板和真实编译测试均已提供。
- ✅ 权限、DTO、数字/日期、动态模式、跳过原因、可选预约清空、历史分页、会员反馈等可从本审查包验证，具体证据如下。未发现额外依赖、公共 request 修改或本任务之外的业务功能。
- ⚠️ Cannot verify from diff：真实桌面交互、最终全套测试/build及服务端最终授权由根控制器独立验收；本审查不将编译测试视为浏览器验收，不覆盖手机、真实数据库或云端。

### Strengths

- `fitness-frontend/src/api/modules/trainingPlans.ts:20` 至 `:31`：请求路径、参数和返回类型对应实际契约，员工/会员 detail 分支覆盖两个请求变体，全部读写传递 `SessionRequestConfig`，没有引入通用会员管理 API。
- `fitness-frontend/src/components/TrainingPlanPanel.vue:33`、`:37`：权限采用当前会话、角色、权限目录和服务端 can* 交集；MANAGER/HQ 仅有受控归档，会员仅有效记录反馈；`:49`、`:63`、`:74` 另行抑制会员草稿、审计和员工操作。`:199` 复用原体测组件并以 canViewBody 控制入口。
- `fitness-frontend/src/components/TrainingPlanPanel.vue:50` 至 `:58`：清理覆盖私密列表、详情、动作、候选、预约和表单，身份/组织/权限变更同步禁用旧实例。列表、详情、候选、预约和写入的请求门控分别位于 `:62`、`:73`、`:90`、`:95`、`:131`，显式冲突重载也透传门控 `:143`、`:148`。
- `fitness-frontend/src/components/TrainingPlanPanel.vue:99` 至 `:125`：整数、重量精度、日期窗口、动作数量/ID集合、至少完成一项、跳过原因和预约候选均做精确组装；可选 bookingId 的 undefined/null 归一化在 `:123`，0kg 和缺失值显示分离在 `:151` 至 `:153`。
- `fitness-frontend/src/components/TrainingPlanPanel.vue:127` 至 `:137`：single-flight 冻结嵌套请求、版本、对象和幂等键；未编辑的失败重试在重新进行日期校验前复用冻结请求，2600 要求显式重载；旧重载的内容覆盖受动作 epoch 控制 `:148` 至 `:149`。
- `fitness-frontend/src/components/TrainingPlanPanel.vue:162` 的 Hero 控件父容器避免直接 select 根节点布局冲突；`:164` 使用真实统计，`:168`、`:179`、`:180` 显示列表/历史总数和最近100条审计边界，`:175` 至 `:178` 明确独立训练、不签到扣课、目标/实际、作废和反馈状态。
- `fitness-frontend/tests/training-plans-runtime.test.mjs:10` 至 `:25` 使用真实 SFC/API 编译执行；`:62` 至 `:70` 进一步运行真实共享 Axios request 的401刷新/重放路径，`:71` 检查全部13请求的门控，`:72`、`:74`、`:75` 覆盖跨日期重试、可选预约清空及旧重载不能解锁新冲突。

### Issues

#### Critical (Must Fix)

- 无。

#### Important (Should Fix)

- **I1 — 旧创建操作的候选加载后续赋值会覆盖新表单的学员。** `fitness-frontend/src/components/TrainingPlanPanel.vue:83`：`await loadStudents(1)` 后直接执行 `candidateIds.has(props.initialMemberId)` 和 `form.memberId=props.initialMemberId`。`loadStudents` 自身的门控只阻止旧响应写入列表；它正常返回后，旧 `openAction` 仍会读取新操作填充的共享 `candidateIds`。复现：路由提示学员20 → 第一次创建的候选请求悬而未决 → 关闭并重开创建 → 新请求返回学员20/21 → 用户选择21 → 第一次请求完成 → 当前选择变成20。随后保存可能为错误学员建立计划。应在启动创建时捕获 scope/action epoch/操作身份，并在 await 后验证仍属于同一次 create；自动选择提示还应避免覆盖用户已作出的选择。新增覆盖 `openAction` 整个异步流程的回归测试，包含旧成功和旧失败；现有测试 `fitness-frontend/tests/training-plans-runtime.test.mjs:54` 只验证 `loadStudents` 的结果，不覆盖此后续赋值。

#### Minor (Nice to Have)

- **M1 — 旧冲突重载的 finally 可清除新详情加载状态。** `fitness-frontend/src/components/TrainingPlanPanel.vue:149`：finally 仅检查 `scope()`，没有检查对应详情/动作 generation。用户关闭旧冲突操作并加载另一计划时，旧 reload 结束仍会将共享 `detailLoading` 设为 false；新详情请求仍进行却不再显示加载遮罩。内容覆盖已有门控，这里主要是加载反馈不准确。建议使用独立重载 loading 状态或带详情 generation 的加载状态所有权，避免旧操作清理新操作的状态；同时保留成功重载关闭旧 action 后的正确清理。

### Checks and Evidence

- 审查基线采用提供的共享脏工作区快照，base/head 均为 `b75b1ee`；逐一读取 package.json 指定八个补丁，没有重新执行 git diff、没有修改 Git 状态。新文件补丁为全文；面板输出曾截断，仅补读补丁中 `:148` 至 `:154` 的截断函数，没有重复读取业务源文件。
- 绑定检查：task-2-brief/context/report、设计规格和 `docs/qa/training-plans-20261011/api-contract.json`。
- 一次包外聚焦检查：风险是复用体测组件时会员的 undefined memberId 是否误走员工接口。读取 `fitness-frontend/src/components/BodyMeasurePanel.vue:100`、`:101`、`:164`、`:178`、`:194`、`:205` 的 prop/分支匹配，确认 undefined 使用原会员接口、具体 ID 使用原员工接口；未修改或扩大旧接口权限。
- 一次代码疑点专用运行：在 frontend 目录，以 `E:/node/node.exe --input-type=module` 通过内存脚本复用审查包测试 harness，编译实际 SFC/API，只运行 I1 的关闭/重开候选加载场景；没有生成测试文件、没有重跑全套。实际输出 `Before stale initial create completes: 21` / `After stale initial create completes: 20`，断言失败 `20 !== 21`，进程退出1，确认 I1。
- 工作者报告的最终173/173和build通过已读取，未重复运行；已继承主包体积 advisory 按控制器约定不作为本功能缺陷。根控制器独立最终验证结果不在本报告中代为宣称。

### Assessment

**Task quality:** Needs fixes

**Reasoning:** 主体权限、契约和异步请求保护实现完整，测试包含真实编译和401重放；但创建动作在候选请求之后缺少同等生命周期门控，已实证可以覆盖新选学员并导致错误对象提交。I1 修复并新增对应回归后方可通过任务级审查；M1 为非阻断加载状态问题。
