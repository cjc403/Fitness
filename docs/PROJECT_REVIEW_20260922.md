# FitChain 项目改进建议（第二轮体检）

状态：**仅诊断与建议，未做任何代码改动**（按你的要求，等你同意再动手）。

体检方式：实地读代码 + 查库 + 查现有文档，每条都给出可核对的证据。不采用 `docs/后续完善计划.md` 的勾选状态——
那份文档已经和实现脱节（见 P1-3）。

---

## P0 · 建议尽快做（低风险、高收益）

### 1. 项目没有版本控制（我认为这是当前最该补的一件事）

**证据**：`fitness-feature-hh/.git` 不存在。现在的"版本管理"是 `backups/` 下按日期手工复制目录，
仅今天一天就新增了 5 个快照目录（`source-before-course-crud-20260921` … `source-before-private-training-guide-20260922`）。

**风险**：改错了只能靠手工对比目录；无法 diff "这周到底改了什么"；没法回滚到某个中间点；
将来交接给别人或多人协作基本无从下手。

**建议**：`git init` + 一份 `.gitignore`（排除 `target/`、`node_modules/`、`dist/`、`logs/`、`backups/`、`data/`、`*.mv.db`），
然后按现有阶段补几次提交。**注意**：项目现有约定是"不是 Git 仓库、不擅自 init"，所以这条必须你明确同意。

### 2. 开卡/下单没有幂等键（直接的资金重复风险）

**证据**：
- `idempotencyKey` 在整个后端只出现在退款流程（`finance/dto/FinanceDtos.java`、`RefundApplicationEntity`、`RefundService`），
  数据库上还配了唯一索引 `refund_application_requested_by_idempotency_key_key`；
- 开卡用的 `SaleRequest` **没有幂等字段**，`sales_order` 只有 `order_no` 唯一索引；
- 前端 `CommercialCenter.vue` 的 `submitSale` 只有一句确认弹窗，**没有提交中禁用**：

```js
const submitSale=async()=>{...await ElMessageBox.confirm(...);await createSale(saleForm);...}
```

**风险**：确认后按钮仍可再次点击（`await` 期间弹窗已关闭、按钮可点），连点两次或网络重试 →
**生成两张卡 + 两笔收款 + 两份权益 + 两份合同/收据**。
有意思的是：**退款比收款更安全**（退款有幂等键）。今天我又给会员端加了自助购买入口，这个风险面比之前更大了。

**建议**：给 `SaleRequest` 加 `idempotencyKey`（复用退款那套写法：`(operator, key)` 唯一约束 + 指纹校验），
前端用 `utils/financeUi.mjs` 里已有的 `refundIntent()` 同款随机键；并在提交期间禁用按钮。

### 3. 门禁二维码永久有效、可截图复用

**证据**（`CommercialService.myAccessCode` / `scanAccess`）：

```java
if (member.getAccessToken() == null || member.getAccessToken().isBlank()) {
    member.setAccessToken(UUID.randomUUID().toString().replace("-", ""));
    members.save(member);
}
return new AccessCodeResponse(..., member.getAccessToken(), "FITCHAIN://ACCESS/" + member.getAccessToken(), ...);
```

`accessToken` **一次生成、永不轮换**；`scanAccess` 只按字符串查会员，没有时效、没有一次性、没有 nonce、没有设备绑定。

**风险**：截一张二维码可以无限次入场；token 泄露即等于会员身份被冒用。这是项目已知技术债（路线图里也写了"防截图、设备签名"）。

**建议**：改成**短时效一次性令牌**——会员端每次进入页面请求一个 60 秒有效、用后作废的 `nonce`（存库或 Redis），
`scanAccess` 校验在场馆维度内一次性消费；二维码里带签发时间与随机串。这是本项目里最值得做的一次安全加固。

---

## P1 · 质量与性能（不影响功能，但值得清理）

### 1. 列表接口存在 N+1 查询

**证据**：`RefundService.list()` 与 `TrainingService.listLessons()` 都是
`list.stream().map(this::response).toList()`，而 `response(...)` 里逐行 `findById`：

- `RefundService.response`：每行 1 次 `entitlements.findById` + 1 次 `members.findById`，**每条分摊再各 1 次 `orders.findById`**
- `TrainingService.response`：每行 1 次 `schedules.findById` + 1 次 `members.findById` + 1 次 `users.findById`

**影响**：50 条退款记录 ≈ 200+ 次查询；数据量上来后列表页明显变慢。
**建议**：先 `findAllById` 批量捞，用 `Map` 组装，再做 map。改动集中在两个 `response` 方法，风险低。

### 2. 部分外键列没有索引（今天新加的守卫正好查这些列）

**证据**（查 `pg_indexes` / `pg_constraint`）：`sales_order.product_id`、`member_entitlement.product_id`、
`private_lesson.schedule_id`、`private_lesson.venue_id`、`refund_application.entitlement_id`、
`member_entitlement.source_order_id` 等列无索引。

**建议**：一条 `V10` 迁移补 4–6 个索引即可（`CREATE INDEX IF NOT EXISTS`）。当前数据量小，属于"顺手做掉"。

### 3. 文档与实现脱节

**证据**：`docs/后续完善计划.md` 里「会员端功能」下的"约课功能 / 我的预约列表 / 个人信息管理"、
「教练端」的"课程表查看 / 课程统计"等仍标 `[ ]` 未完成，但这些**早就上线了**；
反过来，V9 退款精确价值账、会员自助购买/退款、私教消课自检、商品删除/下架、排课与教练账号绑定等**新能力一个都没进这份文档**。

**风险**：看文档会严重误判项目状态——你做组会汇报或交接时最容易踩这个坑。
**建议**：把这份文档改成"历史规划（存档）"，另起一份《当前功能与未完成项》作为事实来源；
同时把今天的 6 组新功能补进 `docs/开发指导手册.md`。

### 4. 前端测试几乎是空白

**证据**：`fitness-frontend/tests/` 只有 2 个文件（`finance-ui.helpers.test.mjs`、`finance-runtime.test.mjs`），
没有任何组件测试；后端 83 个用例挤在 5 个测试类里。
**建议**：前端至少覆盖"金额/时间格式化、状态文案映射、排课时间计算"这类纯函数（成本极低），
关键的会员自助流程用一个组件冒烟测试守住。不必追求覆盖率数字。

---

## P2 · 产品能力（按你的优先级挑，不一定现在做）

| 项 | 为什么值得做 |
| --- | --- |
| **通知中心（站内信）** | 你最近连续撞到三次同类问题：排课改了、商品下架了、退款要审核——**会员都不知道**。没有通知渠道，很多业务规则只能"拒绝操作"（比如有预约的排课不能改时间）。这是解锁一批功能的前置项 |
| **课程评价** | 有预约与出勤数据后可自然延伸；也是会员端的"活跃度"抓手 |
| **发布计划** | 本地已远超线上（按项目备忘线上仍是 V8 私教闭环版），今天又新增 6 组功能。建议规划一次上线，把 V9 + 本轮能力一起发布（**需要你单独授权**） |

## 不建议现在做的

- 第三方支付接入、微信小程序、移动 App、i18n 多语言、ELK/Prometheus 全家桶——
  现阶段收益远低于成本，等有真实运营需求再说。

---

## 我建议的执行顺序

1. **Git 管起来**（一次性的地基，越早越省事）
2. **开卡幂等键** + **二维码短时效**（两个都是"钱/身份"相关，值得优先）
3. 文档同步 + 消 N+1 + 补索引（半天量级，一次做完）
4. 通知中心（做得动就做，是很多功能的解锁键）

请告诉我做哪几条。**在你说同意之前，我不会改任何代码。**
