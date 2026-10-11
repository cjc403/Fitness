# Task 1 backend fix round 1 re-review — 2026-10-10

## Assessment

**Approved for the scoped backend fix delta.** The prior Important null-element finding and the accepted Minor bounded-hydration finding are closed. No actionable new Critical, Important, or Minor issue was found in this delta.

## Scope and method

Read the prior `backend-review.md`, implementer claims in `docs/qa/cash-shifts-20261010/backend-fix-round1.md`, the supplied `backend-fix-round1.diff`, the binding design, the four changed Java source/test files, and narrow controller/validation/repository dependencies. Reviewed introduced behavior and closure of the two named findings only; did not repeat the unchanged locking/security review.

No suites were rerun. Only safe final test-summary lines were read from the existing private H2 and PostgreSQL logs; no raw request bodies or JWTs were copied. No product, Git, runtime, database, real 5433, or 8189 mutation was performed. The only written file is this ignored report.

## Prior issue closure

### Important: null submitted channel element — Closed

- Source: `fitness-backend/src/main/java/com/gym/fitness/module/cashshift/dto/CashShiftDtos.java:9` now declares `@NotNull List<@NotNull @Valid ActualChannel>`, retaining list presence, per-element presence, and nested row validation.
- Trigger checked: `channels=[null]`, and the four valid required rows followed by null.
- HTTP path: `CashShiftController.java:16` validates the request before calling the service; `GlobalExceptionHandler.java:52` maps request-body validation errors to HTTP 400 and parameter code 1001. This prevents the former service dereference and transaction entry for these inputs.
- Regression evidence: `fitness-backend/src/test/java/com/gym/fitness/CashShiftIntegrationTest.java:228–235` covers both triggers, asserts code 1001, and verifies OPEN, version 0, and only the original OPEN audit event. The test permits HTTP 200 or 400 while enforcing the specified code; the inspected validation handler establishes the current 400 mapping.
- Impact addressed: malformed input no longer reaches the former 500 path or closes/increments the shift.

### Minor: pagination and daily aggregation hydration — Closed

- OPEN display source: `CashShiftLedger.java:17–25` shares the exact same scoped candidate predicate between count, complete detail query, and paginated query. It retains order venue, successful PAYMENT/REFUND types, `baseline < id <= cutoff`, ascending ID, and carryover normalization. `setFirstResult`/`setMaxResults` bound page entity hydration; out-of-range pages retain count. `CashShiftService.java:58` routes only the OPEN payment display endpoint to this page query.
- Workbench source: `CashShiftService.java:64–77` loads the requested shift page plus independently selected currentShift and looks up names only for those displayed rows. `:79–102` applies the same business-date and authorized venue arguments to scalar daily summary and grouped channel totals. Page/status filters do not change the daily denominator. OPEN contributes to counts but not closed money totals; approvedNet includes APPROVED only. Required four channels remain present even for an empty day, with observed additional channels merged.
- Regression evidence: `CashShiftIntegrationTest.java:205–220` checks whole-day totals after return/resubmit/approval, pagination and status independence, OPEN live amount exclusion, currentShift, selected-venue isolation, and Hibernate load bounds (at most page/current shifts, zero channel entities). `:222–226` checks 151 candidates, seven displayed PaymentTransaction entities, literal page boundary IDs, and total 151.

## Complete ledger and close invariants

`CashShiftService.java:43` still calls full `ledger.candidates` for OPEN detail and computes count, channel totals, and fingerprint before limiting the returned payment display to 100. `:51–54` still re-reads all candidates under the existing write path, checks the full fingerprint, and snapshots every candidate at close. The new pagination path is not used by either operation. The extracted query/snapshot helpers preserve the original predicate and fields.

`CashShiftIntegrationTest.java:226` verifies detail count/receipts across all 151 rows, rejection with code 2600 when a 152nd payment is added after preview, and successful refreshed close with paymentTotal 152 and actual receipts 153. Existing frozen/returned handling is unchanged by this delta.

## Existing verification evidence and limits

Inspected summary lines from TEMP `round1-h2-final.log`: 26 total, 0 failures, 0 errors, 6 expected PostgreSQL-only skips, BUILD SUCCESS. TEMP `round1-pg-final.log`: 26 total, 0 failures, 0 errors, 0 skips, BUILD SUCCESS. These are existing execution evidence, not a new reviewer run.

No throughput benchmark or independent new execution is claimed. Full package regression, migration/data comparison, frontend acceptance, QA, and local activation remain root-owned. Approval here covers this backend fix delta only.
