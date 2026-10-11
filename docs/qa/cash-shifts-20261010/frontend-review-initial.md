# Task 2 independent frontend review

## Spec Compliance

- ❌ One async-safety requirement is not met: an obsolete conflict-reload continuation can unlock a newer action's conflict protection. See Important F1 below.
- Reviewed task-only `frontend-review.diff`, binding `docs/superpowers/specs/2026-10-10-cash-shifts-design.md`, task brief, frontend report and build proof. No product, service, database, Git state or branch changes; no subagents and no full-suite rerun.
- ⚠️ Desktop browser flows, backend authorization/enforcement and deployed/activated artifact behavior were not independently executed in this review. The controller owns those acceptance boundaries. This frontend verdict does not replace backend review.

## Strengths

- API shapes and endpoints match the binding contract: `C:/Users/CJC/Desktop/fitness-feature-hh/fitness-feature-hh/fitness-frontend/src/api/modules/cashShifts.ts:19` and `:26`–`:35`.
- Role/permission intersection and local action authorization, including last-submitter self-review prohibition, are explicit: `C:/Users/CJC/Desktop/fitness-feature-hh/fitness-feature-hh/fitness-frontend/src/views/admin/CashShiftCenter.vue:36`–`:45`; router `:69`–`:73`, layout `:92`. One focused unchanged-code check verified both entry points consume `src/utils/accessControl.mjs` intersection logic.
- Blank actuals, zero explicitly entered, per-channel integer-cent discrepancy detection, immutable write payloads and unchanged retry keys are implemented in `CashShiftCenter.vue:70`–`:78`, `:113`, `:129`–`:140`. Returned correction uses the detail's original preview fingerprint, with safe text audit rendering at `:16` and `:148`–`:151`.
- Session/identity/role/venue/permission changes synchronously clear financial data and forms (`CashShiftCenter.vue:34`, `:59`–`:63`); read/write callbacks and their API forwarding prevent obsolete publication (`:83`–`:111`, `:141`–`:146`).
- Opt-in request validity defaults to true; ordinary APIs need no callback. Real Axios interceptor code checks validity before sending and before success/error notices, after asynchronous JSON Blob parsing, and after refresh success/failure: `C:/Users/CJC/Desktop/fitness-feature-hh/fitness-feature-hh/fitness-frontend/src/utils/request.ts:69`, `:122`, `:139`, `:153`, `:174`–`:176`, `:189`–`:194`. Cancellation avoids false network notices. One focused unchanged-code check of `src/store/modules/auth.ts:14`–`:23` confirms refresh with an expected generation does not advance the generation; genuine scope changes still invalidate the page.
- Runtime tests execute production SFC setup via `compileScript`, Vue reactivity and production API functions, with boundary transport substitutes; interceptor tests use a real Axios instance with adapter replacement. The amount/state implementation is not mirrored as an expected-value calculator (`tests/cash-shifts-runtime.test.mjs:9`–`:38`, `:53`–`:127`; `tests/async-safety.test.mjs:41`–`:91`).

## Issues

### Critical

None found.

### Important

**F1 — Obsolete conflict reload clears a newer operation's conflict flag.**

- Location: `C:/Users/CJC/Desktop/fitness-feature-hh/fitness-feature-hh/fitness-frontend/src/views/admin/CashShiftCenter.vue:125` (and equivalent list branch `:126`); reload routine starts at `:122`.
- Trigger: action A submits and receives 2600; user clicks “重新载入并核对”, whose detail request stays pending. The cancel button remains usable while loading (`:20`, `closeOperation` at `:120`). User cancels A, opens another action B, finishes the new detail load and submits B; B also receives 2600. A's old request then finishes.
- Observed result: `fetchDetail` correctly discards A's stale result, but the outer `reloadConflict` resumes and checks only the current session and nonempty current detail. It sees B's detail and executes `conflict=false`. The confirmation button and `submitOperation` become usable without B's explicit successful reload and fresh point-by-point verification. The same flaw exists when an old open-action list reload encounters a newer loaded scope/action.
- Impact: the frontend's required conflict recovery gate is bypassed. The backend retains its conflict/fingerprint checks, but a later write is emitted despite the frontend promising to stay disabled until the conflicting action has been reloaded.
- Fix: bind the entire reload intent to the same operation/action generation, detail generation, scope generation and target. After every await, only clear the conflict flag if that original intent is still current and its own successful result loaded. Add a focused runtime RED/GREEN regression for cancel/reopen + new conflict + old reload completion, covering success/failure and the list branch where appropriate.
- Reproduction: one focused Node stdin execution reused the existing production-SFC runtime helper, with only a deferred HTTP detail boundary. Assertions verified A conflict → pending reload → cancel → B conflict=true → obsolete A completion changes B conflict to false → a third POST is emitted. Exit code 0; no suite rerun or file/service/database mutation. This reproduction is additional evidence beyond the reported 129 tests, which do not cover this outer continuation path.

### Minor

None raised.

## Verification and evidence boundary

- Read `C:/Users/CJC/AppData/Local/Temp/fitchain-cash-frontend-20261010/all-tests-final.log`: 129 tests, 129 pass, 0 fail/cancel/skip. `verification-final.json` records testExit=0 and buildExit=0.
- Read `build-final.log`: Vite build succeeds; its large-chunk advisory is documented existing build noise, not a new task-specific failure.
- Independently checked the current five product-source SHA256 hashes against `docs/qa/cash-shifts-20261010/frontend-build.json`; all five match. Dist inventory is reported there but was not independently hash-checked in this review.
- Full test/build results are evidence read from the implementer's logs, not a claim that this reviewer reran them. Tests passing does not negate the focused F1 reproduction.
- Declined to judge: mobile layout, because scope explicitly limits acceptance to desktop; financial-provider settlement integration, because the spec explicitly models ledger registration and actual manual verification rather than provider settlement; deployment and real-data migration, because this task is frontend-only and local.

## Assessment

**Task quality: Needs fixes.**

The contract, amounts, role controls and guarded transport behavior are well grounded, but F1 violates the explicit conflict-reload and obsolete-async-result requirements. Re-review should focus on the reload continuation fix and its regression evidence.
