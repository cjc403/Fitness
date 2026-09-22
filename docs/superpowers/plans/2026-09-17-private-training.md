# Private Training Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Keep tests and production deployment verification as separate gates.

**Goal:** 实现用户批准的预约预占、双确认消课、可配置提成、月结审核及安全线上发布。
**Architecture:** 新 training 模块处理课次状态和结算；BookingService 调用 reserve/release，CommercialService 共享权益行锁并检查未结束预约。新表不改历史预约。
**Tech Stack:** Java17、Spring Boot3.3.13、JPA、Vue3/TypeScript、PostgreSQL、Flyway。
**Spec:** docs/superpowers/specs/2026-09-17-private-training-design.md

## Global Constraints
不追溯扣课；不改历史迁移；未经用户授权不改登录密码；不向日志/响应暴露密钥；本地验证后必须部署并在线验收。目录不是 Git 仓库，保留源码压缩备份，不擅自初始化仓库。2026-09-17用户明确授权重置后，仅重置无法使用统一密码登录的 member，并保留本人首次改密保护。

## Task 1: 课包预占与生命周期
Files: training/{entity,repository,service,controller,dto}；BookingService、BookingRequest、MemberPortalService/Controller；MemberEntitlementEntity/Repository；CommercialService；V8__private_training.sql。
Interfaces: TrainingService.reserve(BookingEntity, CourseScheduleEntity, Long entitlementId)、release(Long bookingId,String reason)、submit(Long bookingId)、confirm(Long bookingId)、reverse(Long bookingId,String reason)。GET /api/v1/training/lessons；POST /lessons/{bookingId}/{submit,confirm,reverse}。
- [x] 写 PrivateTrainingIntegrationTest：无课包私教预约 code!=0（业务成功为0，不是HTTP200）；预占一节 remaining=2/reserved=1；重复预约无二次占用；取消后reserved=0；签到后未扣课；会员确认后remaining=1/reserved=0。
- [x] 执行新用例并观察缺失实现导致失败，保留 red 阶段日志。
- [x] 实现预占、释放、提交、确认及锁。重复提交/确认返回已处理状态；确认前检查状态、归属、课程结束和会员有效性。
- [x] 验证并发抢最后一节只成功一次；错误门店/会员不改变权益；退款/冻结/转卡不能影响占用课包。

## Task 2: 提成与月结
Files: training/entities (CommissionRule,CoachSettlement)、repositories、TrainingService、TrainingController；CoachWorkbenchService；IntegrationTest。
Interfaces: PUT /commission-rules {coachId,rate}；GET /commission-rules；POST /settlements {coachId,month}；POST /settlements/{id}/approve；GET /settlements。
- [x] 写测试：规则20%+单价100产生提成20；重复生成月结只有一张；审核后拒绝冲正；未结算冲正恢复一节且有理由审计；会员/前台不能审核。
- [x] 观察失败；实现单价/比例/金额快照，唯一月结和教练锁；已审核结算不可更改，金额由服务端计算。
- [x] 完整后端测试，检查旧七个集成测试保持通过。

## Task 3: 五角色操作页面
Files: frontend/api/modules/training.ts、components/TrainingPanel.vue；MemberHome.vue、CoachWorkbench.vue、CommercialCenter.vue、BookingList.vue、memberPortal.ts、commercial.ts。
- [x] 用上面的接口集成测试先验证各角色可读写范围，再实现组件。
- [x] 会员私教预约选择课包，展示可用/预占；本人会员确认按钮；教练本人提交授课；总部/店长配置和审核；前台只查看结算。
- [x] npm run build；浏览器检查五角色页面、模块入口、管理控件范围、会员提成保密和空状态。课包选择、实际签课及审核动作由集成/API验收覆盖，未在生产真实订单上测试扣课。

## Task 4: 验证与部署
Files: docs/PRIVATE_TRAINING.md；deploy/linux/release-private-training.sh；AGENTS.md。
- [x] mvn test package；npm run build；在临时 PostgreSQL18执行新20项测试，在生产克隆库验证Flyway V1-V8启动与结构校验。测试禁用付费AI。
- [x] SSH复核线上健康和数据计数；执行数据库备份并保存旧JAR/前端；校验上传SHA256。
- [x] 运行发布脚本；检查健康、前端资源、五角色认证/权限与新模块；不对真实业务做测试扣次。
- [x] 保存验证报告和回滚路径；发布脚本有失败自动恢复旧JAR/前端分支，本次未触发生产回滚演练。交付证据见 docs/PRIVATE_TRAINING_VERIFICATION_20260917.md。
