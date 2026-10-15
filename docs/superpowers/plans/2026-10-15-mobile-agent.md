# Mobile and Role Agent Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for orchestration; independent frontend and model protocol components use superpowers:dispatching-parallel-agents. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Deliver mobile six-role access and a role-scoped agent with genuine query and confirmed business execution.
**Architecture:** Same Vue app and Spring services. A role-filtered tool registry feeds a bounded model loop; durable server-owned previews bind confirmations to the login session.
**Tech Stack:** Existing Vue3/TS, Java17/Spring Boot3.5, JPA/Flyway, DeepSeek-compatible tool calls.
**Spec:** `docs/superpowers/specs/2026-10-15-mobile-agent-design.md`

## Global Constraints

Local only; no deployment/push. Preserve V1–V22 bytes and existing business data. Test backend in fresh TEMP source copy. User approved implementation and recommended decisions; no extra approval loop. Default current checkout retained because prior direction files are already shared here; strict file ownership prevents concurrent edits. No new product dependencies unless necessary.

## Review Focus

- Session change invalidates pending confirmation, even for the same account.
- Model-provided role/venue/confirmation text never grants authority.
- Business exception must roll back writes before FAILED audit is recorded.
- Mobile navigation never exposes unauthorized routes; keyboard/dialog must fit.
- PWA must not persist private API responses, tokens, or personalized HTML.

## Task 1: Model tool-call transport

Files: `assistant/service/DeepSeekClient.java`, new `assistant/service/AgentModelClient.java`, new focused tests. Public adapter: `Optional<Completion> complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools)`; Completion(content,calls,model,totalTokens), Call(id,name,arguments). Keep old client API intact, use existing properties/rate protection/sanitization. No business tools here.
- [x] Write transport parsing/limit/fallback tests, observe failure.
- [x] Implement bounded tool response handling and safe failure metadata.
- [x] Run focused tests from TEMP; communicate exact interface and evidence.

## Task 2: Mobile shell/PWA/Agent interface

Files: frontend only: AdminLayout, mobile components/styles, AssistantPanel, api/modules/assistant.ts, public manifest/worker/icons, main entry, tests. Implement spec API without changing backend; strict role navigation uses current accessControl. Agent UI shows structured query results, tool forms, immutable preview cards, explicit confirm/cancel, audit receipts, epoch guards and network failures.
- [x] Write role mobile-navigation, offline/cache, agent request/confirmation tests and observe failure.
- [x] Implement mobile six-role navigation and responsive core forms preserving desktop.
- [x] Implement PWA shell and mobile Agent interface.
- [x] Run frontend tests/typecheck/build and inspect narrow/wide browser.

## Task 3: Scoped tools and audited actions

Files: new module/agent DTO/controller/service/entity/repository, V23 migration, tests. ToolRegistry exposes tools/query/prepare/execute, delegates existing scoped services; action service handles transactional at-most-once execution and audit. Agent service uses Task1 and enforces bounded rounds, shared API in spec consumed by Task2.
- [x] Write integration tests for role tools and preview/confirm safety, observe failure.
- [x] Implement safe read DTOs and three write flows, migration and session-bound actions.
- [x] Implement chat model loop, deterministic local tool fallback, real service status.
- [x] Verify targeted H2 and isolated PG concurrency/migration, then full backend suite.

## Task 4: Integration, review and local delivery

- [x] Verify model compatibility using no-write test if configured; otherwise disclose unavailable live verification.
- [x] Test six-role mobile/desktop navigation and real isolated flows; no real business test writes.
- [x] Run focused security/implementation review; fix material findings and repeat justified checks.
- [x] Back up/activate tested local artifact, verify old data and migration bytes, update shared handoff/AGENTS/current features and evidence.

## Ledger

- 2026-10-15: User approved both features and sequential development. Prior direction files already modified, retained. Main source base2918d94. No server access. File ownership separated between frontend, model transport and backend orchestration. Functional and browser evidence pending.

- 2026-10-15 delivery: backend297/0 failures/13 skips (Agent PG11 executed), frontend201 and build pass. Six-role real isolated browser flows pass. Local PG upgraded23 after backup; old counts/password hashes unchanged. Independent review findings closed with regression tests. Real model probe401, honest local tools mode; physical phone/HTTPS installation not verified. See docs/手机端与六角色Agent_20261015.md and docs/qa/mobile-agent-20261015/verification.json. No server, push or commit.
