# M7-P1 Pre-Estimate

## Status

Pre-estimate gate only. No code lands until user adjudication.

Baseline anchor:

- HEAD: `4cafa2e`
- Backend tests: `397 / 78`
- OpenAPI MD5: `dc4a91c6471b3cbbf0bc0ba62139087e`
- Migration count: `10`
- `humanpending.md` bracketed entries: `130`

## Baseline Audit Surface

Existing audit infrastructure:

- `audit_logs` table exists in
  `services/api/src/main/resources/db/migration/V202611220900__init_schema.sql:306-317`.
- `payload_hash` exists via
  `services/api/src/main/resources/db/migration/V202611231600__add_audit_log_payload_hash.sql:1-2`.
- Existing indexes:
  - `idx_audit_logs_resource_time(resource_type, resource_id, created_at)`
  - `idx_audit_logs_actor_time(actor_type, actor_id, created_at)`
- `AuditLogMapper.insert(...)` and `selectByResource(...)` exist at
  `services/api/src/main/java/com/labelhub/api/module/admin/mapper/AuditLogMapper.java:14-27`.
- The only current write site is `TaskService.transition(...)` at
  `services/api/src/main/java/com/labelhub/api/module/task/service/TaskService.java:198-218`,
  using inline action string `"task.transition"` at `TaskService.java:351-364`.

## R8 Gate Correction

The prompt text says "12 new action types", but the explicit target list
contains 13 new action strings plus legacy `task.transition`. M7-P1 therefore
needs an `AuditActions` registry with 14 constants if the explicit list is kept.

The same grep pass found four action semantics that are not directly
insertable in the current codebase:

1. `schema.archive`: no archive method or endpoint exists in `SchemaService`.
2. `submission.supersede`: the DB column exists, but no service writes
   `superseded_by_id`.
3. `ai_review.field_assist`: the OpenAPI/controller method exists, but
   `AiReviewController.createFieldAssistCall(...)` returns 501 at
   `services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java:54-59`.
   Current successful AI review field findings can be audited under this action
   only if the user accepts that naming.
4. `export.snapshot_diff`: `ExportService.diffSnapshotsForOwner(...)` at
   `services/api/src/main/java/com/labelhub/api/module/export/service/ExportService.java:134-155`
   is a read path and is not transactional. Auditing it turns a read into a
   write and needs explicit user adjudication.

This document records the gate rather than pretending all 13 new actions are
already insertable.

## Coverage Matrix

Legend:

- `Ready`: audit write can be inserted with constructor injection and payload
  construction only.
- `Ready with naming adjudication`: code path exists, but action naming needs
  user confirmation.
- `Deferred`: requested action has no business surface in the current product.
- `Adjudication required`: implementing audit changes behavior or transaction
  semantics.

### Category 1: Schema Lifecycle

- `schema.publish` | `SchemaService.publishVersion` |
  `services/api/src/main/java/com/labelhub/api/module/schema/service/SchemaService.java:107-145` |
  Ready. Insert after `labelSchemaMapper.updateById(parent)` and optional task
  current-version sync. Actor is `ownerId`; resource is `schema`; payload can
  include `schemaId`, `versionId`, `versionNumber`, `contentHash`, and `taskId`.
- `schema.version_create` | `SchemaService.publishVersion` |
  `SchemaService.java:107-145` | Ready. Same method creates the version at
  `SchemaService.java:124-134`. If both `schema.publish` and
  `schema.version_create` are kept, implementation writes two audit rows from
  the same method.
- `schema.archive` | not implemented | Deferred. No archive method exists in
  `SchemaService` or `SchemasController`; adding it would be a schema lifecycle
  feature, not audit-only coverage.

### Category 2: Submission Lifecycle

- `submission.create` | `SessionService.submit` |
  `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:172-201` |
  Ready. Insert after `submissionMapper.insert(submission)` and session status
  update. Actor is `labelerId`; resource is `submission`; payload can include
  `sessionId`, `taskId`, `datasetItemId`, `schemaVersionId`, `contentHash`.
- `submission.supersede` | not implemented | Deferred. `submissions.superseded_by_id`
  is mapped in `SubmissionEntity`, but no service writes it; M7-P1 should not
  invent return/resubmit semantics without a separate decision.

### Category 3: AI Review

- `ai_review.field_assist` | `AiReviewService.review` |
  `services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:99-170` |
  Ready with naming adjudication. Current product writes AI field findings in
  the normal review flow at `AiReviewService.java:159-168`; the explicit
  `FieldAssist` endpoint is not implemented. If accepted, write one audit row
  when `fieldFindings` is non-empty.
- `ai_review.failed` | `AiReviewService.review` failure path |
  `AiReviewService.java:130` and `AiReviewService.java:200-227` |
  Adjudication required. A same-transaction audit write would rollback when
  the AI review fails. Persisted failure evidence currently lives in
  `FailedAiCallRecorder.recordFailedAttempt(...)` using `REQUIRES_NEW`. User
  must decide whether `ai_review.failed` is a final failure audit row using
  `REQUIRES_NEW`, or whether `ai_review.recorded_failed_call` is sufficient.
- `ai_review.recorded_failed_call` | `FailedAiCallRecorder.recordFailedAttempt` |
  `services/api/src/main/java/com/labelhub/api/module/ai/service/FailedAiCallRecorder.java:29-65` |
  Ready. Insert after failed `ai_calls` row insert. Actor is `ai` or system;
  resource is `submission`; payload can include attempt number, prompt version,
  provider, model, retryable flag, provider code, and failed `aiCallId`.

### Category 4: Review Action

- `review.approve` | `LedgerService.createEntry` |
  `services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java:50-82` |
  Ready. The method validates `payload.verdict` at `LedgerService.java:157-164`.
  If verdict is `approve`, write `review.approve` after ledger insert.
- `review.reject` | `LedgerService.createEntry` |
  `LedgerService.java:50-82` | Ready. If verdict is `reject`, write
  `review.reject` after ledger insert. Actor is `reviewerUserId`; resource is
  `submission`; payload can include `taskId`, `ledgerEntryId`, and verdict.

### Category 5: Export

- `export.snapshot_create` | `ExportService.createSnapshot` |
  `services/api/src/main/java/com/labelhub/api/module/export/service/ExportService.java:54-107` |
  Ready. Insert after `exportSnapshotMapper.insert(snapshot)` at
  `ExportService.java:101`. Actor is `ownerUserId`; resource is
  `export_snapshot`; payload can include `taskId`, `exportJobId`, `fileHash`,
  `manifestHash`, `sourceStateHash`, and `objectKey`.
- `export.snapshot_diff` | `ExportService.diffSnapshotsForOwner` |
  `ExportService.java:134-155` | Adjudication required. This is a read endpoint
  today. Auditing it means turning a read into a write and adding transactional
  semantics or letting `AuditLogService.record(...)` run in its own transaction.

### Category 6: Hard Delete

- `task.delete` | `TaskService.deleteTask` |
  `services/api/src/main/java/com/labelhub/api/module/task/service/TaskService.java:132-188` |
  Ready with one timing decision. Audit row must be written before step 20
  deletes `task_transitions` and before step 21 deletes `tasks`; payload should
  include the task identity and cascade/object key counts collected before
  deletion. Because hard delete consumes the task row, the audit row is global
  evidence and intentionally not FK-linked.

### Legacy Migration

- `task.transition` | `TaskService.transition` |
  `TaskService.java:198-218` | Ready. Replace inline `auditRecord(...)` and raw
  `auditLogMapper.insert(...)` at `TaskService.java:213` with
  `AuditLogService.record(...)` using `AuditActions.TASK_TRANSITION`.

## AuditLogService Design

Required interface:

```java
public interface AuditLogService {
    void record(AuditEventBuilder builder);
}
```

`AuditEventBuilder` is a small fluent builder with required fields:

- `actorType`
- `actorId`
- `action`
- `resourceType`
- `resourceId`
- `payload`

`AuditLogServiceImpl` injects:

- `AuditLogMapper`
- `Canonicalizer`
- `ObjectMapper`

Hashing uses the existing chain:

```java
canonicalizer.sha256Hex(canonicalizer.canonicalJson(payload))
```

Do not re-implement canonical JSON or SHA-256. The implementation writes
payload JSON with `ObjectMapper`, inserts through `AuditLogMapper.insert`, and
throws `IllegalStateException` if the mapper does not affect exactly one row.

Successful business mutation audit writes are fail-fast: failure to record the
audit row rolls back the business transaction.

## AuditActions Registry

Use a centralized constants class, not inline strings:

```java
public final class AuditActions {
    public static final String TASK_TRANSITION = "task.transition";
    public static final String TASK_DELETE = "task.delete";
    public static final String SCHEMA_PUBLISH = "schema.publish";
    public static final String SCHEMA_ARCHIVE = "schema.archive";
    public static final String SCHEMA_VERSION_CREATE = "schema.version_create";
    public static final String SUBMISSION_CREATE = "submission.create";
    public static final String SUBMISSION_SUPERSEDE = "submission.supersede";
    public static final String AI_REVIEW_FIELD_ASSIST = "ai_review.field_assist";
    public static final String AI_REVIEW_FAILED = "ai_review.failed";
    public static final String AI_REVIEW_RECORDED_FAILED_CALL = "ai_review.recorded_failed_call";
    public static final String REVIEW_APPROVE = "review.approve";
    public static final String REVIEW_REJECT = "review.reject";
    public static final String EXPORT_SNAPSHOT_CREATE = "export.snapshot_create";
    public static final String EXPORT_SNAPSHOT_DIFF = "export.snapshot_diff";

    private AuditActions() {}
}
```

Constants for deferred actions may exist even before write sites, but tests
must assert no inline action strings remain in implemented audit write paths.

## Query Path Design

### `GET /audit-logs`

Owner-only paginated query.

Query params:

- `page`
- `size`
- `actionTypes` as comma-separated string
- `resourceTypes` as comma-separated string
- `actorUserId`
- `resourceId`
- `from` ISO datetime
- `to` ISO datetime

Response: `PagedAuditLogs`.

Rows include:

- `id`
- `createdAt`
- `actorType`
- `actorId`
- actor display name when the actor is a known user
- `action`
- `resourceType`
- `resourceId`
- `payload`
- `payloadHash`

Mapper design: eager left join to `users` for query pages. This keeps the UI
table simple and is bounded by pagination.

### `GET /audit-logs/export.csv`

Owner-only CSV export with the same filters.

Export is not paginated, but it has a hard cap of 50,000 rows. If the filtered
count exceeds 50,000 rows, return `413 Payload Too Large` with guidance to
narrow the date range or action filters.

Response headers:

- `Content-Type: text/csv; charset=utf-8`
- `Content-Disposition: attachment; filename="audit-logs-YYYYMMDD-HHMMSS.csv"`

Mapper design: use a bounded `List<AuditLogEntity>` after `countFiltered(...)`
passes under the cap. Streaming cursor can be revisited if real data exceeds
the demo-scale cap, but adding MyBatis cursors in M7-P1 would inflate scope.

## AuditLogMapper Extension

Add methods:

- `selectFiltered(...)` for paginated query
- `countFiltered(...)` for totals and export cap
- `selectFilteredForExport(...)` for CSV rows after cap check

The existing `AuditLogMapperContractTest` at
`services/api/src/test/java/com/labelhub/api/module/admin/mapper/AuditLogMapperContractTest.java:10-18`
currently permits only `insert` and `selectByResource`; C4 must update it to
include the new append-only read methods while still rejecting update/delete
methods.

## V11 Migration

Add:

```sql
CREATE INDEX idx_audit_logs_action_time ON audit_logs (action, created_at);
```

Suggested filename:

`services/api/src/main/resources/db/migration/V202612010900__add_audit_log_action_index.sql`

Rationale: existing indexes support resource-led and actor-led investigations,
but M7-P1 adds action-led filtering such as "all `schema.publish` events in the
last 7 days". `(action, created_at)` matches that query shape with low index
width.

Do not add `(action, resource_type, created_at)` in M7-P1. That compound index
is wider, less generally useful for action-only filtering, and should wait for
real query-plan evidence from the new Owner page.

Migration count changes from `10` to `11`.

## Frontend Query Page Design

New route: `/owner/audit-logs`.

Required UI:

- Filters:
  - action type multi-select
  - resource type multi-select
  - actor user search or numeric actor user ID input
  - date range
  - optional resource ID
- Table columns:
  - timestamp
  - actor
  - action
  - resource type + id
  - payload preview truncated to 200 characters
- Payload detail:
  - expandable modal or drawer showing full payload verbatim
- CSV export button using the same filters
- Pagination

Sidebar:

- Add Owner-only "审计日志" via `roleRoutePriority` and `AppLayout` existing
  menu generation. `apps/web/src/shared/auth/roleRoutes.ts:1-7` is the current
  route registry.

PII boundary: payload fields are displayed verbatim in this demo system. PII
redaction is out of scope and must be documented in final verification.

## Per-Cluster Estimate

| Cluster | Scope | Estimate | Stop at 50% over |
|---|---|---:|---:|
| C1 | OpenAPI: 2 operations + schemas + generated frontend types | 90 | 135 |
| C2 | V11 migration + `AuditActions` + `AuditLogService` + mapper query/export | 230 | 345 |
| C3 | Audit writes for adjudicated existing surfaces + `task.transition` migration | 190 | 285 |
| C4 | Backend tests: service, write sites, filters, CSV cap, mapper contract | 300 | 450 |
| C5 | Frontend Owner audit page, feature hooks, sidebar nav, CSV download | 230 | 345 |
| C6 | Final docs, screenshots, humanpending | N/A | N/A |

Total base code estimate: `1040` changed lines. Hard cap: `1100`.

This estimate excludes implementation of missing business features
(`schema.archive`, `submission.supersede`, real `FieldAssist`) and excludes
turning diff reads into audited writes until user adjudication.

## Risk Register

| Risk | Resolution |
|---|---|
| Prompt count mismatch: 13 new action strings vs "12 new". | Keep explicit action list as source of truth; user adjudicates count and deferred items before code. |
| `schema.archive` and `submission.supersede` do not exist. | Defer or open separate feature design; do not fabricate audit rows. |
| `ai_review.failed` conflicts with rollback semantics. | Decide whether final failure audit is `REQUIRES_NEW`, or rely on per-attempt `ai_review.recorded_failed_call`. |
| `export.snapshot_diff` is a read path. | Decide whether M7-P1 audits reads; if yes, document that diff becomes an auditable read event. |
| Audit write failure could block business mutations. | Fail-fast for successful mutations; audit is governance evidence. |
| Action string drift. | Mandatory `AuditActions` constants; no inline strings in write sites. |
| CSV export memory growth. | Count first, cap at 50,000 rows, return 413 above cap. |
| Actor display name lookup. | Join users for paginated query; bounded list for CSV after cap check. |
| Payload preview overflows UI. | Truncate to 200 characters in table, full payload in modal/drawer. |
| Mapper contract currently forbids new method names. | Update audit mapper contract to permit append-only read methods, still forbid update/delete. |

## Stop Conditions

- User does not adjudicate deferred/gated action semantics.
- Any cluster exceeds estimate by 50%.
- Cumulative code diff exceeds `1100`.
- Any target write site requires business-method restructuring beyond audit
  injection and payload construction.
- Backend suite drops below `397 / 78`, excluding new tests.
- Existing `audit_logs` row format breaks query/export DTOs.
- V11 migration conflicts with an existing migration.
- OWNER-only authorization requires a new auth mechanism.
- Any inline audit action string appears in implemented write sites.

## Verification Plan

After C1:

- Regenerate frontend types.
- `mvn -pl services/api compile`
- `pnpm --filter @labelhub/web typecheck`
- `bash scripts/check-protected-endpoints.sh`
- Record new OpenAPI MD5.

After backend clusters:

- `mvn -pl services/api compile`
- `mvn -pl services/api test`
- Confirm migration count after C2 is `11`.

After frontend cluster:

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- Manual Owner audit-log page smoke.

Final C6:

- Verification doc with action registry, coverage actual vs planned, deferred
  action adjudication, V11 index rationale, CSV cap, test counts, OpenAPI MD5,
  migration count, and visual evidence.
- Screenshots:
  - `01-owner-audit-logs-list-after.png`
  - `02-owner-audit-logs-export-after.png`
- `humanpending.md` one `[M7-P1 resolved]` entry.

## Awaiting Adjudication

Before code starts, the user must decide:

1. Are `schema.archive` and `submission.supersede` deferred, or should M7-P1
   expand into new lifecycle feature work?
2. Does `ai_review.field_assist` mean current successful AI field findings, or
   the currently unimplemented field-assist endpoint?
3. Should `ai_review.failed` be a final `REQUIRES_NEW` failure audit, or is
   `ai_review.recorded_failed_call` sufficient for failure evidence?
4. Should `export.snapshot_diff` audit a read event, accepting that diff reads
   become writes to `audit_logs`?

No OpenAPI, backend, migration, frontend, generated type, test, humanpending,
or screenshot work may begin until these four items are adjudicated.
