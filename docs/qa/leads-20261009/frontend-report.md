# 潜客与体验课：前端实现与验证

日期：2026-10-09。范围为计划 Task 2；只在现有本地工作区实现，未提交、推送、部署，未访问真实业务数据库。

## 文件范围

- 新增 `fitness-frontend/src/api/modules/leads.ts`：完整固定契约，沿用现有 request 与 Result 包装。
- 新增 `fitness-frontend/src/views/admin/LeadCenter.vue`：工作台、任务卡片、关键词/阶段/门店/分页、渠道统计、独立档案抽屉、建档/资料/跟进/体验/结果/开卡操作。
- 新增 `fitness-frontend/tests/leads-runtime.test.mjs`：实际执行 TypeScript API 和 Vue SFC setup，替换 HTTP、浏览器与生命周期边界；没有以静态正则检查替代业务行为验证。
- `router/index.ts` 与 `layouts/AdminLayout.vue`：仅追加 `/leads` 入口“潜客与体验课”，同时要求 LEAD_READ 与 SALES/MANAGER/SUPER_ADMIN 角色；保留先前全部脏改动。

## RED → GREEN 证据

1. `node --test tests/leads-runtime.test.mjs` 首轮 14 失败 / 0 通过：API/page 不存在及 route 未注册。记录在 `.superpowers/sdd/2026-10-09-leads/frontend-red.log`。
2. 完成页面与 API 后首轮 14/14 通过。
3. 后端确认既有会员兼容校验：姓名最多50字、大陆11位手机号；新增回归先失败（51字姓名被发送），再收紧输入和校验。记录 `frontend-validation-red.log`。
4. root 修正收款渠道为现有 CARD/WECHAT/ALIPAY/CASH；新增回归先失败（BANK_TRANSFER 被发送），再修改类型与选项。记录 `frontend-payment-red.log`。没有自行变更 API。
5. 自查发现预约开始时，已打开的结果对话框依赖缓存时钟；先增加“时间越过开始时间”行为回归并看到失败，再加入可卸载的会话隔离时钟。记录 `frontend-clock-red.log`。
6. 最终 `node --test tests/*.test.mjs`：89/89 通过（新潜客20项 + 现有69项），0失败/跳过。完整输出 `frontend-green.log`。
7. 最终 `npm run build`：vue-tsc 与 Vite 成功，退出0。完整输出 `frontend-build.log`。Vite 提示既有主包超过500kB，不影响构建成功。

## 行为覆盖

- 单次写入期间忽略重复点击，锁定潜客/预约与冻结 payload；每个非建档写入携带 expectedVersion，所有写入携带安全随机 idempotencyKey。
- 同一失败意图重试复用键，编辑内容、重新打开、成功后新操作使用新键。已经从未来变成过去的体验时间仅允许原样失败重试；新/已编辑意图仍被阻止。
- 旧列表、旧详情、旧选项及旧订单候选的成功/错误不会覆盖新选择；换账号/卸载后不再发请求，也不发布旧请求结果、关闭新对话框或触发刷新。
- 资料字段/阶段/结果/方法/来源/意向、空内容、非法/过去日期、上海时间、15—180分钟、接待人范围、手机号/姓名长度均有运行验证。
- SALES 不能改归属，可选本门店其他接待顾问。手机号/渠道/门店不进入 profile 修改 payload。
- 已开卡不再跟进；流失需重新跟进；BOOKED 待体验必须先处理才能开卡/流失，不提供手动已开卡阶段。
- NEW 真实收款渠道与金额校验；LINK 只发送已经 API 核验的 orderId，不发送可覆盖既有会员或支付资料的字段。
- 结果准确捕获预约ID，开始前只能取消，开始后支持 ATTENDED/NO_SHOW；等待到开始时间后选项自动可用。
- 版本冲突停用保存并提供“重新加载最新档案”；非预期服务错误使用中文安全提示，保留失败意图便于原样重试。
- 路由和菜单都验证权限与角色交集，额外 MEMBER_READ 不会使前台/教练/会员进入潜客页。

## 自查结论与验收边界

保留当前 WorkspaceHero、动漫资源与主题；桌面多列工作台在小屏降为单/双列，表格可横向滚动、档案宽度不超过视口。日期以服务器上海本地时间展示/验证。收款文案明确为实际收款登记，不启动新支付网关；会员编号 LEAD-{潜客ID}，不自动开登录账号。

事件类型已与后端确认 CREATED/PROFILE/FOLLOW_UP/TRIAL_BOOKED/TRIAL_RESULT/CONVERTED；固定5来源渠道（包括0人数）可正确呈现。版本初始0、每个成功写入+1，由服务端提供并原样传回。

Task 2 编译和运行行为验证完成。真实 HTTP 闭环、桌面/390px截图与浏览器控制台由 root 的隔离 Task 3 验收；本报告不把它们标为已验证。

## 独立审查修复 F1/F2（2026-10-09）

原始独立审查位于 frontend-review.md。两项均已核对当前代码并按实际复现修复；此前自查未涵盖“已加载数据在退出等待期间的展示”和“真实 scoped options 返回单门店”场景，原测试范围不足。

F1：SFC 现在用 flush:sync 监听 sessionGeneration 及用户 ID/角色/组织/权限范围；会话或范围变化时立即使旧页面失效、隐藏其渲染，清除列表、统计、详情、当前潜客、体验、表单、选项、候选订单、门店列表、检索词、错误和重试意图，关闭对话层、清空锁定状态，作废全部请求序列并停止时钟。卸载也执行清理。同一用户和权限范围的令牌刷新不触发失效。

F1 回归改用真实 Vue reactive auth 和 effectScope，先成功加载列表/档案/选项与候选、打开资料草稿、发送未返回写入，再模拟退出、账号替换，以及 generation 不变但组织/角色权限变化。RED 的旧 records 仍1（预期0），旧 modal 仍true（预期false）；GREEN 验证同步清空（nextTick前）及 nextTick 后所有旧资料/弹层消失，晚到响应不发布消息/刷新/私密资料，时钟确实停止。原先“失效后旧对话框仍打开”的断言已纠正。日志 frontend-session-review-{red,green}.log。

F2：新建表单使用独立 createVenueOptions 保存当前权限内完整门店集合，所选门店的 options 只刷新本店顾问/产品，不覆盖全门店选择器。先在工作台筛选某店再建档时，同时获取当前 scoped 选项和完整可选门店；改店清除旧归属人并重新选择该店顾问，保留其他已填字段；所有响应仍受选项序列/操作序列/会话隔离保护。

F2 回归使用与后端一致的响应：全局[1,2]，scoped店1仅[1]，scoped店2仅[2]。RED 原选择器变成[1]/[2]（预期[1,2]）；GREEN 允许在同一表单由店1改店2，ownerId换为店2顾问，姓名/备注保留，提交venueId2及该店ownerId。另覆盖工作台先筛选店1再建档、旧门店成功/失败响应晚到均不覆盖新店顾问。日志 frontend-venue-review-{red,green}.log。

修复后一次完整验证：node --test tests/*.test.mjs → 92/92通过（潜客23项+现有69项）；npm run build → 退出0，vue-tsc/Vite成功。完整日志 frontend-review-final-green.log 与 frontend-review-final-build.log。没有 backend/其他功能改动、真实数据库写入、提交或部署。此为实现者修复证据，等待原独立审查者范围复审与 root 的浏览器验收。

## 协调者最终验收补充

原独立审查者已关闭F1/F2。桌面实际渲染发现现有全局主题将查询按钮的蓝色字叠在蓝色渐变上；仅在LeadCenter的scoped CSS限定本页plain primary按钮为浅底深字。真实计算样式为字色rgb(36,97,164)、底色rgb(237,245,255)、无背景渐变。此次样式修改后重新执行完整前端92/92及vue-tsc/Vite构建，退出0，日志frontend-final-{tests,build}.log。

隔离PG真实浏览器已完成总部建档、切店、跟进、体验预约/取消与阶段恢复、历史保存、顾问登录及归属限制、搜索和今日任务筛选；开卡表单检查文案，资金闭环由实际HTTP验收覆盖。验收开始后无页面运行错误。桌面截图和范围见browser-verification.json。用户随后明确手机布局不用默认检查，后续只检查桌面，已完成的手机截图保留历史记录。
