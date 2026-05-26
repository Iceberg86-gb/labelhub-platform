# M6-P1 Scope Budget

> Basis: M6-P0 audit + M6-P0.5 lifecycle research  
> Status: ready for implementation prompt. User裁决 is complete: Q1-Q9 = A, Q10 = B.  
> Constraint model: strict constraints remain default; P0/P1 bug fixes may use explicit, logged exceptions.

## Recommended M6-P1 Theme

**Submission Lifecycle + Default Flow Repair**

M6-P1 should repair the normal path:

1. labeler submits a session,
2. reviewer sees the submission in the default queue,
3. owner Trusted Export includes the submitted answer facts,
4. AI review and reviewer ledger facts coexist without mutating submission lifecycle,
5. task creation no longer 500s when deadline semantics and contract drift.

Cost/performance remains gated until this default flow is semantically correct.

## Final裁决 Inputs

| Question | Final裁决 | Implementation impact |
|----------|-----------|-----------------------|
| Q1 | A: submit writes `submitted` | `SessionService.submit` behavior changes under a logged bug-fix exception. |
| Q2 | A: deprecate `under_ai_review` and V9-normalize historical rows | Add V9 migration to update default and normalize existing dev rows. |
| Q3 | A: reviewer default queue only uses `submitted` | Queue SQL/default can stay conceptually narrow; regression must prove real submit populates it. |
| Q4 | A: Trusted Export exports submitted answer facts | Export SQL can stay `submitted`; regression must prove real submit populates export scope. |
| Q5 | A: approved/rejected are verdict context, not submission statuses | No new submission lifecycle states for verdict. |
| Q6 | A: AI review appends facts only | Add regression that AI review does not mutate `submission.status`. |
| Q7 | A: minimal submitted-only answer lifecycle | Avoid new lifecycle states in M6-P1. |
| Q8 | A: `deadlineAt` required at create | OpenAPI requiredness + backend controlled 400; no draft-task/publish-time validation UX. |
| Q9 | A: one session per dataset item per labeler | M6-P1 may clarify copy/tests but should not add task-scoped claim locking. |
| Q10 | B: full regression set | Budget is below 500 functional lines, so implement the full P0/P1 regression gate. |

## Backend Scope

| File | Change type | Constraint classification | Estimated functional lines |
|------|-------------|---------------------------|----------------------------|
| `SessionService.submit` | Change normal submission status write | Bug-fix exception | ~5 |
| `V2026....__submission_lifecycle_alignment.sql` | Add V9 migration for status default and legacy row normalization | Additive migration | ~10-15 SQL |
| New `SubmissionStatusCodes` or equivalent | Centralize status constants | Additive file | ~25 |
| `ReviewerQueueService` / queue mapper | Likely no-op; add regression proving real submit appears in default submitted queue | Guarded by tests | ~0-10 |
| `SubmissionMapper.selectSubmittedByTaskOrderedById` | Likely no-op; add regression proving real submit appears in export scope | Guarded by tests | ~0-10 |
| `TasksController.createTask` / command mapping | Return controlled 400 for missing `deadlineAt` instead of NPE/500 | Bug-fix exception | ~5-15 |
| OpenAPI `labelhub.yaml` | Add `deadlineAt` to `CreateTaskRequest.required`; defer `SubmissionStatus` enum unless churn stays tiny | Compatible/minor bump | ~10-30 |

Estimated backend functional budget: **65-130 lines**.

## V9 Migration SQL

M6-P1 should add a new additive Flyway migration. The exact filename follows the existing timestamp convention, but the body should stay this small:

```sql
ALTER TABLE submissions
  ALTER COLUMN status SET DEFAULT 'submitted';

UPDATE submissions
SET status = 'submitted'
WHERE status = 'under_ai_review';
```

Rationale:

- The first statement fixes future inserts that rely on the physical default.
- The second statement normalizes historical dev rows created under the M3 naming mistake.
- The rollback path is the `m5-p7-baseline` tag plus dev database reset; there is no production data migration burden in this project state.

## Frontend Scope

| File | Change type | Constraint classification | Estimated functional lines |
|------|-------------|---------------------------|----------------------------|
| `CreateTaskModal.tsx` | Keep existing deadline required validation aligned with final contract; adjust only if generated contract needs compile fixes | Bug-fix exception | ~0-10 |
| `OwnerTaskSubmissionsSection.tsx` | Map submission statuses to user-facing labels | Bug-fix/polish | ~10-20 |
| `ReviewerQueuePage.tsx` / `useReviewerQueueQuery.ts` | Likely no-op; default remains `submitted` | Guarded by tests | ~0-10 |
| Typecheck contract | Pin final status shape and deadline behavior | Additive test artifact | ~20 |

Estimated frontend functional budget: **20-50 lines**.

## Test Scope

Required regression tests if recommendations are accepted:

1. `submit_creates_submission_with_submitted_status` or equivalent replacement for the current `under_ai_review` expectation.
2. `labeler_submit_appears_in_default_reviewer_queue` integration test using the real submit endpoint, not direct fixture insertion.
3. `trusted_export_includes_submission_created_by_real_submit_path`.
4. `ai_review_does_not_change_submission_status`.
5. `create_task_requires_deadline_with_400`.
6. Existing idempotency and ledger tests still pass.
7. Direct migration/default test or repository-level assertion that new submissions no longer inherit `under_ai_review`.

Estimated test budget: **250-450 test lines**.

## Documentation Scope

| File | Change | Estimated lines |
|------|--------|-----------------|
| `docs/internal/decision-log.md` | Convert M6-P0.5 draft into final裁决 record | ~80-140 |
| `docs/internal/m6p0-smoke-audit-report.md` | Optional appendendum referencing fixed Bug #001 after M6-P1 | ~20 |
| `humanpending.md` | Mark M6-P1 resolved/follow-ups | ~20 |

## Total Budget

| Category | Estimate |
|----------|----------|
| Backend functional code | 65-130 |
| Frontend functional code | 20-50 |
| Total functional code | **85-180** |
| Tests | 250-450 |
| Docs | 100-180 |

M6-P1 should fit comfortably under the 500-line functional budget if it stays focused on lifecycle/default-flow repair and task deadline handling. Q10=B is therefore active: implement the full P0/P1 regression gate.

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Changing `SessionService.submit` breaks existing M3 tests | Test suite churn, possible provenance regressions | Update tests to encode final semantics; add AI review no-status-mutation test |
| V9 normalizes historical `under_ai_review` rows incorrectly | Dev data drift or loss of audit nuance | Keep migration limited to one default change and one targeted UPDATE; rollback through baseline tag/dev DB reset |
| Reviewer default queue behavior changes unexpectedly | M4 acceptance evidence may shift | Add real submit -> default queue integration regression |
| Trusted Export source scope changes | M5 reproducibility evidence may need re-smoke | Add real submit -> nonzero export regression; hash reproducibility tests remain |
| OpenAPI status enum becomes too strict too soon | Generated client churn | Defer `SubmissionStatus` enum unless implementation proves it is tiny; Q8 deadline requiredness is the only required contract correction |
| Deadline requiredness conflicts with existing backend command mapping | Create endpoint 500 or generated-client mismatch | Add controlled 400 regression and contract/typecheck guard |

## Proposed Commit Granularity For R10

1. `docs: finalize submission lifecycle semantics decision`
2. `test: add lifecycle regression tests`
3. `fix: normalize submission lifecycle status`
4. `fix: guard reviewer queue and export default flow`
5. `fix: require task deadline at create`
6. `docs: record M6-P1 verification and follow-ups`

Each commit should be independently reviewable; the baseline tag remains the hard rollback point.

## Scope Stop Conditions

Stop and split M6-P1 if any of these happen:

- V9 migration requires complex historical data transformation beyond status/default correction.
- OpenAPI enum introduction causes broad frontend generated-type churn.
- Reviewer queue scope needs multi-status pagination SQL rather than a simple semantic alignment.
- Duplicate claim semantics becomes larger than copy/decision documentation.
- Functional code estimate exceeds 500 lines.
