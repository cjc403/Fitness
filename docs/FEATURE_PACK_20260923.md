# 四个新功能开发记录（2026-09-23）

状态：**已完成并验证，本地运行，尚未上线。** 迁移 V11 已在本地正式库执行（纯附加）。

对应建议书：`docs/FEATURE_PROPOSAL_20260923.md`（用户选定：冲突检测 + 批量排课 + 优惠券 + 课程评价）。

---

## 0. 必修：教练排课冲突检测

**问题**：`CourseService.createSchedule` 只校验了"结束>开始 / 场馆存在 / 场馆与课程一致"，
**从不校验该教练在同一时段是否已有别的课** → 同一教练能被排到重叠时段，会员两边都能约上，到店只有一个教练。

**实现**：
- `CourseScheduleRepository.findCoachOverlaps(coachId, start, end, excludeId)`：
  重叠判定 `已有.start < 新.end 且 已有.end > 新.start`，排除自身、排除已取消。
- `CourseService.coachConflictReason / assertCoachAvailable`：建课与改课都会校验，
  冲突时**报出具体冲突的那节课**（时间 + 课程名）。首尾相接（10:00-11:00 与 11:00-12:00）不算重叠。

## 1. 周期性批量排课

- 新接口 `POST /api/v1/admin/schedules/batch`（`SCHEDULE_WRITE`）。
- 入参：课程 + 教练 + 周几（可多选 1–7）+ 时段 + 日期区间 + 容量 + `dryRun`。
- **逐日展开**：命中周几的日期生成一节；已过去的时段、与教练其它排课冲突的时段**跳过并说明原因，不阻断整批**。
- `dryRun=true` 只预检不落库（同批次内先保存的课也会被后续冲突检测看到，因此不会自己和自己撞）。
- 单次跨度上限 180 天。
- 前端：「课程与排课」新增**批量排课**按钮与对话框，先「预检」看到"将生成 N 节 / 跳过 M 节 + 每节原因"，再「确认生成」。

## 2. 优惠券与折扣

- 新增两张表：`coupon_template`（券模板）、`member_coupon`（会员券）；
  `sales_order` 增加 `member_coupon_id` 与 `discount_cents`。
- 支持**满减**（满 X 减 Y）与**折扣**（如 0.9 = 九折），金额一律用**分**存整数。
- 管理端：建券 / 改券 / 停用 / 发放（多选会员 + 每人张数，校验剩余额度）；开卡弹窗可按「会员+商品」**试算可用券**。
- 会员端：「我的优惠券」页签；自助购买时若有可用券会先弹出选券（含"不使用优惠券"）。
- **资金纪律（本项的关键工程点）**：
  - 用券时**实收金额一律由服务端按分计算**，忽略前端传的 `paidAmount`；
  - 订单 `original_amount` 记标价、`paid_amount` 记折后实收、`discount_cents` 记抵扣；
  - 权益价值账按 `paid_amount` 入账 → **退款自然按实付退，不会按标价多退**；
  - 券在下单事务内加行锁核销（`findByIdForUpdate`），一张券只能用一次；
  - 销售幂等键的回放路径在扣券之前返回，**重试不会重复扣券**。
- 新权限：`COUPON_READ` / `COUPON_WRITE`（超管、店长、前台、会籍顾问）。

## 3. 课程评价与教练评分

- 新增 `course_review` 表，`booking_id` **唯一约束**（一条预约只能评一次，数据库兜底）。
- 资格只认预约状态 **`ATTENDED`**（真实到课签到），服务层 + 数据库双重保证。
- 接口：会员提交/查看（`MEMBER_SELF_SERVICE`）、教练看本人汇总（`COACH_WORKBENCH`）、
  店长看本店 / 总部看全部（新权限 `COURSE_REVIEW_READ`）；汇总含平均分、五星分布、各教练平均分、低分记录（≤3 星）。
- 前端：会员端「课程评价」页签（待评价列表 + 星级 + 文字 + 历史评价）。

## 权限说明（顺带确认的一件事）

权限矩阵来自代码里的 `RolePermissionCatalog`（`DatabaseUserDetailsService` 直接读它），
**数据库的 `permission` / `role_permission` 表不参与鉴权** → 新增权限**不需要迁移**，只改 catalog 即可。

---

## 门槛与验证

| 项 | 结果 |
| --- | --- |
| 后端 `mvn -B clean package` | **89 / 89 测试通过**（新增 4 个），BUILD SUCCESS，jar 71,407,839 字节 |
| 前端 `npm run build`（含 vue-tsc） | 退出 0 |
| 前端 Node 测试 | 8 / 8 |
| **迁移 V11 落库** | `flyway_schema_history` V11 success=t；`coupon_template`、`member_coupon`、`course_review` 三表已建；`sales_order` 两个新列已加 |
| 在线冒烟 | 批量排课预检正常（生成 3 节并列出周几）；优惠券/评价接口 200；**越权拦截生效**（会员建券 403、会员看教练评分 403） |

新增回归测试（`FitnessApplicationIntegrationTest` 19–22）：
1. `coachCannotBeDoubleBookedInOverlappingSlots` —— 重叠必须被拒并指出冲突课程；首尾相接允许。
2. `batchSchedulePreviewsConflictsThenCreatesTheRest` —— 预检不落库（连跑两次结果一致）、正式生成、再预检全被判冲突。
3. `couponDiscountsPaidAmountAndCannotBeReused` —— 试算抵扣 2000 分、**实收 180.00（忽略前端传的 200.00）**、券转 USED、同一张券再用被拒。
4. `memberCanReviewAnAttendedClassOnlyOnce` —— 未签到不能评、签到后可评、重复评价返回 2601、教练端与管理端汇总可见。

## 附带发现（非本次改动，需你决定）

`coach` 账号用文档里记录的口令登录**失败**（1102 用户名或密码错误），而 admin / manager / reception / member / cjc2 都正常。
这是既有的账号数据漂移。教练端界面（工作台、评分汇总、提交授课）目前都无法登录验证。
**建议**：确认一下 coach 账号的当前口令，需要的话我可以在「员工账号」里给你重置一个。

## 交付物

- 迁移：`fitness-backend/src/main/resources/db/migration/V11__coupons_and_course_reviews.sql`
- 新模块：`module/coupon/`（实体/仓储/DTO/服务/控制器）、`module/review/`（同）
- 改动：`CourseService`、`CourseController`、`CommercialService`、`SalesOrderEntity`、`CommercialDtos`、
  `MemberPortalService/Controller`、`MemberPurchaseRequest`、`RolePermissionCatalog`
- 前端：`api/modules/{coupon,review,course,memberPortal}.ts`、
  `views/admin/{CourseManagement,CommercialCenter}.vue`、`views/member/MemberHome.vue`
