# M6-P7 Pre-Estimate

## Status

M6-P7 is a new mini-phase after the M6-P6 closure baseline `b93743f`.
It introduces an Owner-side hard delete capability for tasks of any status.

This document is the pre-estimate gate only. No OpenAPI, backend, frontend,
or migration code may land until the user adjudicates this document.

## Pre-Estimate Gate Correction

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

## Complete FK Dependency Graph

This graph enumerates task-scoped FK edges from
`V202611220900__init_schema.sql`,
`V202611250900__add_tasks_current_dataset.sql`, and
`V202611260900__add_label_schema_current_version.sql`. Global `users`
edges are included when the source table is task-scoped, but `users`,
`roles`, `user_roles`, `audit_logs`, and `outbox` are not deleted by M6-P7.

1. tasks.owner_id → users(id)  [constraint: fk_tasks_owner]
2. task_transitions.task_id → tasks(id)  [constraint: fk_task_transitions_task]
3. task_transitions.actor_id → users(id)  [constraint: fk_task_transitions_actor]
4. datasets.task_id → tasks(id)  [constraint: fk_datasets_task]
5. dataset_items.dataset_id → datasets(id)  [constraint: fk_dataset_items_dataset]
6. dataset_items.task_id → tasks(id)  [constraint: fk_dataset_items_task]
7. label_schemas.task_id → tasks(id)  [constraint: fk_label_schemas_task]
8. label_schemas.owner_id → users(id)  [constraint: fk_label_schemas_owner]
9. schema_versions.schema_id → label_schemas(id)  [constraint: fk_schema_versions_schema]
10. tasks.current_schema_version_id → schema_versions(id)  [constraint: fk_tasks_schema_version]
11. sessions.task_id → tasks(id)  [constraint: fk_sessions_task]
12. sessions.dataset_item_id → dataset_items(id)  [constraint: fk_sessions_dataset_item]
13. sessions.labeler_id → users(id)  [constraint: fk_sessions_labeler]
14. sessions.schema_version_id → schema_versions(id)  [constraint: fk_sessions_schema_version]
15. drafts.session_id → sessions(id)  [constraint: fk_drafts_session]
16. submissions.session_id → sessions(id)  [constraint: fk_submissions_session]
17. submissions.task_id → tasks(id)  [constraint: fk_submissions_task]
18. submissions.dataset_item_id → dataset_items(id)  [constraint: fk_submissions_item]
19. submissions.labeler_id → users(id)  [constraint: fk_submissions_labeler]
20. submissions.schema_version_id → schema_versions(id)  [constraint: fk_submissions_schema_version]
21. submissions.superseded_by_id → submissions(id)  [constraint: fk_submissions_superseded_by]
22. ai_calls.submission_id → submissions(id)  [constraint: fk_ai_calls_submission]
23. ai_calls_in_field.submission_id → submissions(id)  [constraint: fk_ai_calls_in_field_submission]
24. ai_calls_in_field.ai_call_id → ai_calls(id)  [constraint: fk_ai_calls_in_field_call]
25. adjudication_rules.task_id → tasks(id)  [constraint: fk_adjudication_rules_task]
26. adjudication_rules.created_by → users(id)  [constraint: fk_adjudication_rules_creator]
27. quality_ledger_entries.submission_id → submissions(id)  [constraint: fk_quality_ledger_submission]
28. quality_ledger_entries.task_id → tasks(id)  [constraint: fk_quality_ledger_task]
29. quality_ledger_entries.actor_id → users(id)  [constraint: fk_quality_ledger_actor]
30. quality_ledger_entries.ai_call_id → ai_calls(id)  [constraint: fk_quality_ledger_ai_call]
31. current_verdicts.submission_id → submissions(id)  [constraint: fk_current_verdicts_submission]
32. current_verdicts.task_id → tasks(id)  [constraint: fk_current_verdicts_task]
33. current_verdicts.rule_version_id → adjudication_rules(id)  [constraint: fk_current_verdicts_rule]
34. current_verdicts.derived_from_ledger_entry_id → quality_ledger_entries(id)  [constraint: fk_current_verdicts_ledger]
35. review_actions.submission_id → submissions(id)  [constraint: fk_review_actions_submission]
36. review_actions.task_id → tasks(id)  [constraint: fk_review_actions_task]
37. review_actions.reviewer_id → users(id)  [constraint: fk_review_actions_reviewer]
38. export_jobs.task_id → tasks(id)  [constraint: fk_export_jobs_task]
39. export_jobs.requested_by → users(id)  [constraint: fk_export_jobs_requester]
40. export_snapshots.export_job_id → export_jobs(id)  [constraint: fk_export_snapshots_job]
41. export_snapshots.task_id → tasks(id)  [constraint: fk_export_snapshots_task]
42. export_snapshots.verdict_rule_version_id → adjudication_rules(id)  [constraint: fk_export_snapshots_rule]
43. tasks.current_dataset_id → datasets(id)  [constraint: fk_tasks_current_dataset]
44. label_schemas.current_version_id → schema_versions(id)  [constraint: fk_label_schemas_current_version]

The corrected order is derived by full topological sort of the FK graph. It
supersedes the prompt's 19-step listing.

## Corrected Deletion Order

The corrected order contains 21 data mutations: 4 NULL-out preconditions and
17 DELETE steps. Before step 1, the service must read task-scoped export object
keys for after-commit object-storage cleanup.

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

## S3 Cleanup Scope

M6-P7 reuses `ObjectStorageWriter.deleteObject(objectKey)`. It must not modify
that helper.

Pre-estimate verification found that the current baseline has two possible
export object-key sources:

- `export_jobs.file_key`: a legacy or future single-object field. Current
  `ExportService.createSnapshot` does not populate it, so most existing rows
  are expected to be null.
- `export_snapshots.object_key`: the object-key prefix written by M5/M6 Trusted
  Export. `ExportService.createSnapshot` writes each artifact file under this
  prefix and also writes `manifest.json`.

The deletion service must collect cleanup keys before deleting DB rows:

1. Every non-null, non-blank `export_jobs.file_key` for the task.
2. For every `export_snapshots` row with a non-null `object_key` prefix:
   `object_key + "manifest.json"`.
3. For every `export_snapshots.file_manifest.files[]` entry with a `name`:
   `object_key + name`.

Keys are de-duplicated and blank values are skipped. If a historical row has
no `file_manifest` or no usable `object_key`, object cleanup is skipped for
that row and the DB delete still proceeds.

This corrects the initial prompt statement that `export_snapshots` had no
independent object-storage field. `V202611271100` added
`export_snapshots.object_key`, and the Java entity maps it.

## Transaction Boundary

All 21 DB mutations run inside one `@Transactional` service method. Any DB
failure rolls back all DB mutations.

Object-storage cleanup is best-effort with WARN-level logging and must not
rollback the DB delete. The implementation plan should register cleanup after
the transaction commits, using the object keys collected before DB deletion.
This timing avoids deleting objects when the DB transaction later rolls back,
while still preserving the M6-P4b posture that object cleanup failure does not
mask or undo the primary DB operation.

If after-commit registration is unavailable in the final implementation path,
the implementation must stop for user adjudication before using an inside-
transaction cleanup fallback.

## Per-Cluster Estimate

Changed-line counts include insertions plus deletions under tracked files.
Generated backend files under `services/api/target/` are not tracked.

| Cluster | Scope | Estimate |
|---------|-------|----------|
| 1 | OpenAPI YAML `DELETE /tasks/{taskId}` operation, 204/404/403 responses, frontend type regeneration | 30 changed lines |
| 2 | Backend hard-delete service path, one focused deletion mapper, controller endpoint, auth/security wiring, after-commit S3 cleanup registration | 225 changed lines |
| 3 | Backend tests for authorization, 404, full cascade boundary, object-key cleanup selection, and DB rollback around the corrected order | 185 changed lines |
| 4 | Owner task list delete action with confirmation, mutation hook, query invalidation, generated frontend schema update | 50 changed lines |
| 5 | Final docs and humanpending closure | N/A code lines |

Total code estimate: 490 changed lines. Hard cap: 500 changed lines.

The estimate is higher than the original prompt's roughly 360 lines because
the corrected order adds `tasks.current_dataset_id`, after-commit cleanup
registration, `export_snapshots.object_key` cleanup, and a fuller FK-boundary
test matrix.

## Risk Register

| Risk | Resolution |
|------|------------|
| Schema versions may be shared across tasks or taskless schemas. | Before implementing schema deletion, verify that task-scoped sessions, submissions, and `tasks.current_schema_version_id` only reference schema versions whose `schema_versions.schema_id` joins to `label_schemas.task_id = ?`. If sharing is discovered, STOP for user adjudication. |
| `audit_logs` may retain task IDs for deleted tasks. | Intentional. `audit_logs` are global audit history and are not task-scoped FK rows. The forward R8 record documents this. |
| `export_snapshots.file_hash` uniqueness release. | The original unique key was already replaced by `idx_export_snapshots_file_hash` in `V202611271100`. There is no current unique-key release risk, but deleting rows still removes historical snapshot metadata. |
| Object-storage cleanup can partially fail. | Cleanup is best-effort, WARN-logged, and after-commit. DB deletion remains authoritative. |
| Object-key reconstruction from `export_snapshots.object_key` and `file_manifest` may miss malformed historical rows. | Skip malformed/blank keys and document skipped cleanup with WARN or DEBUG-level evidence. Do not block DB deletion on malformed object metadata. |
| Mapper contract tests currently protect append-only mapper surfaces. | M6-P7 intentionally adds delete methods. Existing mapper contract tests must be updated or scoped so the hard-delete path is explicit and reviewed, not silently introduced. |

## Stop Conditions

- Any cluster exceeds its estimate by 50%.
- Cumulative code diff exceeds 500 changed lines.
- Schema version sharing is discovered.
- A DB FK requires changing schema constraints or adding `ON DELETE CASCADE`.
- A V11 migration becomes necessary.
- S3 cleanup cannot be made best-effort without risking DB rollback.
- Backend full suite drops below the previous `390 tests, 0 failures, 0 errors, 78 skipped` baseline, excluding new passing tests added by M6-P7.
- Any new technical surprise changes the hard-delete story, object-cleanup scope, authorization, or evidence semantics.

## Verification Plan After Code Begins

After each code cluster, report cumulative diff against `b93743f`.

- Backend clusters: `mvn -pl services/api compile`, then `mvn -pl services/api test`.
- Frontend cluster: `pnpm --filter @labelhub/web typecheck`, then `pnpm --filter @labelhub/web build`.
- Contract guard after OpenAPI changes: `bash scripts/check-protected-endpoints.sh`.
- Final docs: record old OpenAPI MD5 `c042f8bc62a15efd98bd01363b9e14ff`, new MD5, migration count still `10`, test deltas, visual sign-off, and the R8 transparency block required by the M6-P7 prompt.

## Awaiting Adjudication

The user must approve this pre-estimate before any OpenAPI, backend,
frontend, generated type, or humanpending code-path change lands.
