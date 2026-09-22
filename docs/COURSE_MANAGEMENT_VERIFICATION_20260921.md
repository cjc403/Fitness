# 课程管理三项增强验证记录

状态：**已完成并验证，尚未发布到线上。** 未改数据库结构、未改 Flyway 迁移、未触碰服务器。
本地 PostgreSQL 正式库中**未写入或删除任何业务数据**。

## 需求与根因

### 1. 新增课程没有「所属门店」选择

`CourseService.applyCourse` 其实**早已支持**超级管理员按 `CourseRequest.venueId` 指定门店
（非超管一律锁定为本人门店），但前端「新增课程」弹窗**从未渲染过这个字段**，
于是超管建课只能落在登录账号所属门店，跨门店维护课程无法进行。

### 2. 编辑课程没有删除功能

后端**没有** `DELETE /api/v1/admin/courses/{id}`，前端也没有入口。

需要注意的数据约束（已实测确认）：

- `course_schedule.course_id → course.id` 是 **NO ACTION** 外键，直接删除有排课的课程会被数据库拒绝；
- `booking.schedule_id` 与 `private_lesson.schedule_id` 又都引用排课，因此**不能做级联删除**——
  那会连带破坏真实会员的预约与私教历史。

### 3. 时长/容量/价格看不到数字，且偶尔报「参数校验失败」

两个独立原因叠加：

**(a) 布局缺陷（看不到数字）**：三个 `el-input-number` 挤在 `el-row` 的三个 `el-col :span="8"` 里，
而外层表单 `label-width="90px"` 会从每个列里再吃掉 90px。实测可用宽度不足以容纳"减号 + 输入框 + 加号"，
Element Plus 遂把内部 input 压成接近 0 宽——**值确实在，只是显示不出来**，所以"盲打有效但看不见"。

**(b) 越界即报错（参数校验失败）**：服务端约束是时长 15–300、容量 1–500、价格 ≥ 0。
实测（用不存在的课程 id 探测，全程不落库）：

| 提交值 | 结果 |
| --- | --- |
| 时长=10 / 400 | 400 + 1001，「最小不能小于15」/「最大不能超过300」 |
| 容量=0 / 600 | 400 + 1001，「最小不能小于1」/「最大不能超过500」 |
| 价格=-1 | 400 + 1001，「必须大于或等于0」 |
| 时长=null | 通过（服务端落库时兜底为 60，不会丢数据） |

输入框看不见当前值，用户只能盲打或连点加减，很容易落到范围外 → 就会弹「参数校验失败」。
另外后端返回的字段级原因一直存在于 `data` 里，**前端从未展示**，所以提示毫无指向性。

## 改动内容

### 后端

| 文件 | 改动 |
| --- | --- |
| `CourseController.java` | 新增 `DELETE /api/v1/admin/courses/{id}`，`@PreAuthorize("hasAuthority('COURSE_WRITE')")` |
| `CourseService.java` | 新增 `deleteCourse`：有排课时拒绝并说明数量；新增 `resolveVenueId`（只计算不赋值）；`updateCourse` 在写入前校验"有排课的课程不得变更场馆" |
| `CourseScheduleRepository.java` | 新增 `countByCourseId` |

拒绝逻辑放在业务层而非依赖数据库：这样拿到的是可读中文原因，而不是 500 或外键约束报错。

### 前端

| 文件 | 改动 |
| --- | --- |
| `views/admin/CourseManagement.vue` | 新增/编辑课程弹窗增加「所属场馆」（超管可选，必填）；数字字段改为 `controls-position="right"` + `width:100%` + 独立 `label-width="52px"`，弹窗加宽到 620px；三个数字字段补必填与范围校验、提交前夹紧到合法区间；编辑态底部新增「删除课程」（二次确认） |
| `api/modules/course.ts` | 新增 `deleteCourse` |
| `utils/request.ts` | 400 响应若带字段级明细（`data: {字段: 原因}`），拼接进提示，避免只显示"参数校验失败" |

行为约定（刻意为之）：

- **有排课的课程不能删除**，提示"该课程还有 N 条排课，不能删除；请先处理相关排课"；
- **有排课的课程不能变更所属场馆**，提示"该课程已有排课，不能变更所属场馆"——
  改门店不会带着已有排课一起搬，会造成"课程在这家店、排课在另一家店"的错位（正是本日前一轮排查过的问题）；
- 无排课的课程可自由删除与改门店。

## 验证

- 后端 `mvn -B clean package`：**76 / 76 测试通过**，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS，
  `repackage` 正常产出 fat jar（71,300,063 字节）。日志 `logs/wb-course-gate3.log`。
- 新增回归测试 `FitnessApplicationIntegrationTest#courseDeletionAndVenueChangeAreGuardedByExistingSchedules`：
  覆盖"超管按门店建课 → 无排课课程删除成功 → 有排课课程删除被拒 → 有排课课程改门店被拒 → 被拒操作未落库"。
- 前端 `npm run build`（含 `vue-tsc` 类型检查）退出 0；Node 测试 8/8。日志 `logs/wb-fe-build4.log`、`logs/wb-fe-tests5.log`。
- 源码备份：`backups/source-before-course-crud-20260921/`。

期间一次构建失败为**测试自身缺陷**：JSONPath 过滤表达式返回 `Integer` 而断言传入 `long`，报
`expected:<1> but was:<1>`。已改为不依赖数值类型比较的断言方式（改为验证"被拒操作未落库：课程仍在原场馆，
故再次改门店依旧被拒"），随后全绿。

## 未做与后续建议

- **排课仍没有编辑/删除接口**，选错门店或时间的排课只能在界面上放着走 SQL；建议后续补齐。
- 新建排课的开始时间默认仍是 `00:00`（日期选择器只选到日），产出的排课像"零点上课"；
  建议把默认值改为次日 10:00 或"当前时间 +1 小时取整"。
- 本地 PostgreSQL 中两条建在「FitChain 滨江店」的排课（id 23/24）**保持原样未动**，等待用户决定是否迁移。
