# Agent Grounding Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; final whole-change independent review.

**Goal:** 修复错误课程定位和重复操作，升级六角色共用观察—执行—核对链。

**Architecture:** 保持 ComputerAgentService 现有协议；pageDriver 提供上下文命名、活动层限定、目标优先快照和真实后态摘要；pageTaskEngine 记录后态、检测重复循环。

**Tech Stack:** Vue 3、原生 DOM、Node test、Playwright/Edge、现有 DeepSeek 后端。

**Spec:** docs/superpowers/specs/2026-10-16-agent-grounding-design.md

## Global Constraints

- 共享当前 codex/computer-agent 分支；不部署、不推送，不修改既有迁移、真实业务或口令。
- 不另开产品操作浏览器；确认、权限及旧任务延迟写阻断保持。
- 真实验收写入仅4177→18080→55451独占 agent_browser 标记库。
- 保持现有请求字段和长度限制；不把代码/选择器或模型意图当执行成功。

## Review Focus

- Vue复用同行DOM后指向其他对象的旧引用必须拒绝。
- 重叠弹窗、传送下拉框和助手不能使背景动作重新可执行。
- 大列表的目标不能在60控件和6000文本预算中静默消失。
- 循环警告必须进入模型反馈且实际限制重复，正常多次独立业务步骤不能误阻断。
- 后态采集期间关闭/暂停/停止不能稍后误报完成或触发写入。

### Task 1: 观察与对象定位

Files: src/agent/pageDriver.mjs、pageDriver.d.mts；tests/page-observation.test.mjs。

- [x] RED：真实DOM夹具重现同名按钮、弹窗背景、数字值、晚列表目标和对象替换。
- [x] GREEN：实现上下文名称、活动层、状态/正文预算与目标排序；引用检查及后态摘要。
- [x] 验证原 driver 回归和新夹具全部通过。

### Task 2: 反馈、恢复和循环阻断

Files: src/agent/pageTaskEngine.mjs；tests/current-page-agent.test.mjs。

- [x] RED：下一轮规划缺失后态、反复同态动作和来回循环。
- [x] GREEN：记录真实后态，重复先反馈恢复，再限制重复/等待补充；保留确认和终态机制。
- [x] 验证暂停/停止/身份/单次确认旧回归。

### Task 3: 真实业务验收与交接

Files: tests/agent-grounding.live.browser.mjs、docs/qa/agent-grounding-20261016/、AGENTS.md、外层交接§0.17/§10。

- [x] 在修改前保存真实课程页观察缺陷证据。
- [x] 运行截图命令、错误弹窗恢复、大列表与多字段改价，断言正确对象与其他对象/字段不变，确认0→1与重复拒绝。
- [x] 六角色消息回归、全量npm test与构建、独立审查；修复重要发现。
- [x] 核对正常入口新模块、原23迁移字节、私有交接段落；关闭隔离服务，更新交接、本地提交。

## Execution Ledger

- 2026-10-16: 基线8f5e0ab，工作区干净。用户已明确直接按推荐方案修复，不重复设计询问；复用共享分支，内联实施。
- 2026-10-16: 观察及引擎回归均先观察RED再修复；独立复审补出日期浮层/双月预算、同标题门店反馈问题，真实Element Plus夹具覆盖日期和时间范围、受控值及焦点暂停guard。
- 2026-10-16: 重复真实验收出现已确认保存后再次准备保存；原失败保留在 final-before-render-settle.json。补写RED证明回执早于Vue消费和刷新，等待消费响应、读取及渲染后采后态，最终真实任务反馈已回到列表。
- 2026-10-16: 最终前端274/274、构建通过；四条真实模型课程任务与六角色消息均单次人工确认0→1、正确对象/其他字段不变、重复拒绝、同Page/document；教练权限负例0写。独立最终复审37专项及渲染期间暂停/停止夹具通过，无重要发现。
- 2026-10-16: 正常本地启动完成、入口及新模块/readiness200；23迁移及私有交接凭据段原字节保留，§0.17/§10同步。本轮专用QA服务按PID及命令验证后关闭，正常服务保留；仅本地提交，不推送/部署。
