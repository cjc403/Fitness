# Task 1 backend review — 2026-10-10

## Scope and method
Read-only review of the supplied task-only snapshot diff against the original dirty source state, with HEAD b75b1ee and unrelated existing modifications excluded. Read task-1-brief.md, the binding design, implementer report, the diff (recovered only tool-truncated sections), and narrow integration dependencies. No Git mutation, suite rerun, product edit, service operation, or database access was performed.

Before external checks, identified three cross-cutting risks: shared venue lock coverage/order in sale, renewal and refund paths; actor locks versus PostgreSQL foreign-key KEY SHARE; and JWT headquarters switched-venue compatibility. CommercialService.createSale/renew and RefundService through EntitlementValueService use the existing venue mutex; cashshift takes actor → venue → shift and never reverses into member/order/entitlement. PostgreSQL actor and venue locks use FOR NO KEY UPDATE. JWT validation and CashShiftScope both exempt SUPER_ADMIN from account-home-venue equality, preserving switched tokens.

## Spec Compliance
**Substantially compliant; Needs fixes for the invalid-channel-element error contract.**

All six endpoints and specified response fields are present. Role and authority intersection, per-venue isolation, management-only independent review, ownership, version/fingerprint checks, actor-key isolation and replay-before-state/version are implemented. First baseline, later carryover, registration-ID allocation of backdated refunds, fixed closing snapshot, returned corrections, append-only before/after audit, independent receipts/refunds differences, required four channels plus normalized observed channels, Shanghai microsecond timestamps and server-owned boundaries are consistent with the binding requirements.

## Strengths
- Same-channel receipt/refund cancellation still requires an explanation; literal system net 160 / actual net 155 expectations are tested.
- Returned submissions retain original payments, fingerprint and closedAt while correcting actual values and appending audit history.
- Closed detail hydrates at most 100 snapshot payments; names use scalar lookups.
- Inspected log summaries confirm H2: 22 total, 0 failures/errors, 6 expected PG-only skips; PG: 22 total, 0 failures/errors/skips. Six PG tests observe actual Lock waiters before releasing independent transaction barriers, including real commercial sale and backdated refund HTTP flows.
- The isolated no-venue-lock mutant fails the expected waiter assertion. This is useful evidence that the concurrency checks detect removal of the integration mutex.

## Issues

### Critical
None found.

### Important — fix before completing Task 1
1. **[P2] Reject null elements in the submitted channel array.**
   - Source: fitness-backend/src/main/java/com/gym/fitness/module/cashshift/dto/CashShiftDtos.java:9 and fitness-backend/src/main/java/com/gym/fitness/module/cashshift/service/CashShiftService.java:52.
   - Trigger: an otherwise valid submit request contains `channels: [null]`, or the four valid required rows followed by null.
   - Evidence: the list itself is @NotNull and cascading @Valid does not reject null elements. The service unconditionally invokes c.channel(). The existing GlobalExceptionHandler maps the resulting NullPointerException to HTTP 500 / SYSTEM_ERROR.
   - Impact: malformed user input violates the specified 1001 parameter-error contract and produces a misleading server failure. Transaction rollback prevents partial closure; this is not a money-loss finding.
   - Fix: use a container-element @NotNull constraint (for example List<@NotNull @Valid ActualChannel>) or explicitly reject null before dereference. Add a focused MockMvc assertion for both a null-only list and null after valid rows, expecting code 1001 and an unchanged OPEN/version.

### Minor
1. **[P3] Pagination does not consistently bound database hydration.**
   - Source: fitness-backend/src/main/java/com/gym/fitness/module/cashshift/service/CashShiftService.java:58–64; fitness-backend/src/main/java/com/gym/fitness/module/cashshift/service/CashShiftLedger.java:18.
   - Trigger: a long-running OPEN shift with many payments, or a headquarters day spanning many stores/shifts.
   - Evidence: OPEN paymentPage fetches all candidates and then stream-skips/limits; workbench first loads every scoped day's shift and its channels, then performs the page query. Closed payment paging is correctly bounded.
   - Impact: response length remains bounded, but memory/query cost does not decrease with page size. This is a scaling concern rather than demonstrated failure at current local volume.
   - Recommendation: database pagination/count for the OPEN payment page; database aggregate projections for daily totals/counts/channels, and name lookup only for displayed records/current shift. Full fingerprint computation for OPEN detail may use streaming/projection if necessary; preserve its complete ledger coverage.

## Verification boundaries
No suites rerun: inspected existing summarized logs and test source as requested. The null-element outcome is a deterministic static control-flow finding, not a newly executed HTTP reproduction. No current-volume load benchmark was performed. Full package regression, V20-clone upgrade/data comparison, frontend acceptance and local activation remain the root's assigned verification, not claims of this review.

## Declined to judge
- Frontend rendering/session clearing: assigned to Task 2, outside this backend review.
- Deployment/runtime activation and complete application regression: explicitly retained by root.
- Existing unrelated dirty changes: excluded by the supplied review baseline.

## Assessment
**Needs fixes.** Core ledger, access control and locking behavior are well supported; repair the null-element validation gap with the focused regression before completing Task 1. The bounded-hydration improvement is non-blocking at this review's evidenced scale.
