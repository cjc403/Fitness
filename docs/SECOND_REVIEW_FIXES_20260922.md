# 第二轮体检修复执行记录（4 项全部完成）

状态：**已完成并验证，尚未发布到线上。** 迁移 V10 已在本地正式库成功执行（全部为附加变更，未删任何数据）。

对应建议书：`docs/PROJECT_REVIEW_20260922.md`。

## 1. Git 版本控制

- 在开发根目录 `git init`，沿用并补充了已有的 `.gitignore`：
  新增忽略 `deploy/linux/*.rpm`、`deploy/linux/*.tar.gz`、`*.mv.db`、`~$*`、`logs/`，以及 **`.superpowers/`**。
- **`.superpowers/` 是必须先发现的坑**：它是别的工具留下的工作目录，里面有整份项目的历史快照——
  **18,610 个文件 / 428 MB，还包含 7 份 `node_modules` 副本**。它已被忽略（**文件本身没有删除**）。
- 提交前做了安全抽查：暂存文件里**没有** `node_modules` / `target` / `.tools` / `backups` / 二进制包；仓库里**没有生产密钥**
  （`application-prod.yml` 用的是 `${DB_PASSWORD}` 无默认值）。
- 基线提交 `fc31b2a`：**298 个文件**；并设置 `core.quotepath=false`，中文文件名正常显示。

> ⚠️ 唯一一处明文口令在 **`application-dev.yml`**（指向本地开发库 `fitness_db_dev`，非生产库）。
> **如果以后要把仓库推到 GitHub，先把这一行改成 `${DB_PASSWORD:}`。**

## 2. 开卡/下单幂等键

**问题**：幂等只存在于退款流程，收款反而更容易重复——`submitSale` 只有确认弹窗、没有提交中禁用，
连点或重试会生成两张卡 + 两笔收款 + 两份合同。

**改动**：
- 迁移 V10：`sales_order` 增加 `idempotency_key`、`request_fingerprint`，并加唯一约束 `(operator_id, idempotency_key)`。
- `SaleRequest` 增加必填 `idempotencyKey`；`createSale` 在**场馆锁之后**先查重：
  同键同内容 → 直接返回原单（这才是幂等）；**同键不同内容 → 拒绝**（`1001` 提示"幂等键已用于不同的销售内容"）。
- 指纹 = SHA-256(会员|商品|金额|渠道|备注)，与退款流程同一套思路。
- 前端：管理端开卡、会员自助购买、会员自助退款都改用 `utils/financeUi.mjs` 的安全随机键；
  **开卡弹窗补上了提交中禁用**（此前是缺的）。

## 3. 门禁二维码改为短时效一次性令牌

**问题**：二维码直接用 `member.access_token`——一次生成、永不轮换，**一张截图可以无限次入场**。

**改动**：
- 迁移 V10：新增 `access_grant` 表（token / 签发时间 / 过期时间 / 使用时间 / 使用人与门店）。
- `myAccessCode()` 每次签发 **60 秒有效**的随机令牌；`scanAccess()` 加行锁读取，校验"过期"与"已被使用"，
  并把**旧的永久令牌格式全部判为无效**；核销通过时一次性消费。
- 前端：会员端二维码带 **倒计时并自动刷新**，卡片上写明"一次性二维码：被扫过或超时即失效，截图无法重复入场"。

## 4. 站内通知中心

**问题**：排课改了、商品下架了、退款批了，会员都不知道——这也是"有预约的排课不能改时间"这类硬规则的根因。

**改动**：
- 迁移 V10：新增 `notification` 表（接收人 / 会员 / 门店 / 分类 / 标题 / 正文 / 关联对象 / 已读时间）。
- 新增 `module/notice`：实体、仓储、服务、控制器；接口 `GET /api/v1/notifications`、
  `/summary`（未读数）、`POST /{id}/read`、`POST /read-all`，鉴权 `isAuthenticated()`（会员与员工共用）。
- **已挂钩三个真实场景**：
  1. **排课时间或教练变更** → 通知该排课所有"待参加/候补"的会员（换教练是允许保留预约的变更，正是最需要通知的场景）；
  2. **商品下架** → 通知持有该商品有效权益的会员（说明已购权益不受影响）；
  3. **退款审核通过 / 被拒 / 登记支付** → 通知申请人。
- 前端会员中心新增「消息」页签，带未读角标与"全部标为已读"。

## 5. 顺带做的质量项

- **消 N+1**：`RefundService.list()` 与 `TrainingService.listLessons()` 原先按行 `findById`（退款还按分摊逐条查订单），
  改为"批量捞 + Map 组装"；单条路径仍复用同一装配方法，行为不变。
- **补索引**（V10）：`sales_order.product_id`、`member_entitlement.product_id`、`member_entitlement.source_order_id`、
  `private_lesson.schedule_id`、`private_lesson.venue_id`、`refund_application.entitlement_id`。
- **文档同步**：`后续完善计划.md` 顶部加"历史规划存档"横幅；新建 `docs/当前功能与待办.md` 作为现状事实来源。

## 验证

| 门槛 | 结果 |
| --- | --- |
| 后端 `mvn -B clean package` | **85 / 85 测试通过**（+3 新增），BUILD SUCCESS，fat jar 71,339,562 字节 |
| 前端 `npm run build`（含 vue-tsc） | 退出 0 |
| 前端 Node 测试 | 8 / 8 |
| **迁移 V10 落库** | `flyway_schema_history` V10 success=t；`access_grant`、`notification` 两表已建；`sales_order` 两个新列已加；**7 个索引全部到位** |
| 门禁二维码 | 连续两次取码 **token 不同**，ttl=60 秒（旧实现是永久同一令牌） |
| 通知接口 | cjc2 与 admin 均 200、code 0，未读数 0（尚无触发事件） |
| 开卡幂等 | 不带幂等键 → `400 + 1001 {'idempotencyKey': '不能为空'}`；带键 + 无效会员 → `2100 会员不存在`（先查幂等再查会员） |

新增回归测试 3 个：
- `duplicateSaleSubmissionsCollapseIntoOneOrderByTheIdempotencyKey`：同键重复提交返回**同一张单**；同键换内容被拒。
- `accessQrOnlyAcceptsShortLivedSingleUseGrants`：两次取码令牌不同；旧式 32 位永久令牌被判"无效二维码"。
- `changingScheduleCoachNotifiesBookedMembers`：会员预约后改教练 → 会员收到 `SCHEDULE_CHANGED` 站内消息。

### 过程中修正的测试（都是"行为变更导致旧断言失效"，不是产品缺陷）
- 6 处测试里的开卡调用补上幂等键（其中用随机键保证各用例互不干扰）。
- `RefundFinanceIntegrationTest` 5 处扫码改用 `freshToken()` 新签发令牌——它原先复用一个永久令牌连扫 3 次，
  正好被本次安全加固判定为重复使用。
- 我自己的两个新测试：会员自助购买补幂等键；买商品时改为**只挑本门店的商品**（会员不能跨门店购买）。
