# M6-P4b Scope Budget

## Theme

Trusted Export Inline Cleanup.

M6-P4b implements the export side of M6-P4 final裁决: object-storage keys written during a failed synchronous export are cleaned up inline before rethrowing the existing export failure. M6-P4b intentionally does **not** persist failed export jobs.

## Final裁决 Applied

| Decision | Implementation boundary |
|----------|-------------------------|
| Q9 = B | Inline cleanup of exact object keys written during a failed sync export. |
| Q10 = A | Keep export failure visibility as exception-only; no failed export-job persistence. |

## Scope

| File / area | Change type | Strict-constraint class | Estimate |
|-------------|-------------|-------------------------|----------|
| Object storage writer/client | Add delete method for exact object keys | Additive | ~20-30 lines |
| `ExportService.createSnapshot` | Track keys written in the current attempt and cleanup on failure | Existing behavior exception | ~35-50 lines |
| Tests | Partial export write failure, exact-key cleanup, original failure preserved | Test-only | ~120-180 lines |
| Docs | Verification + false-symmetry follow-up | Documentation | ~60-100 lines |

Expected functional budget: **70-100 lines**.

## Required Semantics

### Inline Cleanup

Only delete object keys written by the current failed export attempt. Do not delete broad prefixes and do not scan buckets.

### Preserve Original Failure

Cleanup is best-effort. If cleanup also fails, the original `ExportFailureException` remains the primary failure reported to the caller; cleanup failure may be logged or suppressed but must not mask the original cause.

### No Failed Export Persistence

M6-P4b does not add failed `export_jobs` rows, failed `export_snapshots`, migrations, OpenAPI fields, or UI. That work is deferred until async export/job化 provides a real API/UI consumer for failed-job state.

## Strict-Constraint Exceptions

| Exception | Location | Reason | R10 path |
|-----------|----------|--------|----------|
| Object-storage inline cleanup | `ObjectStorageWriter` + `ExportService.createSnapshot` | MinIO/S3 writes do not roll back with SQL; exact written keys should be cleaned when sync snapshot fails | Dedicated cleanup commit + tests; revert restores documented orphan-object behavior |

## Test Budget

Required P4b regression coverage:

- Export failure after one or more object writes deletes exactly those written keys.
- Cleanup does not delete unrelated objects with similar prefixes.
- Cleanup failure preserves the original export failure.
- Existing successful export snapshot behavior remains unchanged.
- No OpenAPI/migration/backend status persistence is introduced for failed export jobs.

## Stop Conditions

Stop and rescope before implementation if:

- Cleanup requires background scanning or startup reconciliation.
- Exact written key tracking is unavailable without broad export service redesign.
- Failed export-job persistence becomes necessary to test cleanup.
- Functional code estimate exceeds 120 lines.

## Pre-Implementation Research Findings (M6-P4b Segment 1)

- `ExportService.createSnapshot` catches `RuntimeException` at lines 98-99 and wraps it as `ExportFailureException`. The method is `@Transactional`, so SQL rows roll back while already-written S3/MinIO objects remain outside the SQL transaction.
- `ObjectStorageWriter` currently exposes `putObject(String objectKey, byte[] content)` only; no delete method exists yet.
- Object keys are deterministic and local to the current export attempt via `objectKeyPrefix = "exports/tasks/" + taskId + "/jobs/" + job.getId() + "/"`.
- Current writes are exact-key calls: one `putObject` per artifact file plus `manifest.json`.
- Cleanup strategy: track exact keys in a local list only after a successful `putObject`, delete those keys in the catch block, log cleanup failures without replacing the original export failure, and keep failed export-job persistence out of scope.
