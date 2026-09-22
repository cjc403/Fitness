# 排课编辑与删除验证记录

状态：**已完成并验证，尚未发布到线上。** 未改数据库结构、未改 Flyway 迁移、未触碰服务器。
本地 PostgreSQL 正式库中**没有新增、修改或删除任何数据**。

## 需求

排课原先只有 `GET` 与 `POST`，选错门店或时间的排课在界面上改不了也删不掉，只能走 SQL。

## 数据约束（先查清楚再动手）

| 关系 | 说明 |
| --- | --- |
| `course_schedule.course_id → course` | NO ACTION |
| `booking.schedule_id → course_schedule` | NO ACTION，且 `schedule_id` 非空 |
| `private_lesson.schedule_id → course_schedule` | NO ACTION |

也就是说：**有预约或私教授课记录的排课不能用物理删除**——硬删会破坏会员预约与教练授课历史，而且数据库本身也会拒绝。
另外排课门店必须始终等于课程门店（`createSchedule` 的既有约束），所以"改门店"的正确做法是**更换课程**，门店随之跟随。

本地库现状（25 条排课）：1–22 各有 3–7 条预约；**只有 23、24（滨江店）和 25（总部）三条没有预约**，
正好是本次需要修正的那几条。

## 改动内容

### 后端

| 文件 | 改动 |
| --- | --- |
| `course/dto/ScheduleUpdateRequest.java`（新增） | 编辑请求。**刻意不加 `@FutureOrPresent`**：编辑的用途正是纠正已建错的排课（含已过期的），沿用"必须晚于当前时间"会导致历史排课永远改不动；先后关系改由服务层校验 |
| `CourseController.java` | 新增 `PUT /api/v1/admin/schedules/{id}` 与 `DELETE /api/v1/admin/schedules/{id}`，均要求 `SCHEDULE_WRITE` |
| `CourseService.java` | 新增 `updateSchedule` / `deleteSchedule` / `findAccessibleSchedule` |
| `booking/repository/BookingRepository.java` | 新增 `countByScheduleId` |
| `training/repository/PrivateLessonRepository.java` | 新增 `countByScheduleId` |

**编辑规则**

- 排课门店 = 新课程门店（始终跟随课程，避免"课程在这家店、排课在另一家店"的错位）；
- 已有预约记录时**不允许更换课程**；
- 已有**有效**预约（未取消）时**不允许调整时间或容量**——否则会员按原时间到店却发现课被挪走，而本系统目前没有通知渠道；
- 容量不得小于已报名人数；结束时间必须晚于开始时间；
- 教练姓名/教练 ID 可以随时改（换教练不影响会员的到课时点）。

**删除规则**

- 有预约记录（含已取消）→ 拒绝并给出条数，提示"可改为调整时间或教练"；
- 有关联私教授课记录 → 拒绝并给出条数。

### 前端（`views/admin/CourseManagement.vue`）

- 排课表格新增「操作」列：**编辑 / 删除**（仅对拥有 `SCHEDULE_WRITE` 的角色显示）；
- 「编辑」复用原本的新增排课弹窗，标题切换为"编辑排课"、按钮改为"保存修改"，并预填该排课的门店、课程、教练、时间与容量；
- 编辑态下课程下拉旁提示"更换课程时，该排课会跟随新课程所属门店"；
- 「删除」带二次确认，确认框里显示课程名与日期。
- `api/modules/course.ts` 新增 `updateSchedule` / `deleteSchedule`。

## 验证

- 后端 `mvn -B clean package`：**77 / 77 测试通过**，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS（fat jar 71,303,721 字节）。日志 `logs/wb-schedule-gate2.log`。
- 新增回归测试 `FitnessApplicationIntegrationTest#scheduleEditAndDeleteRespectExistingReservations`：
  覆盖"改教练成功 → 结束早于开始被拒(1001) → 排课不存在(2201) → 无预约可删除 → 会员预约后删除被拒(2600) → 该排课换课被拒(2600) → 教练改/删被拒(403)"。
- 前端 `npm run build`（含 `vue-tsc`）退出 0；Node 测试 8/8。日志 `logs/wb-fe-build5.log`、`logs/wb-fe-tests6.log`。
- **线上拒绝路径实测**（本地正式库，全程未改动数据）：日志 `logs/wb-schedule-online-check.txt`

| 场景 | 结果 |
| --- | --- |
| 删除有 6 条预约的排课 | `2600`「该排课已有 6 条预约记录（含已取消），不能删除；可改为调整时间或教练」 |
| 调整有预约排课的时间 | `2600`「该排课还有 6 条有效预约，不能调整时间或容量」 |
| 有预约排课更换课程 | `2600`「该排课已有 6 条预约记录，不能更换课程」 |
| PUT/DELETE 不存在的排课 | `2201`「排课不存在」 |
| 教练删除排课 | `403`「权限不足」 |
| 排课总数 | 验证前后一致，**0 条内容被改动** |

- 重启后日志无 ERROR；`9091/actuator/health` 为 UP。
- 源码备份：`backups/source-before-schedule-crud-20260921/`。

期间一次构建失败为**我写测试时的笔误**：多行字符串拼接漏了一个 `+`，导致 `需要')'` 编译错误；补上后全绿。

## 当前可编辑、可删除的排课

本地正式库中只有下列三条没有任何预约，因此现在可以在界面上直接编辑或删除：

| id | 门店 | 课程 | 开始时间 |
| --- | --- | --- | --- |
| 23 | FitChain 滨江店 | 私教体验课 | 2026-09-26 00:00 |
| 24 | FitChain 滨江店 | 核心训练营 | 2026-09-28 00:00 |
| 25 | 总部 | 活力燃脂操 | 2026-09-29 00:00 |

## 未做与建议

- 仍未给排课提供"取消（软删除）"状态：有预约的排课既不能删也不能改时间，只能改教练。
  若确实需要通知已预约会员改期，应先补**通知中心**（路线图第 18 节已列）。
- 新建排课的开始时间默认仍是 `00:00`（日期选择器只选到日），建议改为次日 10:00 或"当前时间 +1 小时取整"。
