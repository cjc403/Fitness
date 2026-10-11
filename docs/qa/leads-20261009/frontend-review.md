# 潜客与体验课前端独立审查

审查日期：2026-10-09。审查对象为当前工作区文件；没有以 HEAD diff 代替文件内容，也没有修改实现、启动服务、写业务数据、提交或部署。依据 AGENTS.md、已批准设计、实施计划和 frontend-report.md。

结论：发现 2 项需要修复的问题，其中 1 项涉及失效会话的资料展示。其余已检查的契约、请求意图捕获、同键重试、阶段限制、收款渠道、路由与菜单角色交集没有发现可证明的新缺陷。浏览器及真实 HTTP 闭环尚由 root 验收。

## F1 [P1] 会话失效后仍展示上一账号的潜客资料

- 定位：`fitness-frontend/src/views/admin/LeadCenter.vue:111-112`，关联模板第 2、27-30、41-60 行。
- 当前仅以 `sessionIsCurrent` 阻止新请求及旧请求回写；没有监听 `auth.sessionGeneration` 清空已加载资料、表单及订单候选，也没有在模板上限制失效会话的渲染。
- 复现：加载工作台，打开潜客档案及资料编辑；改变 sessionGeneration，并将 userInfo 清空。使用 Vue 真实 reactive auth 和 nextTick 执行现有 SFC setup 后，工作台 `records`、`detailData`、`current` 和 `operationForm.phone` 仍含旧手机号，两个对话层也仍为打开状态。
- 实际路径：现有 auth store 的 logout 先 clearSession，再 await logoutApi，最后才跳转登录页。后端退出请求慢或超时期间，当前页面可继续展示已经退出账号的姓名、手机号、备注、跟进和订单资料。账号替换但路由组件未卸载时，也会保留旧账号展示。后续请求已被阻止，不能补足展示隔离。
- 动态输出：`SESSION_INVALIDATION {"records":["13800000000"],"detailPhone":"13800000000","detailDialog":true,"operationDialog":true,"currentPhone":"13800000000","formPhone":"13800000000"}`。
- 建议：在会话 generation 变化时同步清空所有敏感状态并关闭对话层、使序列失效；或通过会话是否有效的渲染门控确保旧资料立即不可见，同时处理锁定状态与定时器。无需改现有业务 API。
- 最小回归：先成功加载列表、详情、选项、订单候选和表单，再用 reactive auth 触发会话替换及 nextTick；断言旧资料不可渲染且不保留可见对话层。随后完成一个未返回的旧请求，断言仍不会显示旧资料、弹消息或发刷新。现有第 90-102 行测试只验证阻止新请求与晚返回发布，未验证已加载资料清除；第 94 行反而要求失效后旧操作框保持打开。

## F2 [P2] 总部建档选择一次门店后无法在同一表单改选门店

- 定位：`fitness-frontend/src/views/admin/LeadCenter.vue:185`，关联第 66、192 行。
- 建档的门店下拉框使用 `operationOptions.venues`。`changeCreateVenue` 按新门店调用 options 接口，随即用该门店的完整响应替换 `operationOptions`。当前后端 `LeadService.options:80` 会按传入 venueId 筛选 venues，只返回所选门店。
- 复现：总部账号、至少两门店、工作台筛选为全部门店；打开新建潜客，初始下拉可见门店 1 与 2。选门店 1 后，顾问选项刷新成功，门店下拉只剩门店 1，且下拉没有 clearable，无法改选门店 2。需要关闭重开并重填其他字段。
- 动态输出：`HQ_BEFORE [1,2]`，选择门店 1 并完成 scoped options 响应后 `HQ_AFTER [1]`。复现使用当前 SFC、真实 API 封装以及与当前后端一致的 scoped options mock。
- 建议：总部建档门店列表使用独立的完整门店集合，保留全部允许门店；所选门店仅影响负责顾问和其他业务选项，不替换门店选择器的数据源。
- 最小回归：初始 options 返回两门店；选店 1 时 scoped options 只返回店 1，断言仍可选择店 2；改选店 2 后断言顾问列表归店 2、旧 ownerId 不保留，提交携带店 2 与该店顾问。可另覆盖从工作台先筛选店 1 再建档的情况。

## 验证与审查边界

- 独立重跑 `node --test tests/leads-runtime.test.mjs`：20/20 通过，0 失败/跳过。
- 动态复现没有修改测试文件：在内存中读取测试 harness，执行当前 TypeScript API 模块及 SFC setup；会话复现将 auth 替换为 Vue reactive，以真实 nextTick 检查响应式状态。
- 现有运行测试实际执行 API 和 setup 的 TypeScript 代码，使用 Vue 的真实 ref/reactive/computed，不是静态匹配业务分支；但未编译、挂载或操作 template/Element Plus，因此不能作为桌面/390px布局、弹层交互或完整浏览器验收的证据。路由和菜单测试实际执行配置及权限函数。
- 已检查：固定字段不可变、expectedVersion、捕获目标与冻结 payload、重复点击、同意图重试键、过期日期原样重试、上海时间校验、LOST/CONVERTED 与 BOOKED 操作限制、LINK 字段边界、CARD/WECHAT/ALIPAY/CASH、跨请求序列隔离，以及新入口权限。
- 未重复运行完整前端构建；frontend-report.md 的 89 项全测试和构建成功是实现者提供的证据。本审查不将其冒充为独立执行结果，也不将静态响应式 CSS 检查视为390px浏览器通过。

## F1 / F2 修复复核（2026-10-09）

本次只复核上述两项修复及其引入的相关回归；读取当前实现与测试，未修改产品代码或业务数据。

- **F1：ADDRESSED。** `LeadCenter.vue:95-96、115-125` 捕获初始账号范围，并用同步 watch 监测 generation、账号 ID、角色、organizationId 和权限。失效时立即清空列表、详情、检索、草稿、选项、订单候选，关闭两层弹框，停止时钟并使异步序列失效；模板第 2 行通过 `sessionExpired` 永久停止本组件旧会话的渲染。新增真实 reactive auth 回归（测试第 100-124 行）在退出/换账号/换门店范围/换角色后检查已加载私密资料清除，并完成旧写请求验证没有消息、回写或刷新。相同 generation、相同权限范围的 userInfo 刷新按当前 watch 条件不会失效；权限顺序通过排序消除无意义差异。
- **F2：ADDRESSED。** `LeadCenter.vue:66、107、196-202、207、216` 将建档门店选择器改为独立 `createVenueOptions`；总部从已筛选门店进入时额外获取完整门店集合，后续 scoped options 刷新只改变所属顾问等选项，不缩减门店选择器。新增测试第 125-145 行覆盖全部门店与预筛选门店两种入口、店 1 → 店 2、字段保留、归属顾问重置、最终写入店 2，以及旧店选项晚成功/晚失败被丢弃。
- **独立验证：** 本次执行 `node --test tests/leads-runtime.test.mjs`，23/23 通过，0 失败/跳过，退出码 0。未发现本次两项修复范围内的新可证明缺陷。
- **证据边界：** 此结论为代码与运行行为复核；未独立重跑完整 92 项或构建，亦未代替 root 的真实 HTTP、桌面与390px浏览器验收。初审中的 F1/F2 保留作为修复前的问题记录，当前状态以上述复核为准。
