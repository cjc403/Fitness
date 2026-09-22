# 退款与精确价值账验证记录

状态：开发进行中，尚未发布本批生产版本。不得把下面准备步骤当作上线完成。

## 修改前

- 本地后端：`logs/finance-baseline-backend.log`，mvn test package 退出0；原7+私教20项测试全部执行，0失败/错误/跳过。
- 本地前端：`logs/finance-baseline-frontend.log`，npm run build退出0；既有500kB大chunk提示仍存在。
- 源码备份：`backups/source-before-refund-finance-20260917.zip`。
- 服务器：fitchain/nginx/postgresql-18均active；healthcheck通过；生产前端仍20260917-private-training。
- 修改前真实5角色登录通过。会员必须首次改密的code1105保护仍在，不按权限故障误判、不绕过。

## 独立环境与测试防护

- 克隆库：`fitchain_stage_finance_20260917`，源备份保留在 `/opt/fitchain-stage-finance-20260917/before.dump`，不与生产共库。
- 消耗性Hibernate集成测试库：`fitchain_test_finance_20260917`，测试角色 `fitchain_finance_test`，生产账号不变。
- stage端口18080/19091仅loopback，测试AI禁用。
- `test_finance_acceptance_guard.py`验证5种禁止目标（含生产写、错误库/端口组合）。RED日志和GREEN日志：`logs/finance-deployment-guard-{red,green}.log`；GREEN退出0。
- prepare/start/release脚本服务器bash-n通过；验收CLI在生产mutation组合下提前拒绝，不触发SQL/HTTP。

## 2026-09-18继续验证（不是上线）

- 原V1–V8迁移文件SHA256与开发前源码快照一致。
- V9在独立克隆库通过事务内DDL试执行：七张新表、约束和索引创建成功，18份历史权益仅生成UNVERIFIED价值账户；随后ROLLBACK，查询确认新表未留存。
- 该次试执行日志：`logs/finance-v9-stage-transaction-dryrun.log`，退出0。对应V9 SHA256：`A4D6A957E0349127E34F73F79BB0547ADAC1A014A24FBCCB2C21B36420B1760B`。最终制品仍需正式Flyway迁移与应用验收，本次不是迁移完成证据。
- V9补充不可覆盖的转卡核验事件details_json后再次通过相同事务试执行并回滚，18份历史卡仍仅UNVERIFIED。最终候选迁移SHA256：`F5C036C89AA0B0531E61E3F1A8D07EA7CC65081068F022B716DEC21784BAE256`；日志`logs/finance-v9-final-stage-transaction-dryrun.log`退出0，details_json非空约束存在，回滚后无新表留存。
- 使用独立测试角色与loopback SSH隧道连接消耗性测试库；凭证只进入受保护临时文件或进程环境，不记录内容。
- 2026-09-19独立PostgreSQL复测退出0：退款真实HTTP28项、金额3项，失败/错误/跳过均为0；测试结束后专用库连接0、测试角色所属表0。证据`logs/finance-task1-postgres-rerun.log`及Surefire XML。首次被会话中断的运行不计为通过。
- 发布加固脚本本地9项单测、Bash语法和Python编译通过；尚未安装Nginx配置或发布。独立复审通过前不触碰服务器发布状态。

## 本批完成门槛（当前未核定）

- 后端新退款/余额/尾差/冲正/暂停/多来源/作用域/重复与并发测试及原测试全部通过。
- 新财务口径、对账差异及CSV真实输出和注入测试通过。
- 前端类型构建、真实浏览器员工审批链/会员只读/错误状态通过。
- PostgreSQL V9增量迁移、原迁移校验和和历史行数不变；独立库完整资金写验收通过。
- 最终后端和前端制品SHA256、stage验收标记与上传包一致。
- 生产数据库/旧JAR/旧前端备份完整；上线真实登录和只读新API/公网页面正常。
- 测试服务/隧道关闭、测试角色NOLOGIN、临时密码文件清除，生产健康/监控恢复。

本批不实现真实支付网关，不在生产给真实会员创建测试订单、扣次或退款。不宣称税务会计合规、无限并发或绝无故障。
