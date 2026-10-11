# 教练训练计划与执行记录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 本地交付教练制订计划、登记训练、会员查阅反馈及门店监督的完整闭环。

**Architecture:** 新建workout模块独立存储计划、动作、执行快照与审计，通过会员行锁、请求摘要和版本管理并发。Vue复用TrainingPlanPanel供员工和会员，新增菜单及会员页签；私教资金流程和体测保持原有行为。

**Tech Stack:** Java17/Spring Boot3.5/JPA/Flyway/PostgreSQL18/H2；Vue3/TypeScript/Element Plus/node:test，无新增依赖。

**Spec:** docs/superpowers/specs/2026-10-11-training-plans-design.md

## Global Constraints

- 用户授权直接推荐方案执行，不再设计/执行方法提问；本次仅第4项，第5项待续。
- 本地5433最终备份迁移，隔离库测试全部业务写；只验桌面，不访问或更新服务器，不提交或推送。
- 共享main工作区已有119项未提交状态；原有改动保留，用户明确要求在该共享本地项目接续，原位编辑，新鲜TEMP复制完整脏源码构建；不创建遗漏已授权改动的干净worktree。
- V1–V21字节不变，新增V22，零新增依赖；资金/预约/体测历史数据不改。
- Spec是权限、字段、状态、范围、API的唯一要求来源，任何冲突先按Spec裁定，写本计划ledger。

## Review Focus

1. 历史取消/请假预约或另一门店预约不能赋予教练权限；Task1分别用真实JWT+数据库数据验证。
2. 同一会员/门店并发发布、同预约并发登记及反馈/作废竞争不得重复有效记录；Task1用隔离PG的独立线程事务与同步闩验证。
3. 重试须先鉴权再判重复，但在合法范围内同intent跨版本/时间可重试；Task1改变状态/时间/内容与归属后各测一例，Task2保持冻结请求key。
4. 会员猜草稿ID、分页统计或操作他人记录应拒绝且不泄露审计；Task1专用读写DTO断言，Task2会员模式检查。
5. 切店/账号/抽屉关闭/卸载后的迟到响应或401重放不得跨学员写或污染界面；Task2测试实际API配置与编译SFC异步轨迹。

## Task 1: 后端计划、执行与反馈模块

**Files:** Create fitness-backend/src/main/java/com/gym/fitness/module/workout/{controller/TrainingPlanController.java,dto/TrainingPlanDtos.java,entity/*,repository/*,service/TrainingPlanService.java,service/TrainingPlanAccess.java}; Create fitness-backend/src/main/resources/db/migration/V22__training_plans_and_sessions.sql; Modify fitness-backend/src/main/java/com/gym/fitness/security/RolePermissionCatalog.java（仅新增权限）；Create fitness-backend/src/test/java/com/gym/fitness/TrainingPlanIntegrationTest.java 与 TrainingPlanPostgresConcurrencyTest.java（如需要拆真实并发）。

**Interfaces:** Consumes current SecurityUtil, MemberRepository.findByIdForUpdate, BookingRepository, SysUser/venue、现有Result/BusinessException；Produces Spec API及所有字段，创建最终docs/qa/training-plans-20261011/api-contract.json（接口清单和DTO实际字段，不含测试个人数据/令牌）。只写新模块，必要已有仓库query加方法不得改原查询语义。

- [x] Step1 写最小RED HTTP测试：教练POST计划返回草稿，再GET含动作；运行临时src副本mvn -B -Dtest=TrainingPlanIntegrationTest test，确认当前缺路由失败；把命令/实际输出写task1报告。逐步补测试再实现。
- [x] Step2 实现权限/候选学员、V22与实体约束、分页只读DTO，测试不含隐私字段、会员草稿拒绝、本店教练/取消/90天/跨店关系和manager/HQ仅可监督归档的权限边界。
- [x] Step3 实现草稿create/edit/publish/archive及行锁/版本/规范幂等审计，测试一个有效计划、已发布冻结、同key重复、不同payload冲突、非法/重复动作及日期边界。
- [x] Step4 实现session快照、预约验证与唯一有效记录、void和member feedback，测试实际字段/至少一个完成、部分跳过、越权、历史只读、反馈修正、作废统计排除、不写既有资金/预约/体测。
- [x] Step5 增加PG专项独立线程事务竞争测试；属性只接受精确 jdbc:postgresql://127.0.0.1:55450/training_junit，PG测试口令isolated-test，不准连接5433。用物理测试DB及Spring Flyway validate，不ddl-auto=create-drop真实库；等待根控制器启动隔离PG后执行。默认PG专用测试跳过必须报告。
- [x] Step6 focused green一次、最终backend全套由控制器独立TEMP clean package。报告TDD、改动、实际契约、测试与风险；无提交。独立审查通过才Task2实现。

## Task 2: 员工训练中心与会员我的训练

**Files:** Create fitness-frontend/src/api/modules/trainingPlans.ts、fitness-frontend/src/components/TrainingPlanPanel.vue、fitness-frontend/src/views/admin/TrainingPlanCenter.vue；Modify router/index.ts, layouts/AdminLayout.vue, views/coach/CoachWorkbench.vue, views/member/MemberHome.vue；Create tests/training-plans-runtime.test.mjs（必要page/component helpers集中新模块，不改公共request语义）。

**Interfaces:** Consumes Task1 api-contract.json和Spec DTO，已有request isRequestCurrent、auth.sessionGeneration、WorkspaceHero、BodyMeasurePanel、accessControl；Produces /training-plans教练/店长/HQ入口及会员home我的训练页签；单个新面板接memberMode:boolean可只读复用。教练工作台传memberId查询参数作为打开计划的提示，服务端始终决定可见范围；不要把路由参数当授权。

- [x] Step1 编写编译真实SFC/API的RED测试（当前组件不存在/操作未实现），指定actual界面方法和动作；node --test tests/training-plans-runtime.test.mjs，记录RED命令输出。
- [x] Step2 实现计划列表筛选/分页/总部选店、真实统计、创建学员搜索分页、动态模式动作行与草稿修改，错误不当空数据。角色入口由后端权限、共用accessControl。
- [x] Step3 实现详情/目标对实际/分页训练/审计/复用体测、发布归档、登记/跳过/可选当天预约、作废；会员我的训练与反馈。入口显式说明训练登记不签到扣次；字段范围与Spec一致。
- [x] Step4 对每个API透传isRequestCurrent；冻结请求/幂等、版本冲突显式reload，组装完整精确字段。测试singleflight、相同失败retrykey、修改key变化、过期响应/失败/refresh重发/换账号/切店/换对象/关闭/卸载、即时清私密数据、历史分页、会员草稿不显示/不可教练登记。
- [x] Step5 node全套与npm run build一次通过后报告。控制器独立最终复验和浏览器桌面验收；不测手机、不提交。

## Task 3: 控制器完成迁移、运行验收和交接

**Files:** docs/教练训练计划与执行记录_20261011.md、docs/qa/training-plans-20261011/*、AGENTS.md、README.md、docs/当前功能与待办.md、仓库外交接§0/§10；本计划私有ignored ledger及TEMP工具。

**Interfaces:** Consumes Task1/2完成+独立审查、API契约、最终源码快照和制品，Produces完成证据verification.json、当前本地V22健康运行与操作指南。根控制器执行，不另起无必要实现agent。

- [x] Step1 保存HEAD/119原状态、V1–21 SHA、交接§3SHA（仅摘要），完整后端/前端baseline复制，不重置任何原有改动。
- [x] Step2 隔离TEMP PostgreSQL55450、training_migration(旧V21真实库副本只测迁移)、training_qa(全新纯假业务)、training_junit(专项测试)，后端8190/9201、前端5176。启动前/停止时验证端口、PID、声明数据目录，绝不触及5433。比65历史表数量/摘要与V21→22前后一致/五新表空。
- [x] Step3 最终TEMP backend clean package、前端全套和build；PG专项；HTTP完整教练计划和会员反馈闭环，重试/越权及原booking/finance不改；桌面教练→会员→教练/经理/HQ查看/监督归档、筛选/分页/体测、无console错误；留隔离假资料截图。
- [x] Step4 最终整体独立审查；修复Important/Critical并复验受影响项。核验当前源与最终tested制品SHA、旧迁移字节未变。
- [x] Step5 备份真实fitness_db和旧JAR，验证现运行后端路径/PID再停并安装tested JAR；启动prod8080/9091，验证V22旧65表数据摘要不变/新表空，再以正常管理员登录、只读新接口与原续费/潜客/交班、登出测试会话；明确auth metadata变化。运行start-fitchain -NoBrowser通过；前端localhost5173可用。
- [x] Step6 更新指南/事实索引/AGENTS/README和交接现状/日志（§3SHA前后一致）。保持真实服务可用，停本次QA服务，退出测试账号并关闭本次QA标签。git diff --check、证据JSON解析、敏感令牌扫描、源/制品最终一致，ledger完成，无提交/服务器更新。

## 自检结果

设计覆盖三项交付任务；Task1输出API契约供Task2、两任务输出制品供Task3。共享文件仅权限→路由菜单概念接口，无实现并发。Spec唯一权威；后台一次只有一个implementer。既有共享main授权接续优先于skill新worktree/提交默认；新鲜TEMP完整副本保护当前运行JAR，且保存baseline区分继承改动。用户直接推荐执行指令覆盖重复批准门槛。
