# 第4项：教练训练计划与执行记录设计

日期：2026-10-11。用户已批准五项业务功能，本次“开始下一项”执行第4项，第5项器械维护留待下一阶段。用户2026-10-10要求直接采用推荐方案，不再询问常规选择或设计批准。

## 目的与范围

围绕真实门店服务完成“制定计划 → 发布 → 教练登记训练 → 会员查看并反馈 → 查看历史”的闭环。既有私教预约、签到、签课、扣次、提成和体测不变。训练登记不等于出勤或消课，不写资金、权益、预约、体测或会员训练目标。计划目标取独立快照，体测按钮复用原组件；不提供医疗建议或自动生成处方。

这是新增前后端与数据模块的架构任务。沿用Java17/Spring Boot3.5、Vue3/TypeScript/Element Plus、PG18，零新增依赖。新增V22，V1–V21字节不变；本地5433最终备份迁移，隔离库测试全部业务写；只验桌面、不访问或更新服务器、不提交或推送。

## 业务与权限

- 新权限TRAINING_PLAN_READ给教练/店长/总部，TRAINING_PLAN_WRITE只给教练，TRAINING_PLAN_ARCHIVE给教练/店长/总部；会员通过MEMBER_SELF_SERVICE专用接口。前台、会籍顾问无训练计划权限。唯一权限来源RolePermissionCatalog，菜单/路由共用accessControl。
- 教练只在账号所属服务门店操作，候选学员为本店专属学员，或在本店由本人授课的BOOKED/ATTENDED预约学员（排课开始日期在今天前90天及以后）；取消、请假、爽约、候补预约不授予关系。跨店会员有符合条件的本店预约时允许本店教练提供训练服务。
- 创建时固定coachId=当前账号、venueId=当前账号门店，会员须正常、未黑名单；操作既有计划须仍有关系，且计划教练/服务门店与本人一致。另一个教练不能修改或记录该计划。
- 店长查看服务门店的计划，总部可跨店查看；二者可带原因归档计划以处理教练调岗/学员归属变化，不能编辑内容或登记/作废训练；会员只读本人已发布/归档计划（草稿列表不出现，按ID也拒绝），可反馈本人有效训练记录。无关人员不得通过ID、分页、搜索、统计或审计获取数据。会员返回对象不含员工审计或他人个人信息。
- 草稿DRAFT可修改全部计划内容；发布ACTIVE后内容固定。允许归档DRAFT/ACTIVE为ARCHIVED，无硬删或重新激活。一个会员在同一服务门店同时最多一个ACTIVE计划（数据库可空active_member_key唯一键，值为venueId/memberId）；发布新计划前需显式归档旧计划，拒绝偷偷替换。一教练的操作不归档另一教练计划；店长/总部可带原因归档可访问的DRAFT/ACTIVE，写独立审计，此监督归档不要求仍存在教练关系，且允许处理停用或黑名单会员的旧计划，以释放有效计划占用；不授予训练数据修改权。会员反馈须在锁后确认会员仍ACTIVE且非黑名单；创建重试须重新校验原计划的当前教练/服务门店权限后才能返回详情。
- 标题1–100字，目标FAT_LOSS/MUSCLE_GAIN/POSTURE/ENDURANCE/GENERAL；周期开始≤结束，最多366个日历日（差≤365）；起始不早于今天前365天、结束不晚于今天后365天。备注≤1000字，动作1–20项，名称1–100字、动作说明≤300字。
- 动作模式STRENGTH：组数1–20、次数1–200，重量可空或0–500kg（最多2位小数）；DURATION：分钟1–240。模式不适用的字段必须空，不隐式接受脏数值。每条动作固定序号，草稿重写动作无训练记录依赖。
- ACTIVE计划才能登记训练，日期须在计划周期内、≤今天、≥今天前365天；时长1–240分钟，主观强度1–10可空，教练公开备注≤1000字。所有计划动作须出现且每个恰好一次（外部/重复/缺失ID拒绝），完成DONE按模式填写实际值，跳过SKIPPED填原因1–300字且不含实际数值；至少完成一个动作。
- 可选择关联既有预约bookingId：须该会员、该教练、该服务门店，状态ATTENDED且排课日期等于训练日期；同一预约只能有一条有效训练记录。不修改预约、私教签课或扣次。无关联预约时明确标注“独立训练记录”。
- 训练记录不可覆盖；教练可作废本计划的有效记录（包括已归档计划），原因1–300字，保留内容与反馈、释放预约唯一占用，随后重新登记须计划ACTIVE。作废记录不计完成次数、最近训练和统计，会员可看其作废状态但不可反馈。
- 会员反馈1–5分、内容≤500字（内容可空），可修正本人的反馈，使用训练记录版本并留审计；不可反馈作废记录、不可指定会员ID冒充他人。

## 数据与并发

五张表：training_plan、training_plan_exercise、training_session、training_session_exercise、training_plan_event。FK/范围/状态/唯一约束与查询索引兼容PG/H2；V22全新，不回填历史业务数据。

每次写入含idempotencyKey(8–80字符，字母数字/下划线/短横线)；同一memberId+actorId+key唯一事件，记录SHA256规范请求摘要、操作种类和对象。先当前权限/关系检查，再查重；相同请求重试返回原操作对象的当前详情，不重复写、即使版本/状态已推进也成功；相同键不同请求2600。请求失败事务回滚，不吞唯一约束冲突。按会员行锁串行所有本模块写入（create/update/publish/archive/record/void/feedback），再校验expectedVersion，计划/记录版本严格递增；新建不要求版本。发布和记录唯一键提供数据库第二道保护。成员/预约权限在锁内再次核验。

审计事件保留操作、作者姓名、时间、原对象ID、关键变更/原因及请求摘要；不要存令牌或口令，会员无事件数据。记录原计划动作快照，草稿后续变动或他人资料修改不改变既有训练记录。计划详情分页训练历史，每页20默认最大100；审计最近100条并给eventTotal；页面提示历史数量，不误导为全量。

## API契约

根路径 `/api/v1/training-plans`，前端请求用`/v1/training-plans`。

- GET `/students?keyword&page=1&size=20`（教练）：分页候选 `{records:[{id,realName,memberNo,venueId,trainingGoal,trainingGoalNote}],total,page,size}`，不可返回手机号/证件。按会员ID去重，最多100。
- GET `/workbench?venueId&status=ALL&keyword&page=1&size=20`（员工读）：`{summary:{draftPlans,activePlans,archivedPlans,sessionsThisMonth},plans:{records:PlanSummary[],total,page,size},venues:[{id,name}]}`。只给HQ可选门店；店长/教练传别店拒绝。统计不受status/keyword/page影响但受授权与选店范围影响。
- GET `/{id}?sessionPage=1&sessionSize=20`：`Detail={plan:PlanSummary,exercises:ExerciseView[],sessions:{records:SessionView[],total,page,size},events:EventView[],eventTotal}`。
- POST 根路径：`Create={memberId,title,goal,startDate,endDate,note?,exercises:ExerciseInput[],idempotencyKey}`→Detail。ExerciseInput={name,mode,sets?,reps?,weightKg?,minutes?,note?}。
- POST `/{id}/draft`：与Create相同内容但不含memberId，含expectedVersion；不可换会员/教练/门店→Detail。
- POST `/{id}/publish`、`/{id}/archive`：`{expectedVersion,idempotencyKey,reason?}`→Detail，archive原因必填。
- POST `/{id}/sessions`：`{expectedVersion,idempotencyKey,performedOn,durationMinutes,effort?,note?,bookingId?,exercises:[{exerciseId,status,sets?,reps?,weightKg?,minutes?,note?}]}`→Detail。
- GET `/{id}/booking-options?performedOn=yyyy-MM-dd`：仅可操作教练，有效ATTENDED且当天的本人该会员预约，返回`[{id,courseName,startTime}]`；登记接口重新验证。
- POST `/{id}/sessions/{sessionId}/void`：`{expectedVersion(记录版本),idempotencyKey,reason}`→Detail。
- 会员GET `/api/v1/member/training-plans?status=ALL&page=1&size=20`→同workbench（不含员工草稿、门店选项为空）；GET `/api/v1/member/training-plans/{id}?sessionPage&sessionSize`→Detail(events=[],eventTotal=0)；POST `.../{id}/sessions/{sessionId}/feedback`：`{expectedVersion(记录版本),idempotencyKey,rating,content?}`→Detail。
- PlanSummary至少含id/memberId/memberName/memberNo/coachId/coachName/venueId/venueName/title/goal/startDate/endDate/note/status/version/exerciseCount/sessionCount/lastSessionOn/canEdit/canPublish/canArchive/canRecord/canViewBody；canViewBody仅界面提示，不扩大既有体测接口权限：HQ/可访问教练/会员本人true，店长仅会员归属店与本人门店相同true；can*由服务器授权及状态计算，会员全部false。
- ExerciseView含id/position及输入字段。SessionView含id/planId/version/performedOn/durationMinutes/effort/note/bookingId/courseName/status(COMPLETED/VOIDED)/voidReason/coachName/createdAt/feedbackRating/feedbackContent/feedbackAt/canVoid/canFeedback，以及exercises动作快照含exerciseId/name/mode/position/实际字段/status/note与目标字段targetSets/targetReps/targetWeightKg/targetMinutes。
- EventView={id,type,actorName,createdAt,reason,sessionId?}。API只用DTO，无实体直接序列化。组数/次数/分钟/强度/评分/ID/版本等整数字段拒绝小数截断，仅本模块严格解析、不改变旧模块。计划允许同名不同目标动作，执行记录重复ID拒绝。合法参数显式校验，业务冲突2600/无权1003/参数1001，沿用现有Result及全局错误模式。

## 桌面界面

教练/店长/总部左侧“训练计划”，路由`/training-plans`，统一WorkspaceHero冰蓝主题、真实统计、计划表、搜索/状态/总部门店筛选。教练创建含候选学员检索及分页、动态动作行；草稿保存、发布、归档；详情抽屉显示动作目标、训练历史和员工审计，教练登记实际训练/作废，清晰显示“仅训练记录，不会签到或扣课”。员工体测入口复用BodyMeasurePanel。教练工作台学员行加“训练计划”跳转，限定可操作关系仍由接口判断。

会员主页新增“我的训练”页签，复用TrainingPlanPanel只读模式，能看自己的已发布/归档计划、训练历史、目标与实际、作废状态并提交/修改反馈；不改其它页签。表格名称、按钮、空态、错误、加载、重试均中文；查找失败显示失败，不能当空白。后端返回数字保持数值，不把缺失值当0。

异步安全：共享request已有isRequestCurrent门控，新增API全量透传。组件捕获sessionGeneration+账号/角色/门店+筛选/目标/动作generation，过期读写不发、不重放、不toast、不清当前表单；切店/换账号/关闭/卸载即时清私密数据。single-flight冻结payload+目标+版本，相同失败请求保留key（即便时间流逝），编辑/重开换key；2600显式重新加载后新key，不自动覆盖。禁用切目标时的未完成保存，迟到响应不打开新抽屉或跨会员污染。

## 验收

后端新模块H2完整闭环、草稿隐藏、教练关系/取消/跨店/状态、经理仅监督归档、会员反馈及越权、冻结快照、校验、重试/异键冲突、并发发布/记录/同预约/版本、作废与反馈审计、页界和统计。用隔离PG迁移验证V21→22历史65表摘要不变/新表空，并跑新专项（含真实并发，不能将H2全套称为PG）。前端真实编译SFC/API运行测试覆盖上述异步与重试、筛选、表单、会员操作权限，完整node tests+vue-tsc/build。隔离HTTP及桌面教练创建→发布→登记→会员反馈→教练查看→作废，店长/HQ查看与受控归档/切店；控制台无错误。不检查手机。

全部独立审查、最终制品校验后备份真实本地库和旧JAR；验证PID/路径再停后端，安装测试JAR并只读健康/API/正常登录登出验收。真实新表空，历史业务表摘要保留（正常登录/登出元数据单独报告）。保持本地服务可用，清理本次隔离服务和浏览器标签；更新AGENTS/README/当前功能与待办/交接§0与§10，§3凭据字节摘要不变。不宣称未执行的检查通过。
