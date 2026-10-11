# Backend task review — round 1

## Spec Compliance

- ❌ **Issues found / Needs fixes**: CREATE replay can return a plan outside the coach's current service venue; member feedback omits the current member ACTIVE/nonblacklist check. Both affect the required permission-before-idempotency boundary. Exact locations and regression cases are below.
- Scope: the 21 backend patches listed in `.superpowers/sdd/2026-10-11-training-plans/backend-review-121345/package.json`, against its captured dirty-worktree baseline at HEAD `b75b1ee`; no Git branch diff was recomputed. All listed patches were read once, in bounded groups. The task brief, implementation report, binding design and `docs/qa/training-plans-20261011/api-contract.json` were also checked. No current changed source file was separately reread.
- Controller clarification received during review: member feedback must reject inactive/blacklisted members before retry, including after waiting for the member lock. Supervisor archive is an explicit exception to member-state/old-coach-relationship restrictions, retaining current manager/HQ scope and reason requirements. Supervisor archive is therefore **not** flagged for permitting inactive members.
- ⚠️ V1–V21 byte preservation, historical 65-table preservation, whole-backend results, final local enablement, frontend and desktop behavior are outside this patch-only task review. The controller is independently validating them; this report does not claim those checks passed.

## Strengths

- `fitness-backend/src/main/resources/db/migration/V22__training_plans_and_sessions.sql:1`: the migration adds exactly the five requested training tables, with FK/range/state constraints, identity-consistent ACTIVE key, valid booking key and corresponding unique constraints (lines 11, 26, 39). It contains no legacy-table DML.
- `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:75`: existing-plan coach writes recheck the refreshed actor/plan relationship after the member lock; successful replay precedes version/state checking (lines 76, 78, 80, 89). Database uniqueness provides the second guard, and unique exceptions are not caught to continue a failed transaction.
- `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanAccess.java:25`: coach writes bind both plan coach and service venue, while manager/HQ archive is separated from editing/recording/voiding. Controller permission gates at `controller/TrainingPlanController.java:20`–33 correspond to this separation; the RolePermissionCatalog patch only adds the three new permissions.
- `fitness-backend/src/main/java/com/gym/fitness/module/workout/repository/TrainingPlanRepository.java:9`: workbench queries and statistics use the same scoped relationship predicate, member reads exclude DRAFT, and summary counts ignore the display keyword/status/page filters.
- `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:67`: history is paged, session actions are fetched in a batch, audits are capped at 100 with eventTotal, and member responses omit events. DTOs at `dto/TrainingPlanDtos.java:18`–28 omit phone/identity-document data and expose the specified contract.
- `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:80`: training validates dates and exact exercise membership, enforces at least one DONE action, refreshes and checks an ATTENDED booking, saves target/name/mode snapshots (line 86), and voiding preserves feedback/original booking while releasing its unique occupancy (line 89).
- `fitness-backend/src/main/java/com/gym/fitness/module/workout/dto/InputNumbers.java:12`: integer/long deserializers reject fractional tokens locally. Service validation at `service/TrainingPlanService.java:33`–46 and 80–83 implements the stated numeric, length, mode and calendar ranges without changing old-module parsing.
- `fitness-backend/src/test/java/com/gym/fitness/TrainingPlanPostgresConcurrencyTest.java:27`: PG tests guard the exact isolated JDBC URL, use Flyway plus Hibernate validate, and exercise actual independent transactions for publication, booking/session competition, same-key CREATE, feedback/void and lock-wait refresh. H2 tests additionally exercise snapshots, history totals, privacy, modes and physical V22 constraints.

## Issues

### Critical (Must Fix)

- None found in this task scope.

### Important (Should Fix)

1. **[P1] Reauthorize the original plan before returning a CREATE replay.** `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:74` returns `detail(plan(retry.getPlanId()), ...)` directly. Its preceding checks establish only that the coach is currently related to the member in the coach's current venue; they do not authorize the original event's plan. A coach can create a plan at venue A, transfer to venue B, regain a legitimate relationship with the same member at B, and resend the saved CREATE request/key. The replay then returns venue A's full detail and employee audit although ordinary GET correctly denies access via `TrainingPlanAccess.writable` (line 25). Load the original plan and apply current plan read/write scope authorization after the lock and before returning its detail. Add a regression where normal GET and the identical CREATE replay both return 1003 following that venue transfer, while same-scope replay still returns current original detail without additional rows/events.

2. **[P1] Enforce current member state on feedback before replay.** `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:90` uses the member-readable check before and after locking, but `TrainingPlanAccess.java:29` only checks ownership and non-DRAFT status for self reads. `TrainingPlanService.java:49` refreshes the member but tests only the actor account's ACTIVE status. Consequently an account that remains ACTIVE can submit or replay feedback for its own inactive/blacklisted member profile, including when the profile is disabled during the lock wait. Add a feedback-specific ACTIVE/nonblacklist guard before idempotency lookup, rechecking the refreshed member after acquisition; do not add that restriction to supervisor archive. Cover both a fresh feedback key and a prior successful key after disabling/blacklisting the member, asserting 1003 and unchanged session version/feedback/event count. A lock-wait variant should prove the post-wait state is used.

### Minor (Nice to Have)

- **[P3] Bounded but repeated per-plan queries.** `fitness-backend/src/main/java/com/gym/fitness/module/workout/service/TrainingPlanService.java:66` obtains member/coach/venue, action count, session count/latest session and coach relationship for each summary; `canArchive` may repeat the relationship lookup. Pagination bounds exposure but a 100-plan page still scales in query count. Consider batching summaries or reusing already-computed authorization when measured latency warrants it; this does not expand the current requirements or block approval by itself.
- **[P3] Test logs are green but not pristine.** The supplied `C:/Users/CJC/AppData/Local/Temp/fitchain-training-final-4379fc44e3e24b26a489bf89abae2549/green-pg.log:194`–195 contains HHH000444 follow-on-locking warnings corresponding to `TrainingPlanService.java:84`; lines 24–38 also include pre-existing SpyBean deprecation warnings and line 96 a JVM sharing warning. These are not test failures. Keep them disclosed and, in future lock-query cleanup, prefer a narrow explicit locking query that avoids the warning; do not suppress it or broaden this task into legacy test cleanup.

## Focused Checks and Evidence Boundary

- Reviewed supplied green log rather than rerunning tests: line 141 reports H2 14/14, line 198 PG 6/6, line 202 total 20 with zero failures/errors/skips, and line 205 BUILD SUCCESS. These are verified existing evidence, not new reviewer executions. No test suite, database connection, local 5433 action, cloud action, Git mutation or source edit was performed.
- Named outside-diff risk: verify `findByIdForUpdate` really locks the member and inspect lock-order interaction with existing booking writes. One focused search covered `fitness-backend/src/main/java/com/gym/fitness/module/member/repository/MemberRepository.java:14`–16 (PESSIMISTIC_WRITE) and `module/booking/service/BookingService.java:51`–56, 133–136, 267–281. The truncated `find` helper was read to complete that specific check: booking's helper locks schedule before loading booking, while the new recording path refreshes booking then schedule. This limited static check does not prove cross-module deadlock absence; no concrete failing interleaving was established, so no speculative blocker is added.
- The contract JSON is a brief-required artifact not included among the 21 backend source patches; it was read specifically to check route/DTO consistency. No broader repository crawl was done.

## Assessment

**Task quality: Needs fixes.**

The database guards, snapshots, narrow writes, scoped reads and existing concurrency evidence support the main design. The two authorization gaps above must be repaired and verified with targeted regressions before this task is approved.
