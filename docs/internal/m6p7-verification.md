# M6-P7 Verification

## Status

M6-P7 closed on 2026-05-27. Baseline: `b93743f` (M6-P6c closure).
Final code head: `4555148` (Cluster 4b Popconfirm viewport fix). Final docs
head: this commit.

M6-P7 is a post-M6-P6 mini-phase introducing Owner-side hard delete for tasks
of any status. The capability is intentionally destructive: it removes a task
and the task-scoped facts that M6-P0.5 otherwise treats as immutable within an
alive task.

## Commit Map

| Commit | Cluster | Purpose |
|--------|---------|---------|
| `d83f60f` | docs | Scope + pre-estimate with FK-corrected order |
| `272bc61` | C1 | OpenAPI `DELETE /tasks/{taskId}` + controller/service stub |
| `852330c` | C2 | `TaskService.deleteTask` 21-step cascade implementation |
| `1774a33` | C2b | Blank-name S3 key skip hot-fix discovered during C3 test writing |
| `767d590` | C3 | Backend cascade test matrix, 6 methods covering 7+ cases |
| `e2a6ee8` | C4 | Frontend delete action with Popconfirm |
| `4555148` | C4b | Popconfirm viewport placement fix after manual visual audit |
| this commit | C5 | Verification doc + screenshots + humanpending |

## R8 Transparency Records

### Record A: Philosophical Override

```
User adjudicated against auditor's repeated recommendation of Option C
(draft-only hard delete) in favor of Option A (hard delete any status).
The auditor's concerns were:

1. Option A consumes facts protected by the M6-P0.5 system-level decision
   "Submission is an immutable answer fact." Physical deletion of any
   submission directly contradicts that decision.

2. Option A consumes append-only quality_ledger_entries, which are the
   source of truth for the M4/M5 Verdict-derivation Highlight 2 defense
   story.

3. Option A breaks Trusted Export reproducibility (Highlight 3) for any
   export snapshot whose task has been deleted: the snapshot remains in
   audit_logs and S3 (if not also wiped) but the task it references no
   longer exists.

4. Option A requires cascading deletion across 12 directly-linked tables
   plus indirect submission/ai_call chains, vs Option C's zero cascade.

The user accepted these costs. The auditor proceeds with Option A.

Defense talk-track note for the team: if a reviewer asks "what happens
to immutable submission facts when a published task is deleted?", the
honest answer is "the M6-P7 hard delete is explicitly an Owner-side
recovery tool that supersedes the immutability of facts within that
task; the immutability decision applies to the lifecycle of an active
task, not to the existence of the task itself. Quality ledger
append-only semantics still hold inside any task that is alive."
```

### Record B: Pre-Estimate Gate Correction

```
The auditor's original M6-P7 prompt listed a 19-step deletion order based
on direct task_id FKs only. Pre-estimate gate verification against the
full FK graph (V202611220900__init_schema.sql + V202611250900 +
V202611260900) discovered 4 ordering conflicts:

1. quality_ledger_entries.ai_call_id → ai_calls(id): ledger must delete
   before ai_calls
2. export_snapshots.verdict_rule_version_id → adjudication_rules(id):
   snapshots must delete before rules
3. tasks.current_dataset_id → datasets(id) (V202611250900): must NULL
   before datasets delete
4. current_verdicts.derived_from_ledger_entry_id → quality_ledger_entries(id):
   verdicts must delete before ledger

R8 transparency record:
- Conflicts 1-3 reported by agent at pre-estimate gate.
- Conflict 4 added by auditor during pre-estimate review (agent omitted
  it on first FK scan).

Corrected order is derived by full topological sort of the FK graph, not
by patching the original 19 steps. The corrected order supersedes the
prompt's 19-step listing.
```

### Record C: Cluster 1 Boundary Correction

```
Cluster 1 implementation also triggered a stop. Auditor's original Cluster
1 boundary ("OpenAPI only, contract-only commit") was incompatible with
this repo's compile boundary: OpenAPI generates Java interfaces that the
controller must implement in the same compile unit. Agent stopped before
commit, surfaced the conflict, and proposed two resolutions. User
adjudicated to merge OpenAPI + controller/service stub into Cluster 1,
with the cascade implementation isolated to Cluster 2. Estimate adjusted:
Cluster 1 ~70 lines, Cluster 2 ~180 lines, total unchanged.
```

### Record D: Cluster 2b Blank-Name Hot-Fix

```
Cluster 3 test writing reverse-revealed a Cluster 2 production bug:
collectExportObjectKeys treated empty-string file_manifest.files[].name
as a valid name, producing an S3 key equal to the snapshot's object_key
prefix. Agent stopped Cluster 3 before commit, surfaced the bug, and
proposed Cluster 2b: minimal production fix (Java 17 pattern matching
for non-blank String narrowing) plus one focused regression unit test.
User adjudicated to land Cluster 2b before resuming Cluster 3. The
Cluster 3 draft was held untracked during Cluster 2b and verified by
sha256 before Cluster 3 resumed.
```

### Additional Closure Record: Cluster 4b Viewport Fix

Manual UI audit after Cluster 4 found that Semi Popconfirm could drift past
the right viewport edge when the task table filled a maximized browser. Cluster
4b (`4555148`) forced the confirmation to open leftward and wrapped the warning
copy in a constrained text block. This is recorded as a frontend-only viewport
fix after the approved Cluster 4 UX path.

Manual delete checks also found repeated 500 responses before the API restart.
The root cause was a stale API runtime: the running Spring process predated the
Cluster 2 hard-delete bytecode. Restarting the API on current HEAD made DELETE
return 204, and follow-up GET returned 404 for the deleted verification task.

## Implemented Cascade Order

The implemented order is copied from `docs/internal/m6p7-pre-estimate.md`.
It is implemented in `services/api/src/main/java/com/labelhub/api/module/task/service/TaskService.java`
lines 144-185 of current code head `4555148` (original implementation commit
`852330c`). Mockito `InOrder` verification in
`TaskServiceDeleteTest.verifyFullCascadeOrder` covers all 21 steps at
`services/api/src/test/java/com/labelhub/api/module/task/service/TaskServiceDeleteTest.java`
lines 222-245.

1. tasks [NULL current_dataset_id]
   // blocks: fk_tasks_current_dataset would prevent datasets delete.
2. tasks [NULL current_schema_version_id]
   // blocks: fk_tasks_schema_version would prevent schema_versions delete.
3. label_schemas [NULL current_version_id]
   // blocks: fk_label_schemas_current_version would prevent schema_versions delete.
4. submissions [NULL superseded_by_id]
   // blocks: fk_submissions_superseded_by self-reference would prevent submissions delete.
5. ai_calls_in_field [DELETE]
   // blocks: fk_ai_calls_in_field_submission and fk_ai_calls_in_field_call would prevent submissions or ai_calls delete.
6. current_verdicts [DELETE]
   // blocks: fk_current_verdicts_submission, fk_current_verdicts_task, fk_current_verdicts_rule, and fk_current_verdicts_ledger would prevent submissions, tasks, adjudication_rules, or quality_ledger_entries delete.
7. review_actions [DELETE]
   // blocks: fk_review_actions_submission and fk_review_actions_task would prevent submissions or tasks delete.
8. export_snapshots [DELETE]
   // blocks: fk_export_snapshots_job, fk_export_snapshots_task, and fk_export_snapshots_rule would prevent export_jobs, tasks, or adjudication_rules delete.
9. quality_ledger_entries [DELETE]
   // blocks: fk_quality_ledger_submission, fk_quality_ledger_task, and fk_quality_ledger_ai_call would prevent submissions, tasks, or ai_calls delete.
10. ai_calls [DELETE]
    // blocks: fk_ai_calls_submission would prevent submissions delete.
11. drafts [DELETE]
    // blocks: fk_drafts_session would prevent sessions delete.
12. submissions [DELETE]
    // blocks: fk_submissions_session, fk_submissions_task, fk_submissions_item, and fk_submissions_schema_version would prevent sessions, tasks, dataset_items, or schema_versions delete.
13. sessions [DELETE]
    // blocks: fk_sessions_task, fk_sessions_dataset_item, and fk_sessions_schema_version would prevent tasks, dataset_items, or schema_versions delete.
14. dataset_items [DELETE]
    // blocks: fk_dataset_items_dataset and fk_dataset_items_task would prevent datasets or tasks delete.
15. export_jobs [DELETE]
    // blocks: fk_export_jobs_task would prevent tasks delete.
16. adjudication_rules [DELETE]
    // blocks: fk_adjudication_rules_task would prevent tasks delete; current_verdicts and export_snapshots have already cleared rule references.
17. datasets [DELETE]
    // blocks: fk_datasets_task would prevent tasks delete; tasks.current_dataset_id and dataset_items have already cleared dataset references.
18. schema_versions [DELETE]
    // blocks: fk_schema_versions_schema would prevent label_schemas delete; task, label_schema, session, and submission references have already been cleared.
19. label_schemas [DELETE]
    // blocks: fk_label_schemas_task would prevent tasks delete.
20. task_transitions [DELETE]
    // blocks: fk_task_transitions_task would prevent tasks delete.
21. tasks [DELETE]
    // blocks: all dependents cleared.

## S3 Cleanup Behavior

Object key collection happens before any DB delete at
`TaskService.collectExportObjectKeys` lines 267-286. Keys are sourced from:

- `export_snapshots.object_key + "manifest.json"`
- `export_snapshots.object_key + file_manifest.files[].name`, where `name` is
  a non-blank String after Cluster 2b
- `export_jobs.file_key`

Keys are de-duplicated via `LinkedHashSet`. After-commit registration uses
`TransactionSynchronizationManager.registerSynchronization` at lines 312-323.
Each cleanup call uses `ObjectStorageWriter.deleteObject`; failures are logged
at WARN level at lines 325-331 and do not propagate.

D-口径: this mirrors the M6-P4b Trusted Export inline cleanup pattern. S3
failure does not mask or rollback the DB delete. DB delete failure short-circuits
before registration, so S3 cleanup is never touched on rollback; this is covered
by `deleteTask_mapperFailure_doesNotRegisterS3Cleanup`.

## Authorization Path

- Controller: `@PreAuthorize("hasRole('OWNER')")` on `TasksController.deleteTask`.
- Service: `selectByIdForUpdate(taskId)` row lock at `TaskService.java:134`.
- 404: `TaskNotFoundException` before any delete at lines 135-137.
- 403: `TaskAccessDeniedException` before any delete at lines 138-140.

## Protected Endpoint Guard Caveat

`scripts/check-protected-endpoints.sh` only verifies the presence of 5
hard-coded protected paths in the OpenAPI spec. It does not verify that
`DELETE /tasks/{taskId}` is OWNER-protected. The OWNER protection of this
endpoint is enforced by:

1. `@PreAuthorize("hasRole('OWNER')")` on `TasksController.deleteTask`
2. Global OpenAPI `bearerAuth` security requirement applied by the contract
3. Generated `@SecurityRequirement` from openapi-generator on the controller method

This caveat is documented forward, not retrofitted to the protected-endpoint
guard script in this phase.

## New OpenAPI MD5 Baseline

- Old (M6-P5.1 baseline through M6-P6c head `b93743f`): `c042f8bc62a15efd98bd01363b9e14ff`
- New (M6-P7 Cluster 1 forward): `dc4a91c6471b3cbbf0bc0ba62139087e`

Future phases must reference `dc4a91c6471b3cbbf0bc0ba62139087e` until the next
contract change.

## Migration Count

Migration count remains 10. No V11 migration was created. The cascade is
application-level only; no FK was changed to database-level CASCADE.

## Backend Test Suite Result

- Pre-M6-P7 baseline at `b93743f`: 390 tests, 0 failures, 0 errors, 78 skipped
- After Cluster 2b (`1774a33`): 391 tests (+1 regression test for blank-name skip)
- After Cluster 3 (`767d590`): 397 tests (+6 cascade matrix tests)
- M6-P7 final: 397 tests, 0 failures, 0 errors, 78 skipped

D-口径: sandbox first runs can hit SocketException on local MySQL access for the
full `mvn test` suite. Escalated reruns passed consistently, matching prior M6
socket D-口径 handling. Cluster 5 is docs-only and did not change backend code.

## Cumulative M6-P7 Diff

- Full M6-P7 delta after this docs commit: `19 files changed, 1314 insertions(+), 13 deletions(-)`
- Code-only M6-P7 delta after Cluster 4b: `11 files changed, 665 insertions(+), 13 deletions(-)`

## Cluster Estimate vs Actual

| Cluster | Estimate | Actual | Stop triggered |
|---------|----------|--------|----------------|
| C1 (OpenAPI + stub) | 26 lines | 30 lines (16 YAML + 14 Java) | No |
| C2 (cascade) | 225 lines | 262 lines (96% of 270 cap) | No |
| C2b (hot-fix) | 33 lines | 42 lines | No |
| C3 (tests) | 185 lines | 247 lines (89% of 278 cap) | No |
| C4 (frontend) | 50 lines | 74 lines (99% of 75 cap) | No |
| C4b (viewport fix) | 8 lines | 8 lines | No |

The original 500-line hard cap was set before the Cluster 1 compile-boundary
correction, before Cluster 2b surfaced during test writing, and before the
Cluster 4b viewport fix from manual audit. The end-state exceeded the original
aggregate cap, but every increase is attached to a stop/adjudication record and
no individual cluster crossed its 50% stop threshold.

## M6-P7 Final State

- Backend can permanently delete a task and all 17 task-scoped fact surfaces.
- S3 cleanup is after-commit, de-duplicated, best-effort, and WARN-logged.
- Frontend exposes a destructive Owner action with Popconfirm informed consent.
- Quality Ledger append-only semantics continue to hold inside any alive task.
- The M6-P0.5 submission immutability decision is superseded only for the act
  of hard-deleting an entire task.

## Visual Verification

Three after-screenshots are archived under `docs/screenshots/m6p7-after-set/`:

- `01-owner-task-list-with-delete-after.png`: Owner task list with delete action visible on each row.
- `02-owner-task-delete-popconfirm-after.png`: Popconfirm open, warning copy visible and in-viewport.
- `03-owner-task-delete-success-toast-after.png`: Success Toast `任务已永久删除` plus list count decremented from 14 to 13.

The visual capture used a temporary task (`m6p7-visual-delete-...`) and deleted
it during capture. Follow-up API GET returned 404, confirming the screenshot
flow did not leave that temporary task behind.
