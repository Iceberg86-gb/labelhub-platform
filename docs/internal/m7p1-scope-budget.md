# M7-P1 Scope Budget

## Phase Character

M7-P1 is the first sub-phase of the M7 "approach perfection" track. It
targets rubric sub-criterion 2.4, complete audit logs, which the M7
self-assessment marked as a material gap after M6-P7.

Baseline anchor:

- HEAD: `4cafa2e`
- Backend tests: `397 tests, 0 failures, 0 errors, 78 skipped`
- OpenAPI MD5: `dc4a91c6471b3cbbf0bc0ba62139087e`
- Migration count: `10`
- `humanpending.md` bracketed entries: `130`

M7-P1 does not modify the M6 immutability decisions. Submission facts remain
immutable inside alive tasks, Quality Ledger entries remain append-only, and
M6-P7 whole-task hard delete remains the only explicit override for deleting a
task and its facts. Audit log entries are append-only governance evidence and
align with the M6 ledger philosophy.

## Pre-Estimate Gate Correction

The original M7-P1 prompt says "12 new action types" but enumerates 13 new
action strings:

1. `schema.publish`
2. `schema.archive`
3. `schema.version_create`
4. `submission.create`
5. `submission.supersede`
6. `ai_review.field_assist`
7. `ai_review.failed`
8. `ai_review.recorded_failed_call`
9. `review.approve`
10. `review.reject`
11. `export.snapshot_create`
12. `export.snapshot_diff`
13. `task.delete`

Plus existing `task.transition`, the action registry would contain 14
constants, not 13. This scope-budget keeps the explicit action list as the
source of truth and records the count mismatch for user adjudication.

Baseline grep also found that not every requested action currently has a
business mutation surface:

- `schema.archive`: no archive service or endpoint exists.
- `submission.supersede`: `submissions.superseded_by_id` exists, but no service
  writes it.
- `ai_review.field_assist`: the OpenAPI/controller endpoint exists, but
  `AiReviewController.createFieldAssistCall` returns `501 Not Implemented`.
  Current successful AI review field findings can still be audited under this
  action if the user accepts that mapping.
- `export.snapshot_diff`: the diff endpoint is a read path, not a mutation, and
  the service method is not transactional.

No code cluster may start until the user adjudicates whether these items are
deferred or whether M7-P1 expands to create the missing business surfaces.

## Locked Goal

Implement a unified audit logging substrate and expand coverage across the M6
mutation surfaces that already exist, without inventing missing lifecycle
features inside this phase.

The proposed base goal is:

- Migrate existing `task.transition` from raw `AuditLogMapper.insert` to the new
  `AuditLogService.record(...)`.
- Add audit events for the directly insertable existing surfaces:
  `schema.publish`, `schema.version_create`, `submission.create`,
  `ai_review.field_assist` for successful AI field findings,
  `ai_review.recorded_failed_call`, `review.approve`, `review.reject`,
  `export.snapshot_create`, and `task.delete`.
- Hold `schema.archive`, `submission.supersede`, `ai_review.failed`, and
  `export.snapshot_diff` at the pre-estimate gate until the user adjudicates
  their semantics.
- Add Owner-only audit log query and CSV export APIs.
- Add Owner UI route `/owner/audit-logs` with filters, table preview, payload
  expansion, pagination, and CSV export.
- Add V11 index-only migration for action-led audit queries.

If the user insists that all four gated items land in M7-P1, the phase needs a
revised estimate and likely a broader design pass, because it would no longer
be an audit-only coverage expansion.

## Allowed Surfaces

- New backend `AuditLogService.record(...)` path and `AuditActions` constants.
- Extension of `AuditLogMapper` with append-only query/export methods.
- Migration of `TaskService.transition` audit writing to the unified service.
- Audit writes at adjudicated service mutation sites.
- New OpenAPI operations:
  - `GET /audit-logs`
  - `GET /audit-logs/export.csv`
- Frontend generated OpenAPI types.
- New Owner page `/owner/audit-logs`.
- New frontend feature folder under `apps/web/src/features/audit/`.
- `AppLayout`/role-route sidebar update for an Owner-only "审计日志" entry.
- V11 index-only migration on `audit_logs(action, created_at)`.
- Tests under audit-related backend module paths and touched service tests.
- Final M7-P1 verification docs, screenshot index, and one `[M7-P1 resolved]`
  humanpending entry at closure only.

## Forbidden Surfaces

- Adding columns to existing tables.
- Changing M6 immutability or append-only ledger semantics.
- Changing Quality Ledger, Submission, or M6-P7 hard-delete contracts except
  adding audit writes at adjudicated points.
- Adding the missing archive/supersede/field-assist implementation unless the
  user explicitly expands scope after reviewing this gate.
- Adding new authorization mechanisms beyond existing OWNER-only
  `@PreAuthorize` patterns.
- Touching unrelated frontend pages.
- Retrofitting prior M6 docs or prior `humanpending.md` entries.
- Treating audit writes as best-effort for successful business mutations.

## Failure Semantics

M7-P1 proposes fail-fast audit writes for successful business mutations:
`AuditLogService.record(...)` failure rolls back the business transaction.

Reason: audit logs are governance evidence. Silent audit loss is worse than
rejecting the mutation. This intentionally differs from M6-P7 object-storage
cleanup, where S3 cleanup is best-effort because it is residue cleanup after
the authoritative DB mutation.

Failure evidence is a special case. Existing AI failed attempts use
`FailedAiCallRecorder.recordFailedAttempt(...)` with `REQUIRES_NEW` so failure
evidence survives the outer AI review rollback. M7-P1 must preserve that
semantics for `ai_review.recorded_failed_call`.

## Budget

Base implementation budget after the pre-estimate correction:

| Cluster | Scope | Estimate |
|---|---|---:|
| C1 | OpenAPI query/export operations + schemas + generated frontend types | 90 |
| C2 | V11 index migration + AuditActions + AuditLogService + AuditLogMapper query/export methods | 230 |
| C3 | Audit writes for adjudicated existing surfaces + migrate `task.transition` | 190 |
| C4 | Backend tests for service, writes, filters, CSV cap, legacy migration | 300 |
| C5 | Frontend Owner audit page + feature hooks + sidebar nav | 230 |
| C6 | Final docs + screenshots + humanpending | N/A |
| **Total** | Base code estimate | **1040** |

Hard cap: `1100` changed code lines for the base implementation.

If any gated item expands into new business functionality, stop and revise the
budget before code lands.

## Stop Conditions

- The user does not adjudicate the four gated action semantics.
- Any cluster exceeds its estimate by 50%.
- Cumulative code diff exceeds `1100` changed lines.
- Any audit write site requires restructuring a business method beyond
  constructor injection, payload construction, or a local try/catch for failure
  evidence.
- Backend suite drops below `397 / 78`, excluding new passing tests.
- Existing `audit_logs` rows are incompatible with the new query/export DTOs.
- V11 migration conflicts with an existing migration.
- OWNER-only authorization cannot use existing `@PreAuthorize` patterns.
- Any inline audit action string appears outside `AuditActions`.

## Pre-Code Gate

No OpenAPI, backend, frontend, migration, generated type, test, or humanpending
change may land until the user adjudicates `docs/internal/m7p1-pre-estimate.md`.
