# 健身房管理系统

> AI接手开发先读 [详细接手指南（2026-10-19）](docs/AI接手开发指南_20261019.md) 和 AGENTS.md；源码、运行版本、线上状态及原始QA边界在指南中分别记录。

## 项目简介

这是一个可直接运行的连锁健身房管理系统，采用前后端分离架构，支持多场馆、会员、课程排课、预约签到、经营看板和智能客服。

## 手机端与六角色 Agent（本地 V23）

六角色 Agent 已改为**直接操作当前标签页**：双击 `start.bat`，登录后在助手会话选择“网页执行”，描述目标即可在正在看的页面中切换、点击和填写。提交前展示实际冻结请求，由你逐次确认；关闭助手会暂停。网页操作不需要另开 Edge，本机执行器仅为课表文件保存/请求打开提供可选能力。范围与验收见 [六角色网页 Agent 使用说明](docs/六角色电脑Agent_20261016.md)。

默认模型为 **DeepSeek-V4.1-Flash**，配置使用官方 API 名称 `deepseek-flash`。文字、工具调用及合成图片识别已实测；当前网页 Agent 仍通过 DOM 与文字工作，截图输入尚未接入。见 [模型配置说明](docs/AI_INTEGRATION.md)。

双击 `start-mobile.bat` 获取同一局域网的手机入口；六角色使用同一账号与权限。操作方式、真实模型调用、预览确认与隔离保护及真实验收记录见 [手机端与六角色Agent](docs/手机端与六角色Agent_20261015.md)。本轮没有更新线上服务器。

## 已完成功能

本次私教闭环使用步骤见 [私教签课与月结指南](docs/PRIVATE_TRAINING.md)，本地及线上交付证据见 [2026-09-17 验收记录](docs/PRIVATE_TRAINING_VERIFICATION_20260917.md)。

- JWT 登录、Refresh Token 自动续期、失效会话自动退出、六类角色与细粒度操作权限控制
- 员工账号管理：分级创建、门店分配、资料编辑、启停和密码重置
- 会员账号闭环：公开注册、已有档案开通账号、首次登录强制改密
- 连锁场馆增改查与门店级数据隔离（店长/教练/前台仅访问所属门店）
- 会员增改查、搜索、分页、暂停/恢复、黑名单
- 课程模板、教练排课、容量与时间校验
- 代会员预约、重复预约/状态/容量校验、取消与签到
- 会员自助端：查看本人档案、浏览可约课程、预约、预约记录与限时取消
- 教练工作台：本人排课、学员名单、课程统计与签到
- 商业会籍中心：月卡/季卡/年卡/次卡/私教课包、销售开卡、续费、冻结/解冻、转卡与退款
- 财务业务闭环：订单、微信/支付宝/银行卡/现金收退款流水、会员合同、收据与发票记录
- 二维码门禁：会员动态展示真实二维码，前台扫码核验、次卡自动扣次、完整入场记录
- 课程履约：满员候补、候补自动递补、会员请假、教练爽约标记与处罚积分
- 私教消课闭环：课包预约预占、教练提交授课、会员本人签课、一次性消课、取消/爽约释放、冲正审计
- 私教提成与月结：可配置比例、签课金额快照、按实际确认月份生成/更新草稿、次月审核及角色隔离
- 教练经营数据：完成课时、履约时长、已签私教实际产值和提成；历史未关联预约与团课不计入私教结算
- 会籍预警：到期、7/30 天临期和剩余次数不足提醒
- 实时经营看板：会员、课程、预约、出勤率、近 7 日趋势
- DeepSeek 智能客服：多轮业务问答、上下文排课、敏感信息脱敏、限流熔断与本地知识库降级
- 个性化课程推荐：结合训练目标、预约历史、时间偏好、课程容量与新鲜度进行可解释评分
- Swagger API 文档、统一响应、全局异常与参数校验
- H2 本地持久化数据库与自动演示数据；PostgreSQL + Flyway 生产部署
- Actuator 健康检查、Prometheus 业务指标、日志轮转和 PostgreSQL 备份/恢复脚本
- 精确价值账与退款闭环（V9）：权益价值账户、按剩余价值出具退款报价、申请/审核/拒绝/撤回/登记支付、来源资金核对与退款分摊、幂等键防重复提交
- 会员自助退款：会员可在会员中心自行发起退款申请与撤回；金额由服务端按价值账核定，管理员审核
- 会员自助购买：会员可直接购买本门店会籍；金额取服务端商品标价，支付渠道固定标记为 `SELF_SERVICE`
- 会籍商品上下架：卖过的商品只能下架（保护订单与权益历史），下架后会员端立即不再展示
- 排课与教练账号绑定：排课必须绑定真实教练账号，教练工作台才看得到自己的课；私教排课未绑教练会醒目标记
- 私教消课自检：管理端面板直接显示"私教课程 / 已排期 / 已绑教练 / 有效课包"四项前置条件是否齐备
- 站内通知（V10）：排课变更、商品下架、退款审批结果主动推送给会员，会员端带未读角标
- 门禁二维码一次性化（V10）：改为 60 秒短时效一次性令牌，截图无法重复入场
- 销售幂等（V10）：同一幂等键只落一单，杜绝连点或重试造成的重复开卡
- 会员续费与回访（V19）：今日/逾期回访、7/30 天临期、高风险筛选、结构化沟通结果、下次回访、顾问/店长/总部范围统计及防重复提交；实际续费依据已支付续费订单的退款后净额。使用与验证见 [续费与回访指南](docs/会员续费与回访_20261003.md)。
- 潜客与体验课（V20）：咨询建档、联系任务、跟进历史、独立体验预约与到店结果、实际开卡或核验订单关联、渠道转化统计；包含门店/顾问隔离、幂等、版本冲突及事务回滚保护。使用与验证见 [潜客与体验课指南](docs/潜客与体验课_20261009.md)。

- 前台交班与日结（V21）：每店同时一个班次，逐渠道核对实际收款/退款、差异说明、提交交班、独立店长复核、退回修正、固定流水快照和前后审计；总部跨店、本店隔离。使用与验证见 [交班与日结指南](docs/交班与日结_20261010.md)。

- 教练训练计划与执行记录（V22）：草稿/发布/归档、动作目标、训练执行快照、会员反馈、错误作废及审计；教练、门店监督与会员本人隔离。登记不签到或扣课。见 [训练计划指南](docs/教练训练计划与执行记录_20261011.md)。

## 技术栈

### 后端
- Java 17 LTS
- Spring Boot 3.5.16
- Spring Data JPA
- Spring Security 6.3 + JWT
- H2（本地零配置）/ PostgreSQL 18（生产环境）
- Flyway 11.15（数据库迁移，与 Spring Boot 3.5 配套验证）
- MapStruct 1.5.5 (DTO转换)
- Swagger/OpenAPI 3 (API文档)

### 前端
- Vue 3.4.x
- TypeScript 5.x
- Element Plus 2.5.0+
- Vite 7.3.6
- Vue Router 4.x
- Pinia 2.x
- Axios 1.x

## 项目结构

```
hh-gym/
├── fitness-backend/          # 后端项目
│   ├── src/main/java/
│   │   └── com/gym/fitness/
│   │       ├── config/       # 配置类
│   │       ├── common/       # 公共模块
│   │       ├── module/       # 业务模块
│   │       └── security/     # 安全相关
│   └── src/main/resources/
│       └── db/migration/     # Flyway迁移脚本
└── fitness-frontend/         # 前端项目
    └── src/
        ├── api/              # API接口封装
        ├── views/            # 页面视图
        ├── router/           # 路由配置
        ├── store/            # 状态管理
        └── utils/            # 工具函数
```

## 快速开始

### 最简单的启动方式（Windows）

双击项目根目录的 `start.bat`。现在使用的是自修复启动器，只有数据库、后端接口和前端登录页全部通过真实健康检查后才会打开浏览器。它会自动处理以下常见问题：

最终检查使用后台健康状态、只读场馆接口 `/api/v1/auth/venues`（校验成功码与列表格式）和登录页面。PostgreSQL 的生产配置默认关闭 API 文档，`/api-docs` 返回 404 属于正常情况，不会阻止启动。

- PostgreSQL 显示“正在运行”但实际无响应时，请求管理员权限并自动重启服务
- 使用本机直连、关闭无意义的 SSL 探测并设置连接超时，避免数据库连接长时间假死
- 启动前验证数据库账号确实能够执行查询，而不只是检查端口是否存在
- 检查 Java、Maven、Node.js、npm、项目文件和必要配置；首次缺少配置时安全提示输入
- 检测并清理本项目遗留的后端/前端进程，但绝不会擅自结束其他软件
- 前端依赖缺失或首次启动失败时自动执行 `npm install` 修复并重试
- 后端启动失败时重新确认数据库状态并自动重试一次
- 使用启动互斥锁，连续或重复双击不会产生并发构建和端口冲突
- 后台运行并将信息写入 `logs/`，关闭启动窗口不会关闭系统

若遇到断电、磁盘损坏、文件被删除或密码被人为修改等无法安全自动修复的问题，启动器会明确显示原因，而不再只出现“服务器错误”。此时双击 `diagnose-startup.bat`，会生成不包含密码和密钥值的 `logs/startup-diagnostics.txt`，便于检查或发给开发者。重复双击不会重复占用端口：

- 系统地址：http://localhost:5173
- 后端接口：http://localhost:8080
- Swagger（仅启用文档的开发环境）：http://localhost:8080/swagger-ui.html
- 登录账号：使用“角色与演示账号”章节列出的五类正式账号

本项目已经在 `.tools/` 中安装了项目专用 JDK 17 和 Maven，不需要配置系统环境变量。首次运行若没有 `node_modules`，脚本会自动执行 `npm install`。

### 环境要求（手动启动时）

- JDK 17+
- Maven 3.9+
- Node.js 20.19+（20.x）或 22.12+；Vite 7 构建要求，推荐使用当前受支持的 LTS
- PostgreSQL 18（仅 `prod` 生产配置需要）

### 后端启动

本地默认使用 `fitness-backend/data/fitness.mv.db`，无需安装或启动数据库。启动项目：
```bash
cd fitness-backend
mvn spring-boot:run
```

访问 Swagger 文档：http://localhost:8080/swagger-ui.html

### 前端启动

1. 安装依赖：
```bash
cd fitness-frontend
npm install
```

2. 启动开发服务器：
```bash
npm run dev
```

3. 访问：http://localhost:5173

## 数据库与演示数据

开发人员单独运行 `scripts/run-backend.bat` 时使用本地 H2 演示环境，`DemoDataInitializer` 会写入 2 家场馆、18 名会员、4 个课程和近期预约数据。数据会持久保存在 `fitness-backend/data/`。日常双击 `start.bat` 使用的是 PostgreSQL 正式数据。

生产环境使用 PostgreSQL 和 Flyway。推荐在项目根目录运行：

```powershell
.\scripts\setup-postgres.ps1
.\scripts\start-production.ps1
```

脚本会交互式读取密码和 JWT 密钥，不会把秘密写入仓库。服务器非交互部署需设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、`APP_ADMIN_PASSWORD` 环境变量，完整步骤见文末部署指南。

已有五角色账号、只希望补充展示用业务数据时，可执行一次：

```powershell
.\scripts\start-production.ps1 -SeedSampleData
```

该模式会幂等补充 2 家场馆、18 名会员、10 个课程模板、近 7 天和未来 30 天排课，以及预约/签到/取消记录；不会创建或修改任何登录密码。初始化完成后，日常仍使用普通的 `start-production.bat` 或 `start-production.ps1` 启动。

## 角色与演示账号

仅在开发人员手动启动 H2 演示环境时，下面账号的密码统一为 `password`。双击 `start.bat` 进入 PostgreSQL 正式环境时，账号沿用已设置的安全密码，不会被启动器重置：

| 账号 | 角色 | 主要权限 |
| --- | --- | --- |
| `admin` | 总部管理员 | 跨门店看板、全部业务、场馆创建与切换 |
| `manager` | 店长 | 本店看板、会员、课程排课、预约签到、场馆资料 |
| `coach` | 教练 | 专属工作台、本人排课与学员、查看课程、到店签到 |
| `reception` | 前台 | 会员维护、查看课程、预约与签到 |
| `member` | 会员 | 浏览课程、自助预约、我的预约、智能客服 |

权限同时在三层生效：后端接口鉴权、门店数据范围校验、前端路由/菜单/操作按钮控制。不能依靠修改前端地址绕过后端权限。

## 分阶段优化路线

1. 角色权限体系：已完成。
2. 会员自助预约页面与教练工作台：已完成。
3. PostgreSQL 正式部署、自动备份与运行监控：已完成。正式数据库运行在本机 PostgreSQL 18（端口 5433），每日 02:00 自动备份。
4. DeepSeek 大语言模型与个性化课程推荐：已完成代码接入；配置 API Key 后即可启用真实模型回答。
5. 员工账号管理与会员账号开通：已完成，并已在 PostgreSQL 正式环境验证五类角色登录。
6. 商业健身房核心业务：已完成首版闭环，并已迁移到 PostgreSQL 正式数据库。
7. 精确价值账与退款闭环、会员自助购买/退款、私教消课自检、排课与教练账号绑定：已完成（V9）。
8. 销售幂等、门禁一次性二维码、站内通知、N+1 消减与索引补齐：已完成（V10）。

## 验证

后端默认测试使用**内存 H2**。必须在新鲜TEMP源码副本运行以下命令，避免覆盖运行中的JAR；PG专项另外在明确的隔离库执行：

```powershell
# 在仓库根目录执行；仅复制源码到新临时目录，不覆盖运行中的JAR
$repoPath = (Get-Location).Path
$verifyBuildPath = Join-Path $env:TEMP ('fitchain-verify-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $verifyBuildPath | Out-Null
Copy-Item -LiteralPath (Join-Path $repoPath 'fitness-backend\src') -Destination $verifyBuildPath -Recurse
Copy-Item -LiteralPath (Join-Path $repoPath 'fitness-backend\pom.xml') -Destination $verifyBuildPath
$env:JAVA_HOME = Join-Path $repoPath '.tools\jdk-17'
Push-Location $verifyBuildPath
try { & (Join-Path $repoPath '.tools\maven\bin\mvn.cmd') -B clean package } finally { Pop-Location }
```

前端（类型检查 + 构建 + 单元测试）：

```bash
cd fitness-frontend
npm ci
npm run build             # 含 vue-tsc 类型检查
node --test tests/*.test.mjs # 181项，含异步/续费/回访/潜客/交班/训练及会话失效行为
```

## 版本与文档状态

- 数据库迁移由 Flyway 管理，本地源码和实际 PostgreSQL 当前到 **V22**（训练计划、动作、执行快照和审计）；本轮只更新本地，最终运行与备份证据见 `docs/qa/training-plans-20261011/verification.json`。线上沿用2026-10-02 **V17**验收记录，本轮未连接或部署。`ddl-auto=validate` + `validate-on-migrate=true`，已应用 V1–V22 迁移不可修改。
- **当前功能与待办以 [docs/当前功能与待办.md](docs/当前功能与待办.md) 为准**；`docs/后续完善计划.md` 是历史规划存档，已与实现脱节。

## API接口规范

- 管理端：`/api/v1/admin/...`
- 会员端：`/api/v1/member/...`
- 教练端：`/api/v1/coach/...`
- 公共接口：`/api/v1/common/...`

## 开发团队

- 成员1：前端开发及测试
- 成员2、3、4：后端开发
- 成员4：前后端衔接把控、功能测试

## 更多文档

详见 [docs/](docs/) 目录下的开发指导手册。

PostgreSQL 部署、监控和备份操作见 [docs/POSTGRESQL_DEPLOYMENT.md](docs/POSTGRESQL_DEPLOYMENT.md)。

DeepSeek 配置、安全降级和推荐算法说明见 [docs/AI_INTEGRATION.md](docs/AI_INTEGRATION.md)。

员工与会员账号创建、首次改密和权限边界见 [docs/ACCOUNT_MANAGEMENT.md](docs/ACCOUNT_MANAGEMENT.md)。

商业会籍、订单财务、二维码入场和课程履约演示见 [docs/COMMERCIAL_OPERATIONS.md](docs/COMMERCIAL_OPERATIONS.md)。

### 2026-10-02 本地安全加固

检查结论、已修复问题、验证证据和后续建议见 [项目安全与优化检查](docs/项目安全与优化检查_20261002.md)。保留已有外观改动，未发布、推送或改变现有业务库。本地运行中的旧版本未重启；下次通过 `start.bat` 重建启动后才使用新代码和 V18。生产 API 文档默认关闭，服务默认绑定 loopback；特殊网络拓扑可显式配置 `SERVER_ADDRESS`。

### 2026-10-03 启动器修复

修正启动器对生产 API 文档的强制检查：改为验证只读场馆接口，并在诊断报告标明文档关闭时的 404 属于正常情况。实际执行 `start.bat` 已成功，默认浏览器登录页正常；只读确认本地 PostgreSQL 已应用 V18，未执行线上发布。回归检查：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/startup-endpoints.test.ps1`（10 项）。此前“本地旧版本未重启”的记录为 2026-10-02 的历史状态。
