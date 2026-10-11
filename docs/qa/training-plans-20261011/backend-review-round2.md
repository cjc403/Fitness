# Backend task review — round 2, scoped P1 fixes

## Spec Compliance

- ✅ **Spec compliant for the two P1 fixes.** Both round-1 blockers are closed. The remainder of Task 1 retains the round-1 review baseline; this review does not reopen unrelated scope.
- Reviewed each of the three supplied deltas in `.superpowers/sdd/2026-10-11-training-plans/backend-fix-review/` once and read `task-1-fix-report.md`. No full source/diff reread, repository crawl, database/service connection, test execution, Git mutation or source edit was performed.

## Strengths and Fix Verification

- **CREATE replay scope — fixed.** `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:77` now loads and refreshes the original event's plan and calls `access.writable(a, original)` before returning its detail. This occurs after the member lock and actor refresh, and checks the original plan's current coach/venue/relationship authorization. It keeps ordinary same-scope replay independent of advanced plan status/version. `fitness-backend/src/test/java/com/gym/fitness/TrainingPlanIntegrationTest.java:34` verifies same-scope replay, coach transfer to a different venue with a still-valid relationship to the same member, denial of both ordinary GET and CREATE replay, and unchanged plan/event counts.
- **Feedback member state — fixed.** `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:50` returns the actually refreshed member from `lockMember`; lines 51–52 implement the specific ACTIVE/nonblacklist check. Line 93 applies it before locking and again to that returned member after waiting, before the idempotency lookup. Thus both fresh feedback and prior successful-key replay are denied after member deactivation/blacklisting.
- **Lock-wait regression — substantive coverage.** `fitness-backend/src/test/java/com/gym/fitness/TrainingPlanPostgresConcurrencyTest.java:97` executes all four combinations of inactive/blacklisted state and fresh/replayed feedback. Its helper acquires a separate JDBC member lock, observes the request waiting in PostgreSQL, changes state before releasing the lock, then asserts 1003 and unchanged session version, rating, content and event count (lines 100–117).
- **Read/archive exceptions retained.** The feedback guard is not installed in generic self-read or supervisor archive authorization. `fitness-backend/src/test/java/com/gym/fitness/TrainingPlanIntegrationTest.java:49` checks preserved member history and feedback, denied cross-venue manager archive and successful same-venue archive after disabling/blacklisting and removing the coach relationship. `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:74` additionally derives `canFeedback` from the current profile state, while leaving history visible. No DTO or schema changes are present in the reviewed source deltas.

## Issues

- **Critical:** none.
- **Important:** none remaining in this scoped re-review.
- **Minor:** round-1 bounded per-plan queries and warning-noise observations remain unchanged and nonblocking. The supplied new GREEN log still discloses HHH000444 at lines 203–204; no claim of warning-free output is made.

## Existing Execution Evidence Checked

- `C:/Users/CJC/AppData/Local/Temp/fitchain-training-p1-red-75b42448d7224e429983d6097c23f822/red.log:917`: four tests, four failures, zero errors/skips; BUILD FAILURE at line 920. The displayed H2 failures expected denial 1003 but received success 0, consistent with the original defects.
- `C:/Users/CJC/AppData/Local/Temp/fitchain-training-p1-green-f29323d525f347819674e9a1fbf68274/green-pg.log:150`: H2 17/17; line 211: PG 7/7; line 215: total 24, zero failures/errors/skips; line 218: BUILD SUCCESS. These are read-only checks of implementer execution evidence, not reviewer reruns.
- The controller's fresh whole-backend build is separate validation and was still running when this review was dispatched; this report does not substitute an earlier build result for that run.

## Assessment

**Task quality: Approved.** The two P1 authorization gaps are repaired at the correct lock/replay boundaries, with regressions for cross-venue replay and all four feedback lock-wait combinations. Round 1 plus this scoped review has no remaining Important/Critical backend task blocker.
