# M6-P7 Scope Budget

## Phase Character

M6-P7 is a post-P6 mini-phase, not a continuation of M6-P6 UI polish.
It starts from baseline `b93743f` on branch `m6-engineering-hardening`.

## Locked Goal

Implement Owner hard delete for any task in any status. The delete consumes
the task row and all task-scoped facts through a FK-correct application-level
cascade.

The corrected cascade order is locked by
`docs/internal/m6p7-pre-estimate.md`. It is derived from the complete FK graph
and supersedes the original prompt's 19-step listing.

## Adjudication Record

The user adjudicated in favor of Option A: hard delete any task of any status.
This was accepted against the auditor's repeated recommendation of Option C:
draft-only hard delete.

The R8 pre-estimate gate correction is recorded in
`docs/internal/m6p7-pre-estimate.md`. The full philosophical override and
R8 transparency block must be carried forward into
`docs/internal/m6p7-verification.md` when the phase closes.

## Allowed Surfaces

- New `DELETE /tasks/{taskId}` OpenAPI operation, OWNER role only.
- Backend task hard-delete service with explicit application-level cascade.
- Backend mapper delete/update methods only as needed for the corrected order.
- Backend tests for cascade correctness, authorization, 404 behavior, object
  cleanup key selection, and rollback-sensitive DB ordering.
- Frontend `OwnerTasksListPage.tsx` delete action with confirmation.
- Frontend generated OpenAPI types after the contract change.
- New M6-P7 docs under `docs/internal/`.
- One `humanpending.md` resolved entry only at final verification closure.

## Forbidden Surfaces

- Soft delete or `deleted_at` columns.
- V11 migration.
- DB-level `ON DELETE CASCADE` changes.
- Bulk delete.
- Per-creator authorization.
- Restore or undo flow.
- Cleanup of `audit_logs`.
- Modifying `ObjectStorageWriter.deleteObject`.
- Touching frontend pages outside `OwnerTasksListPage.tsx`.
- Retroactively editing prior phase docs, audit docs, or prior humanpending
  entries.

## Budget

Hard cap: 500 changed code lines.

Pre-estimate code budget:

| Cluster | Estimate |
|---------|----------|
| OpenAPI and generated frontend type shape | 30 |
| Backend service, mapper, controller, security, object cleanup | 225 |
| Backend tests | 185 |
| Frontend delete action | 50 |
| **Total** | **490** |

This budget is intentionally tight. If implementation cannot stay below 500
changed code lines, stop for user adjudication instead of trimming tests or
silently weakening cascade coverage.

## Stop Conditions

- Any cluster exceeds its estimate by 50%.
- Cumulative code diff exceeds 500 changed lines.
- Schema version sharing is discovered.
- A DB FK requires changing schema constraints or adding `ON DELETE CASCADE`.
- A V11 migration becomes necessary.
- S3 cleanup cannot be made best-effort without risking DB rollback.
- Backend full suite drops below the previous `390 tests, 0 failures,
  0 errors, 78 skipped` baseline, excluding new passing tests added by M6-P7.
- Any new technical surprise changes the hard-delete story, object-cleanup
  scope, authorization, or evidence semantics.

## Pre-Code Gate

No code commit may land until the user adjudicates
`docs/internal/m6p7-pre-estimate.md`.
