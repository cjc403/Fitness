# Computer Agent Implementation Plan

> Use writing-plans, TDD, dispatching-parallel-agents for independently owned components, and a final independent security review. Root performs integration and verification. User explicitly approved implementation and standing recommended choices; no repeated approval gates.

**Goal:** Genuine task→observe→act→feedback execution in a visible controlled browser, with local file artifacts and human-confirmed writes.
**Architecture:** Existing Java model transport emits bounded decisions; a Node/Playwright companion executes them; Vue conversation and task panel control the companion through a loopback-only dev bridge.
**Tech Stack:** Java17/Boot3.5, Vue3/TS, Node24, playwright-core1.64.1/Edge.
**Spec:** `docs/superpowers/specs/2026-10-15-computer-agent-design.md`.

## Global Constraints
Local only. Current feature branch `codex/computer-agent`, same shared directory; no historical migration edits, secret output, arbitrary JS/Shell/model paths, deployment or push. Test writes only in marked isolated DB. Product runtime cannot depend on Codex bundled packages.

## Review Focus
Cross-account/venue/session controls; browser POST escaping the confirmation gate; proxy/absolute base bypass; stale DOM references and prompt injection; output path/symlink/CSV injection; pause/stop races and ambiguous write outcomes.

## Task 1: Backend decision boundary
Own only new Java computer DTO/controller/service and focused tests. Exact session/next/action contracts from spec; reuse identity and model transport. No schema change.
- [x] Write failing tests for role paths, invalid/stale refs, model false success, denied sessions and invalid data/unknown fields; observe RED in TEMP.
- [x] Implement bounded single-action response, server-authored summaries, safe bootstrap userInfo and role-filtered route catalog.
- [x] Verify focused tests in TEMP; root later executes full fresh-copy package.

## Task 2: Local task runner
Own `fitness-agent-runner/**` only: package manifest/lock, Node HTTP, auth/session, browser adapter, task engine, artifacts, tests. Use exact spec API. Bridge secret runtime file and runner settings documented to root. `npm test`, browser actual read/click/fill/confirmation gate tests against local synthetic app. No normal project business writes.
- [x] Write failing engine/ownership/write-gate/artifact tests and observe RED.
- [x] Implement bounded loop, independent browser context, event-based confirmation, pause/resume/stop/input and safe file operations.
- [x] Verify full runner tests and real browser boundary tests; report exact exported server entry/config interface.

## Task 3: Task panel and local API
Own new frontend `src/components/ComputerAgentPanel.vue`, `src/api/modules/computerAgent.ts` and dedicated tests only. Root changes AssistantPanel and Vite. Component exposes `submitGoal(text):Promise<void>`; emits `reply(text:string)`; includes own goal form and current task, state/progress/image, pause/resume/stop/confirm/input and artifact results. Session generation guards all async reads/writes. API uses relative `/local-agent/v1` and authStore token, Result envelope, request-current guards; no user-provided base URL.
- [x] Write failing runtime tests for stale identity, offline, polling/teardown, confirmed request ID and stop.
- [x] Implement component/API against spec, verify typecheck and focused tests without editing shared integration files.

## Task 4: Root integration / launch / proof
Root owns Vite loopback bridge, AssistantPanel conversation mode, start-local-agent.ps1 and launcher integration, final docs/evidence. Writes runner runtime config protected in LocalAppData, strips inherited backend secrets from companion environment, no LAN binding. Test desktop allowed vs LAN/foreign Origin denied and no private bridge fields in health.
- [x] Bridge/launcher integration RED→GREEN; install product dependency only in runner.
- [x] Connect conversation to computer panel, default computer mode when executor is available; preserve business tools and prior actions. Clear private state on logout/change venue.
- [x] Fresh backend full package + frontend tests/build + runner tests; marked-isolated real-model tasks and read-only normal task; browser/stop/write-confirmation/file boundary proof.
- [x] Independent final review, fix important findings with regressions; backup/activate local tested build, preserve data/migrations; maintain handoff and save local stage.

## Ledger
- Baseline f544f71, clean checkout. Feature branch created in current user-visible workspace; normal services remain at prior verified build until explicit local activation step. Old weekly label unchanged.
- API contract preflight: Tasks1/2 exchange session + single decisions; Tasks2/3 exchange Result Task/control API; Task4 supplies loopback secret/proxy and conversation integration. No shared write ownership between implementers.
- 用户补充：六角色都必须好用。Task1全角色路由/推荐任务与数据范围；Task2身份驱动首页；Task3六角色任务提示与界面；Task4六角色真实登录和浏览器任务分别验收，不只验证教练。

- 2026-10-16闭环：后台377项（364执行通过/13旧PG跳过，电脑规划79、传输23、真实PG11），前端236及构建，执行器28，启动10+7+7。六角色真实模型任务和面板通过，真实顾问表单11步（含填写/下拉/冻结确认）成功，0→1条且重复409，CSV6条599字节并请求系统打开。
- 独立审查修复：并发写槽覆盖、严格业务回执、停止后文件副作用、不可完整预览请求拒绝、确认中超时结果不确定、显式文件打开完成条件、自定义下拉/单选及会话等待中撤销。后续收口修正工具回执时间顺序、强制电脑动作、短且新鲜的元素引用、同范围文件去重及否定只读语义；普通业务问答保持auto。
- 全量构建发现既有续费测试微秒舍入竞态，隔离确定性RED复现后仅修复时钟夹具，原断言保留；最终全量GREEN。
- 正常本地已备份并启用经过全量验证的JAR，业务数量和密码哈希不变，V1–V23原字节不变。启动/复用及正常库真实模型只读电脑任务通过。未确认任何正常业务写入，未更新服务器或推送，周标签未动。
