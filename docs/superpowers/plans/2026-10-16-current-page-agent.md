# 当前网页 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 用户在会话下达目标，Agent 直接操作当前 FitChain 标签页，六角色适用。

**Architecture:** 当前 document 驱动器 + 单例任务引擎 + 统一请求确认边界；复用后端单步规划与身份接口。本机执行器只为当前页任务提供可选文件能力。

**Tech Stack:** Vue 3 / TypeScript / 原生 DOM / Axios / Node / Playwright。

**Spec:** docs/superpowers/specs/2026-10-16-current-page-agent-design.md

## Global Constraints

- 仅本地；不改 V1–V23；不覆盖正常业务数据；桌面验收。
- 六角色使用现有权限；当前标签页必须实际变化，禁止另开浏览器替代。
- 写入逐次冻结、人工确认、禁止重放；身份失效后不继续执行。

## Review Focus

- 助手及确认按钮不得成为模型目标。
- 异步表单验证后的真实写请求不能漏过冻结边界。
- 暂停/停止/切换身份期间返回的模型决定不能执行。
- 人工导航和控件变化不能让旧引用误点。
- 已提交但回执不确定时不得自动重试或宣称成功。

### Task 1: 当前页驱动和请求边界

Files: src/agent/pageDriver.mjs、requestGate.mjs 及声明；utils/request.ts；tests/current-page-agent.test.mjs。

- [x] 写并运行失败测试：实际同页导航、助手排除、过期引用和外链拒绝；写请求冻结前无发送、单次确认、停止拒绝。
- [x] 实现 createPageDriver({document,window,router,allowed}) 与请求边界 bindPageTask / interceptPageRequest / settlePageRequest。
- [x] 运行测试验证通过。

### Task 2: 单例引擎、六角色 UI 与文件

Files: src/agent/pageTaskEngine.mjs、currentPageAgent.ts；components/AssistantPanel.vue、ComputerAgentPanel.vue；runner 文件接口及测试。

- [x] 写并运行失败测试：模型返回 navigate 后同页变化；暂停/停止后丢弃决定；身份变更清除任务；提交冻结及确认回执；失败不可假成功。
- [x] 实现观察→规划→当前页动作→真实结果循环和可选文件能力；默认网页执行，关闭助手暂停。
- [x] 保留旧电脑 API 作为历史测试接口；当前 UI 仅连接新同页引擎。
- [x] 前端全量测试与构建，runner 全量测试。

### Task 3: 实际桌面验收与交付

- [x] 标记隔离环境运行六角色真实模型同标签页测试、当前页表单及0→1确认；保存新证据，不覆盖旧证据。
- [x] 独立代码审查，修复重要问题并回归。
- [x] 正常本地只读导航验收；更新 README、AGENTS、共享交接文档（保持私密§3字节），本地提交；不部署服务器。

## 完成记录

当前页/DOM回归16项，前端252及构建、执行器29、启动7通过。六角色真实模型同标签页、真实表单0→1且重复确认拒绝、CSV599字节生成并请求打开、正常本地只读导航通过。重要审查项关闭；后端/JAR和迁移原字节不变。隔离服务停止，仅本地交付。证据见 docs/qa/current-page-agent-20261016/verification.json。
