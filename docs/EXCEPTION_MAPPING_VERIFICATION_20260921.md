# 客户端错误映射修复验证记录

状态：**本地修复与验证完成，尚未发布。** 本批只改后端异常处理与错误码，未涉及数据库迁移、未触碰服务器、未上线。

## 一、修复的问题

`GlobalExceptionHandler` 只有 4 个具体处理器（业务异常、Bean 校验、认证、权限），其余异常全部落进兜底的 `catch (Exception)`，统一返回 **HTTP 500 + code 1000「系统异常，请联系管理员」**。后果有两条：

1. 前端 `src/utils/request.ts` 对 `status === 500` 固定提示"服务器错误"，用户看不到真实原因；参数写错和后端宕机表现完全一样。
2. Prometheus 的 5xx 指标被客户端错误污染，无法用它判断服务健康。

实测被误报成 500 的场景：缺少必填查询参数、查询参数类型非法、请求内容类型不支持、HTTP 方法不支持、请求体 JSON 格式错误、已认证请求访问不存在的路径。

## 二、改动内容

| 文件 | 改动 |
| --- | --- |
| `common/exception/GlobalExceptionHandler.java` | 新增 6 个针对性处理器；4xx 用 `log.warn` 只记原因不打印堆栈，500 才 `log.error` 打堆栈 |
| `common/constant/ErrorCode.java` | 新增 `MEDIA_TYPE_NOT_SUPPORTED(1008)`（纯增量，不影响既有码） |
| `common/response/Result.java` | 新增 `error(ErrorCode, String, T)` 重载，让诊断信息可以随 `data` 返回 |
| `test/.../FitnessApplicationIntegrationTest.java` | 新增回归测试 `malformedRequestsYieldActionableClientErrorsInsteadOfServerErrors` |
| `docs/开发指导手册.md` | §5.4 记录新的状态码映射契约 |

映射结果：

| 场景 | 修复前 | 修复后 |
| --- | --- | --- |
| 缺少必填查询参数 | 500 / 1000 | **400 / 1001**「缺少必填参数：from」 |
| 查询参数类型非法 | 500 / 1000 | **400 / 1001**「参数 from 格式不正确」 |
| 请求体 JSON 无法解析 | 500 / 1000 | **400 / 1001**「请求体格式不正确」 |
| 请求内容类型不支持 | 500 / 1000 | **415 / 1008**「不支持的请求内容类型」 |
| HTTP 方法不支持 | 500 / 1000 | **405 / 1005**「请求方法 DELETE 不被支持」 |
| 已认证访问不存在路径 | 500 / 1000 | **404 / 1004**「请求的资源不存在」 |
| 字段级校验失败（`@Valid`） | 400 / 1001，明细被丢弃 | 400 / 1001 + `data` 回传字段明细 |

**刻意保持不变**：业务异常仍走项目既有的「HTTP 200 + 业务码」风格（例如登录密码错误 HTTP 200 / 1102），前端拦截器按 `code` 判断的分支逻辑完全不受影响。

## 三、证据

修改前运行时证据：`logs/wb-h2-run.log`（2026-09-21，6 种客户端错误均返回 500/1000，堆栈为 `MissingServletRequestParameterException`、`HttpMediaTypeNotSupportedException`、`NoResourceFoundException` 等）。

修改后门槛：

- 后端 `mvn test`：**75 / 75 通过**，0 失败 / 0 错误 / 0 跳过（原 74 项全部保持通过，新增 1 项）。日志 `logs/wb-mvn-test-fix.log`（exit=0）与 `logs/wb-mvn-gate.log`。
- 后端 `mvn test package`：测试阶段 75/75 通过；打包阶段见下方"操作注意"。
- 前端 `node --test tests/*.test.mjs`：**8 / 8 通过**，日志 `logs/wb-fe-tests3.log`。
- 前端 `npm run build`：退出 0，仅既有大 chunk 提示，日志 `logs/wb-fe-build2.log`。
- 部署脚本 `python -m unittest discover -s deploy/linux`：**16 / 16 通过**，日志 `logs/wb-deploy-tests2.log`。

修改后运行时实测（隔离 H2 实例，端口 8081，日志 `logs/wb-h2-run2.log`）：上表 6 个场景的 HTTP 状态与 `code`、`message` 全部符合预期；字段校验失败额外返回 `{"username":"用户名不能为空","password":"密码不能为空"}`；正常登录 / 正常报表 / 正常仪表盘 / 密码错误业务码均未受影响。

源码备份：`backups/source-before-exception-mapping-20260921/`（改动前 4 个文件的原始副本）。

## 四、操作注意：本地实例会锁住打包产物

本地 PostgreSQL 正式实例以 `java -jar fitness-backend/target/fitness-backend-1.0.0.jar` 运行时，Windows 会锁住该文件，导致 `mvn package` 在 `spring-boot:repackage` 阶段报：

```
Unable to rename '...target\fitness-backend-1.0.0.jar' to '...fitness-backend-1.0.0.jar.original'
```

而 `jar:jar` 阶段已经写入了一个**不含依赖、不可执行**的普通 jar，会把可运行的 fat jar 覆盖掉。

处理办法（二选一）：

1. 先停掉本地后端实例，再执行 `mvn test package`，然后重新启动；
2. 或在不占用该路径的位置构建，再把产出的 fat jar 复制回来（写覆盖不受该锁限制）。

**建议把"打包前先停本地后端"写进日常习惯**，避免再出现半成品 jar。

### 4.1 实际发生的事故（2026-09-21，已修复）

本次采用了上面的方式 2，但**第一次尝试时犯了一个更严重的错误**：在后端实例仍在运行时直接覆盖了 jar 文件。

时间线：

| 时间 | 事件 |
| --- | --- |
| 14:05:39 | 登录失败仍正常返回业务错误 1102，实例健康 |
| 14:07:12 | 覆盖 `target/fitness-backend-1.0.0.jar` |
| 14:07:44 | 运行中的实例首次报「系统异常」 |
| 14:07–14:17 | 共 6 次同类异常，浏览器登录页弹出"服务器错误" |

根因：**Windows 下 `Copy-Item -Force` 是原地截断重写同一个文件**，正在运行的 JVM 手中那个 `ZipFile` 句柄于是指向了内容已变的文件。已加载的类不受影响，但**后续按需加载的类会失败**：

```
java.lang.ClassNotFoundException: org.springframework.dao.support.ChainedPersistenceExceptionTranslator
```

这不是代码缺陷——进程本身已经损坏，必须重启。修复动作：停掉该进程 → `mvn -B clean package`（此时无锁，产出完整 fat jar）→ 用项目自身的 `scripts/start-production.ps1` 方式（`SPRING_PROFILES_ACTIVE=prod` + 既有环境变量）重新启动 → 校验 `9091` 健康与五角色登录。

**结论与纪律**：**绝不能覆盖正在运行的 jar**。`mvn package` 在实例运行时失败其实是 Windows 在保护你——绕开它之前，必须先停实例。同理，本地调试实例（如另起的 8081）也会锁住同一个 jar，打包前要一并停掉。

修复完成后的实际验证：停掉损坏进程与调试实例 → `mvn -B clean package`（75/75 测试通过、BUILD SUCCESS、`repackage` 正常完成）→ 以 prod 配置重启，绑定 `0.0.0.0:8080` + `127.0.0.1:9091`。校验结果：健康检查 UP；登录返回业务码 1102（说明类加载恢复正常）；`DELETE /api/v1/auth/login` 返回 `{"code":1005,...}`（本次修复已在运行实例生效）；经前端 5173 代理访问后端正常；重启后日志无 ERROR、无新的 `ClassNotFoundException`。日志 `logs/wb-backend-restart.log`。

### 4.2 补充：从 WorkBuddy 终端启动 Spring Boot 会绑到错误端口

排查重启时发现 WorkBuddy 进程环境含 `SERVER__PORT=53669` 与 `SERVER__HOST=127.0.0.1`，Spring Boot 的松散绑定会把双下划线当层级分隔符解析成 `server.port` / `server.address`，于是应用去抢 53669（已被 WorkBuddy 自身占用），报 `Port 53669 was already in use`。这两个变量**只在 Process 作用域**，用户双击 `start.bat` 启动的进程不受影响。若需要从 WorkBuddy 终端启动，先摘掉这两个变量（或显式传 `--server.port=`，命令行参数优先于环境变量）。

## 五、不在本批范围内

- 未修改任何 Flyway 迁移，V1–V9 保持字节不变。
- 未改动前端源码、未改动权限模型、未改动资金口径。
- 未连接生产或服务器；未部署；`GlobalExceptionHandler` 之外的其他兜底行为未调整。
- 未新增 406（Not Acceptable）与超限上传的专门映射——出现频率低，暂无证据表明需要。
