# 排课与教练账号绑定验证记录

状态：**已完成并验证，尚未发布到线上。** 未改数据库结构、未改 Flyway 迁移、未触碰服务器。

## 现象

管理员在「课程与排课 → 新增排课」里新建了 9 月 24 日「动感单车」，教练栏填写"首席教练"。
管理员列表里能看到这条排课，但用教练账号登录后，教练工作台的「我的排课」里没有它。

## 根因

排课表里教练是**两个字段**：

- `coach_name`：自由文本，由新增排课表单的输入框填写；
- `coach_id`：真实教练账号 ID。

而 `CoachWorkbenchService.workbench()` 的查询条件是按**账号 ID** 过滤：

```java
Long coachId = SecurityUtil.getCurrentUserId();
Long venueId = SecurityUtil.getCurrentOrganizationId();
scheduleRepository.findByCoachIdAndVenueIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
        coachId, venueId, now.minusDays(7), now.plusDays(30));
```

本地库实测：

| 排课 | 教练名 | `coach_id` | 教练工作台可见 |
| --- | --- | --- | --- |
| 1–17（历史数据） | 首席教练 | **3**（= coach 账号） | ✅ |
| **26（本次新增，9/24 动感单车）** | 首席教练 | **NULL** | ❌ |
| 23 / 24 / 25 | coach | **NULL** | ❌ |

**新增排课表单只有"教练姓名"这一个自由文本输入框**，前端从未提交 `coachId`（`ScheduleRequest.coachId` 一直是可选且无人填），
`createSchedule` 于是写入 `coach_id = null`。名字写得完全正确也没用——教练工作台按 ID 查，查不到。

换句话说：**这不是教练工作台的过滤写错了，而是排课表单缺少"绑定真实教练账号"的能力。**

## 改动内容

### 后端

| 文件 | 改动 |
| --- | --- |
| `CourseService.java` | 新增 `listCoaches()`，复用 `SysUserRepository.searchEmployees`（roleCode=COACH、status=ACTIVE），超管看全部门店、其余角色限本门店；新增返回值 record `CoachOption(id, name, venueId)` |
| `CourseController.java` | 新增 `GET /api/v1/admin/coaches`，鉴权 `SCHEDULE_WRITE`（正好是需要建排课的店长与超管） |

### 前端

| 文件 | 改动 |
| --- | --- |
| `views/admin/CourseManagement.vue` | 新增排课弹窗的教练字段由**文本输入**改为**教练账号下拉**（可搜索、必填），提交时同时带上 `coachId` 与 `coachName`；字段下方提示"必须绑定真实教练账号，否则教练工作台看不到这条排课"；无教练账号时禁用创建按钮 |
| `api/modules/course.ts` | 新增 `CoachOption` 类型与 `listCoaches()`；`Schedule` 类型补上 `coachId`（后端实体本来就返回，前端类型漏了） |

编辑排课同样使用这个下拉，所以**历史遗留的"只填了姓名"的排课，可以在界面上补绑教练账号后立刻对教练可见**。

## 验证

- 后端 `mvn -B clean package`：**78 / 78 测试通过**，0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS（fat jar 71,305,618 字节）。日志 `logs/wb-coach-gate2.log`。
- 新增回归测试 `FitnessApplicationIntegrationTest#schedulesBoundToACoachAccountAppearInThatCoachWorkbench`：
  1. 教练列表接口对超管可用、对纯教练角色返回 403（没有 `SCHEDULE_WRITE`）；
  2. 只填 `coachName` 不填 `coachId` 建排课 → **教练工作台看不到**（复现问题）；
  3. 绑定 `coachId` 建排课 → **教练工作台能看到**；
  4. 用编辑接口给"只填姓名"的那条补上 `coachId` → **教练工作台立刻能看到**（修复路径）。
  期间一次失败为**测试自身缺陷**：测试库里存在不止一个 COACH 账号，测试取了列表第一条而不是当前登录的那位教练；
  已改为用登录返回的 `userInfo.id` 精确定位。
- 前端 `npm run build`（含 `vue-tsc`）退出 0，Node 测试 8/8。日志 `logs/wb-fe-build7.log`、`logs/wb-fe-tests8.log`。
- **线上实测**（本地正式库，日志 `logs/wb-coach-online-check.txt`）：

| 步骤 | 结果 |
| --- | --- |
| `GET /api/v1/admin/coaches`（超管） | 返回 1 位教练：首席教练（id=3, venue=1） |
| `GET /api/v1/admin/coaches`（教练角色） | `403` 权限不足 |
| 修复前教练工作台可见排课 | `[12, 13, 14, 15, 16, 17]` —— **不含 26** |
| 给排课 26 绑定 `coachId=3`（PUT） | `200 / code 0`，返回 coachId=3 |
| 修复后教练工作台可见排课 | `[12, 13, 14, 15, 16, 17, **26**]` —— 动感单车 9/24 00:00 ~ 9/25 00:45，0/20 |

**用户反馈的那条排课（26）已顺手补绑到教练账号，现在教练登录即可在「我的排课」看到它。**

## 遗留

- 线上正式库中 **排课 23、24、25 的 `coach_id` 仍为空**（23/24 在滨江店，25 是总部 9/29）。
  在界面上用「编辑」选择一次教练账号即可修复，不需要重新建排课。
  （排课 26 已在本轮修复。）
- 若确实需要"只写名字、不绑定账号"的临时教练，目前不支持——教练工作台看不到这类排课。
  如将来有需求，应给教练工作台补一条"按姓名兜底"的查询，而不是把 `coach_id` 继续留空。
