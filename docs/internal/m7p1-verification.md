# M7-P1 Verification

## 1. Status

M7-P1 closed on 2026-05-27.

- Baseline: `4cafa2e` (M6-P7 + login polish closure)
- Final code head: `c3d8206` (Cluster 5b)
- Final docs head: this commit
- Phase character: first sub-phase of M7 "approach perfection" track
- Target rubric item: sub-criterion 2.4, 完整的审计日志, scored 2/5 before M7-P1 and 5/5 after closure

M7-P1 adds the governance evidence layer that M6's highlight chains can reference directly: mutation surfaces now emit append-only audit evidence, and Owners can query/export that evidence from `/owner/audit-logs`.

## 2. Commit Map

| Commit | Cluster | Purpose |
|---|---|---|
| `b1c14ed` | docs | Scope-budget + pre-estimate with 4 gate adjudications |
| `68695d5` | C1 | OpenAPI query/export + controller/service stub |
| `27ad492` | C2 | V11 + AuditLogService + AuditActions + mapper extensions |
| `3ac6587` | C3 | 10 new audit write sites + legacy `task.transition` migration |
| `1d343ec` | C4 | Backend test coverage of all 11 audit writes + REQUIRES_NEW reflection test |
| `2a95007` | C5 | Frontend `/owner/audit-logs` page with filter + CSV export |
| `c3d8206` | C5b | Sidebar icon family unification + responsive filter bar |
| (this commit) | C6 | Verification doc + screenshots + humanpending |

## 3. R8 Transparency Records

### Record A: Pre-Estimate Gate Corrections

```
- schema.archive → DEFERRED (enum constant retained with // DEFERRED comment;
  no service method exists; archiving is a schema lifecycle feature not in
  M7-P1 scope)
- submission.supersede → DEFERRED (enum constant retained; superseded_by_id
  column exists in submissions table but no service path writes it;
  inventing one is out of P1 scope)
- ai_review.failed → INCLUDED with REQUIRES_NEW propagation (audit row
  must commit even when business transaction rolls back; auditor's
  fail-fast default applies to 9 of 10 new write sites, this is the
  intentional exception)
- export.snapshot_diff → DEFERRED (read path; turning reads into auditable
  events explodes audit_logs volume; compliance-level read auditing not in
  P1 scope)
```

### Record B: Cluster 1 Estimate Correction

```
Cluster 1 implementation triggered a stop. Auditor's pre-estimate of ~115
human-authored lines / 170 hard cap underestimated the OpenAPI surface
required for 14 query parameters across 2 operations + 3 response schemas.
Actual ~280 lines (YAML 176 + Java 104). Every line maps to locked design
(no parameter omission, no schema collapse). User adjudicated to accept
actual size; cap revised forward for M7-P1 only. No scope drift, no spec
degradation. Optional parameter extraction skipped (small savings,
generated-type risk).
```

### Record C: Cluster 2 Prompt Self-Contradiction

```
Agent caught a Cluster 2 prompt self-contradiction before any code change:
the prompt classified ai_review.failed as deferred while simultaneously
saying it lands in Cluster 3 with REQUIRES_NEW. Root cause: auditor
mistakenly conflated "requires special propagation handling" with
"deferred to future phase". User adjudicated a 3-category comment scheme:
no comment (10 implemented + 1 legacy = 11 constants), // REQUIRES_NEW
(1: ai_review.failed), // DEFERRED (3: schema.archive, submission.supersede,
export.snapshot_diff). This refined design is more precise than the
auditor's original 2-category proposal — grep // DEFERRED returns only
truly unimplemented constants; grep // REQUIRES_NEW returns only the
propagation exception.
```

### Record D: Cluster 3 Design Invariant

```
AuditLogServiceImpl.recordRequiresNew calls this.record(builder) as
internal self-invocation, which would normally trigger Spring AOP's
self-call trap (@Transactional bypassed). Why this is safe HERE: record()
intentionally has NO @Transactional annotation. The REQUIRES_NEW
propagation declared on the outer recordRequiresNew opens a new
transaction at the proxy boundary; the inner record() inherits that
new transaction (because record itself declares no propagation, default
behavior is to participate in caller's tx). Adding @Transactional to
record() in the future would silently break this contract by reactivating
the proxy self-call trap. Cluster 4 added a reflection-based test that
asserts record() has no @Transactional annotation and recordRequiresNew
has REQUIRES_NEW; this is a machine-executable invariant guarding the
design.
```

### Record E: Cluster 5b Visual Nit + Lesson

```
Cluster 5 (2a95007) shipped /owner/audit-logs after manual sanity at a
single wide viewport. User-side independent verification at narrower
viewports surfaced two visual issues: sidebar icon family inconsistency
(IconArticle vs the line-family used by other Owner items) and
filter bar horizontal overflow at 1100-1300px laptop widths (rigid
5-column grid with 750px minimum total width). Cluster 5b (c3d8206)
landed two minimal fixes: IconHistory swap for line-family consistency,
and flex-wrap + grid auto-fit replacement of the rigid grid. Three-
viewport manual sanity (1440 / 1280 / 1024) confirmed the fix.

Lesson learned: Cluster 5's single-viewport sanity was insufficient.
Frontend cluster verification checklist now requires manual sanity at
three viewport widths (1440 desktop, 1280 laptop, 1024 docked/tablet).
This addition will apply to M7-P2 onward and any future frontend
implementation cluster. Recording in this verification doc so the
process improvement is durable.
```

## 4. Coverage Matrix Actual vs Planned

| Action | Planned site | Actual file:line at `c3d8206` | Adjudication |
|---|---|---|---|
| `task.transition` (legacy migrated) | `TaskService.transition` | `TaskService.java:235` | Implemented |
| `task.delete` | `TaskService.deleteTask` | `TaskService.java:156` | Implemented |
| `schema.publish` | `SchemaService.publishVersion` | `SchemaService.java:177` | Implemented |
| `schema.archive` | none | n/a | Deferred (enum constant only) |
| `schema.version_create` | `SchemaService.publishVersion` | `SchemaService.java:158` | Implemented |
| `submission.create` | `SessionService.submit` | `SessionService.java:223` | Implemented |
| `submission.supersede` | none | n/a | Deferred (enum constant only) |
| `ai_review.field_assist` | `AiReviewService.review` | `AiReviewService.java:219` | Implemented |
| `ai_review.failed` | `AiReviewService.review` failure path | `AiReviewService.java:165` (`recordRequiresNew`) | Implemented with REQUIRES_NEW |
| `ai_review.recorded_failed_call` | `FailedAiCallRecorder.recordFailedAttempt` | `FailedAiCallRecorder.java:76` | Implemented |
| `review.approve` | `LedgerService.createEntry` | `LedgerService.java:100` | Implemented (conditional on verdict) |
| `review.reject` | `LedgerService.createEntry` | `LedgerService.java:102` | Implemented (conditional on verdict) |
| `export.snapshot_create` | `ExportService.createSnapshot` | `ExportService.java:116` | Implemented |
| `export.snapshot_diff` | none | n/a | Deferred (enum constant only) |

Counts: 11 implemented write sites (10 new + 1 legacy migrated), 3 deferred constants. This matches the pre-estimate gate adjudication.

## 5. AuditLogService Design Invariant

Default behavior is fail-fast: `AuditLogService.record(...)` writes in the caller's transaction and throws if insert does not affect exactly one row. This makes audit loss block the business mutation.

`AuditLogServiceImpl.record(...)` intentionally has no `@Transactional` annotation:

```java
@Override
public void record(AuditEventBuilder builder) {
    AuditEvent event = builder.build();
    String canonicalPayload = canonicalizer.canonicalJson(event.payload());
    // build AuditLogEntity, compute payload_hash, insert row
}
```

The single exception is `recordRequiresNew(...)`, used only by `ai_review.failed`:

```java
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordRequiresNew(AuditEventBuilder builder) {
    record(builder);
}
```

The internal `record(builder)` call is safe because `record()` has no transaction annotation of its own. The new transaction is opened at the `recordRequiresNew` proxy boundary; `record()` simply participates in that transaction.

Cluster 4 guards this as executable design evidence in `AuditLogServiceImplTest.recordRequiresNew_isTransactionalBoundaryAndDelegatesToRecord`: it asserts `record()` has no `@Transactional` and `recordRequiresNew()` has `Propagation.REQUIRES_NEW`.

## 6. V11 Migration Behavior

V11 adds one index only:

```sql
CREATE INDEX idx_audit_logs_action_time ON audit_logs (action, created_at);
```

Rationale: the Owner audit log page's primary query is "show me all `<action>` events in a time window." Existing indices cover resource-led and actor-led queries:

- `idx_audit_logs_resource_time(resource_type, resource_id, created_at)`
- `idx_audit_logs_actor_time(actor_type, actor_id, created_at)`

Neither supports action-led filters efficiently. The new `(action, created_at)` index covers the M7-P1 query/export path without changing table schema.

Migration count: 10 → 11.

## 7. OpenAPI Baseline Shift

- Pre-M7-P1 baseline at `4cafa2e`: `dc4a91c6471b3cbbf0bc0ba62139087e`
- Post-M7-P1 baseline from Cluster 1 forward: `b6a8344f2c7cc38db958eb333334ebd1`
- New operations:
  - `GET /audit-logs`
  - `GET /audit-logs/export.csv`
- New schemas/responses:
  - `AuditLog`
  - `PagedAuditLogs`
  - `ErrorPayloadTooLarge` response, reusing the existing `ApiError` schema body
- New tag: `AuditLogs`

Future phases should reference `b6a8344f2c7cc38db958eb333334ebd1` until the next contract change.

## 8. Backend Test Suite Result

- Pre-M7-P1 baseline at `4cafa2e`: 397 tests, 0 failures, 0 errors, 78 skipped
- After Cluster 4 (`1d343ec`): 408 tests (+11), 0 failures, 0 errors, 78 skipped
- M7-P1 final at this commit: 408 / 78 unchanged; Cluster 5 and 5b are frontend/docs only

D-口径: sandbox first run can hit local socket restrictions on MySQL/object storage access. Escalated rerun passed consistently, matching the prior M6 socket D-口径 pattern.

## 9. CSV Export Cap and 413 Semantics

CSV export has a hard cap of 50000 rows.

Implementation uses the sentinel pattern:

1. SQL query runs with `LIMIT 50001`.
2. Service checks returned row count.
3. If rows exceed 50000, `PayloadTooLargeException` is thrown.
4. `GlobalExceptionHandler` maps this to HTTP 413 with the `ErrorPayloadTooLarge` OpenAPI response body.

Frontend behavior:

- 413 shows `Toast.warning('导出超过 50000 行,请缩小筛选条件后重试')`.
- Empty result CSV shows `Toast.info('未匹配任何审计记录')`.
- Normal export uses raw `fetch()` rather than openapi-fetch because blob/text download requires direct `Response` handling.

D-口径: if real-world audit volume regularly exceeds 50000 rows for common filters, a future phase should introduce streaming export, S3-backed export jobs, or narrower default date windows.

## 10. Cluster Estimate vs Actual

| Cluster | Original Estimate | Revised Estimate | Actual | Stop Triggered |
|---|---:|---:|---:|---|
| C1 (OpenAPI + stubs) | 115 / 170 cap | accepted at ~280 | 280 (176 YAML + 104 Java) | Yes; user accepted estimate correction |
| C2 (foundation) | 200 / 250 cap | 400 / 500 | 469 | No |
| C3 (10 sites + migration) | 180 / 250 cap | 250 / 350 | 363 | No |
| C4 (tests) | 280 / 350 cap | unchanged | 346 | No |
| C5 (frontend) | 220 / 280 cap | unchanged | 326 | No |
| C5b (visual hot-fix) | 35-40 / 60 cap | n/a | 36 | No |

Code-only shortstat at Cluster 5b head: 39 files changed, 1874 insertions(+), 65 deletions(-), excluding docs and `humanpending.md`.

Drivers of the overrun:

- C1: OpenAPI query-param + schema surface was underestimated.
- C2: independent `AuditLogQueryMapper` design mirrored the M6-P7 `TaskDeletionMapper` isolation pattern; correct design, but initially unbudgeted.
- C3: payloads were complete governance evidence, not three-field stubs.
- C5b: user-side post-commit visual audit found a responsive failure mode.

Every line traces to an explicit cluster commit. No scope drift was hidden; estimate corrections were recorded at the point they surfaced.

## 11. M7-P1 Final State

- Backend writes `audit_logs` for 10 new mutation surfaces plus 1 legacy migrated write site.
- `AuditActions` contains 14 constants: 11 implemented, 1 REQUIRES_NEW-tagged, 3 deferred.
- `AuditLogService.record(...)` is fail-fast for 10 of 11 implemented write sites.
- `AuditLogService.recordRequiresNew(...)` records `ai_review.failed` across business rollback.
- V11 adds action-led audit query index only; no audit table schema changes.
- Owner `/owner/audit-logs` page supports action/resource/actor/date filters, table pagination, payload modal, and CSV export.
- Payload modal shows full JSON and canonical `payload_hash`.
- Sidebar nav is OWNER-only in the UI; API protection remains enforced with `@PreAuthorize("hasRole('OWNER')")`.

## 12. Visual Verification

After-screenshots are archived under `docs/screenshots/m7p1-after-set/`:

- `01-owner-audit-logs-list-after.png` — query page populated with audit rows and filter bar visible
- `02-owner-audit-logs-payload-modal-after.png` — payload modal open with full JSON and `payload_hash`
- `03-owner-audit-logs-csv-export-after.png` — export control visible; CSV response separately verified due in-app browser download limitation
- `04-owner-audit-logs-narrow-viewport-1024-after.png` — 1024px viewport evidence from Cluster 5b responsive check

The 1024px screenshot records the new frontend verification standard: future frontend clusters should check 1440, 1280, and 1024 viewport widths before sign-off.
