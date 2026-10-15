# 手机与 Agent 前端验证（2026-10-15）

本目录为前端实现证据。浏览器业务数据由隔离上下文中的 HTTP mock 提供，未使用现有账号、真实令牌或真实业务写入。**这不是后端集成验收，也不是真机验收。**

- `frontend-tests.txt`：`npm test`，210 项执行通过，0 失败、0 跳过。
- `frontend-build.txt`：`npm run build`（vue-tsc + Vite）成功。保留现有的大块产物体积提示。
- `browser-verification.json`：六角色首页、390/360/320 手机宽度、1366 桌面宽度、角色菜单过滤、助手只读查询通过，无页面运行时错误。
- 会员、顾问、教练分别验证 `book_course`、`book_trial`、`training_draft`：结构化表单、预览不写入、离线确认禁用、确认请求仅 `{}`、成功回执。教练动作无需手写 JSON，日期时间按本地 ISO 格式发送。
- `reception-member-dialog-320.png`：现有新增会员弹窗在 320 宽度内，测试只打开和关闭，未保存。
- `pwa-verification.json`：生产 preview 的真实 Service Worker 测试。缓存只含公开 `offline.html`、manifest 与图标。带认证的模拟 API 和模拟私人 HTML 未缓存；断网导航只显示公开离线说明。
- 其余 PNG：六角色手机/桌面与三个写工具表单/预览截图，均使用模拟数据。

重跑浏览器测试需先启动本地开发服务 4178，生产 PWA 测试需先构建并启动 preview 4179。复用机器上已有 Playwright，无新增产品依赖：

```powershell
$env:FITCHAIN_PLAYWRIGHT_MODULE = '<已有 Playwright 的 index.mjs 绝对路径>'
node tests/mobile-agent.browser.mjs
node tests/pwa.browser.mjs
```

可用 `FITCHAIN_QA_BASE` / `FITCHAIN_PWA_BASE` 改本地测试地址。脚本始终模拟业务 API，不用于真实业务确认。真实隔离数据库/API联调、远程模型验证、HTTPS手机安装和物理手机键盘检查由主任务另行记录。

补充真实后端验收见仓库 `docs/qa/mobile-agent-20261015/real-api/verification.json`；这里原有mock截图仍按mock解读。训练动作嵌套参数修复增加3项测试（RED已保留），201/201为最终前端结果。真实模型请求401，物理手机未验证。

手机登录403修复增加6项（含真实Vite代理子测试），最终207/207和构建通过。LAN浏览器对正常本地接口仅发送空字段校验，无真实凭据/业务写入；详情见仓库docs/qa/mobile-agent-20261015/lan-login-verification.json。

电脑版白屏后续修复：新增3项缓存隔离回归，最终210项通过；代理测试缓存改到独占TEMP。两个真实浏览器登录页恢复可见，两个入口共20个入口依赖在全量测试/构建后均200。见仓库docs/qa/mobile-agent-20261015/cache-isolation-verification.json。
