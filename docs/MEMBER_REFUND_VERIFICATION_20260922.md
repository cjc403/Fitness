# 会员自助退款验证记录

状态：**已完成并验证，尚未发布到线上。** 未改数据库结构、未改 Flyway 迁移、未触碰服务器。
本地正式库中**没有提交任何退款申请**。

## 现象

会员端「我的退款」页签只有一张空表，文案写着"退款申请请联系前台"——
会员既看不到自己的退款进度（因为从来没有记录），也没有任何发起退款的入口。

## 根因（两层）

**第一层：会员侧没有自助退款接口。**
`FinanceController` 里的退款接口全部要求 `REFUND_REQUEST` / `REFUND_APPROVE` / `REFUND_PAY` 权限：

| 接口 | 权限 | 会员可调用 |
| --- | --- | --- |
| `GET /finance/entitlements/{id}/quote`（退款报价） | `REFUND_REQUEST` | ❌ 403 |
| `POST /finance/refunds`（提交申请） | `REFUND_REQUEST` | ❌ 403 |
| `GET /finance/me/refunds`（本人进度） | `MEMBER_SELF_SERVICE` | ✅ 200 |

**第二层：权限矩阵里会员确实没有任何退款权限。**
`security/RolePermissionCatalog` 是角色权限的唯一事实来源（登录时写入 JWT，同时驱动 `@PreAuthorize`）。
其中 `MEMBER` 只有 `COURSE_READ`、`MEMBER_SELF_SERVICE`、`ASSISTANT_USE` 三项。

实测（本地正式库）：

| 账号 | 登录权限数 | 退款/价值类权限 | `GET /finance/refunds` | `GET /finance/entitlements/1/quote` | `GET /finance/me/refunds` |
| --- | --- | --- | --- | --- | --- |
| admin | 30 | REFUND_REQUEST / APPROVE / PAY、VALUE_RECONCILE / TRANSFER_VERIFY | 200 | 200 | **403**（刻意只给会员） |
| manager | 27 | 同上（无 VALUE_TRANSFER_VERIFY） | 200 | 200 | **403** |
| member | 3 | 无 | **403** | **403** | 200 |

也就是说：**员工侧的退款受理是通的，缺的是"会员能不能自己发起"这一条路径。**

## 改动内容

### 后端（复用既有的精确价值账逻辑，不新开资金路径）

| 文件 | 改动 |
| --- | --- |
| `RefundService.java` | 新增 `ownEntitlement` / `currentMemberId` 归属校验；新增 `memberQuote` / `memberRequest` / `memberCancel`，**内部直接调用员工侧同一套 `quote` / `request` / `cancel`** |
| `FinanceController.java` | 新增 `GET /finance/me/entitlements/{id}/quote`、`POST /finance/me/refunds`、`POST /finance/me/refunds/{id}/cancel`，均要求 `MEMBER_SELF_SERVICE`（会员已有该权限，**无需改权限矩阵**） |

两条关键的安全边界：

1. **归属校验**：`values.lock()` 只校验"门店范围"，不校验持有人，所以必须显式确认该权益属于当前登录会员，
   否则同门店会员之间可以互相发起退款。测试与线上都验证了这一点。
2. **金额不可篡改**：沿用 `RefundService.request` 的原有逻辑——`amountCents = 价值账余额`，
   由服务端核定；会员提交的请求体里**根本没有金额字段**（`RefundRequest(entitlementId, version, reason, idempotencyKey)`）。
   同时保留幂等键、价值账版本乐观锁、来源资金核对与"未完成服务"阻断。

### 前端

| 文件 | 改动 |
| --- | --- |
| `api/modules/finance.ts` | 新增 `getMyEntitlementQuote` / `createMyRefund` / `cancelMyRefund` |
| `views/member/MemberHome.vue` | 「我的退款」页签新增**申请退款**区：选择本人会籍 → 获取退款报价（显示服务端核定金额、核对状态、阻断原因）→ 填写原因 → 提交；进度表新增「撤回」（仅待审核可撤回）。复用了员工侧的 `refundIntent()` 生成安全随机幂等键 |

## 验证

- 后端 `mvn -B clean package`：**80 / 80 测试通过**，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS（fat jar 71,308,391 字节）。日志 `logs/wb-mrefund-gate.log`。
- 新增回归测试 `FitnessApplicationIntegrationTest#memberCanOnlyRequestRefundOnOwnEntitlement`：
  员工不能用会员自助接口、会员不能用员工受理接口；报价返回服务端版本号；
  不存在的权益被拒；**同门店其他会员的权益被拒（"只能对自己名下的权益申请退款"）**；提交接口对会员开放且结果为业务层结论。
- 前端 `npm run build`（含 `vue-tsc`）退出 0；Node 测试 8/8。日志 `logs/wb-fe-build9.log`、`logs/wb-fe-tests10.log`。
- **线上只读验证**（本地正式库，未提交任何申请，日志 `logs/wb-mrefund-online-check.txt`）：

| 步骤 | 结果 |
| --- | --- |
| cjc2 本人会籍 | id=19 无限年卡（ACTIVE）、id=17 年度畅练卡（ACTIVE） |
| 自助报价 id=19 | 200 / code 0，可退 ¥39999.00，核对状态 VERIFIED，版本 2，阻断原因：**会员有未完成服务预约（含团课）** |
| 自助报价 id=17 | 200 / code 0，可退金额 `null`，核对状态 **UNVERIFIED**，阻断原因：**历史权益待核对 + 有未完成服务预约** |
| 报价不存在的权益 | `2600 权益不存在` |
| 报价同门店其他会员的权益（id=16，会员 id=1） | **`2600 只能对自己名下的权益申请退款`** ✅ 越权被拦 |
| 会员调用员工受理接口 | `403` |
| 店长调用会员自助接口 | `403` |
| 会员查看本人退款进度 | 200，0 条 |

## 需要注意：cjc2 现在两条会籍都会被业务规则拦下

这不是 bug，是 V9 退款规则本来就设计好的两条前置条件，现在界面会把原因直接显示给会员：

1. **有未完成服务预约**：cjc2 名下有 4 条未完成预约（会员端"我的预约 (4)"）。
   需要先完成或取消这些预约，才能申请退款——否则退款后这些预约会挂在已退款的卡上。
2. **历史权益待核对（UNVERIFIED）**：`年度畅练卡` 属于 V9 之前的存量权益，价值余额尚未核对，
   可退金额无法确定。需要员工端在「退款申请/审批 → 权益退款申请」里先做**价值核对**（`POST /finance/entitlements/{id}/reconcile`），
   核对通过后才会变成 VERIFIED，会员才能自助退款。

想端到端跑通一次会员退款，顺序是：**取消/完成未完成预约 → 员工核对存量权益价值 → 会员自助提交 → 管理员审核 → 员工登记退款支付**。

## 未做

- 没有为会员新增"退款专用权限常量"。会员自助入口统一走 `MEMBER_SELF_SERVICE`，与 `/member/*`、
  `/finance/me/refunds` 等既有会员接口保持一致，避免权限矩阵出现第二套会员权限。
- 会员不能指定退款金额、不能指定支付渠道——渠道与凭证由员工登记退款时填写（沿用 V9 流程）。
