# M6-P0 Full Smoke Audit Report

> Run date: 2026-05-25  
> Environment: local machine, Docker MySQL + MinIO, Spring Boot API, Vite web app  
> Git baseline: `m5-p7-baseline` at `e4cd286`, branch `m6-engineering-hardening`  
> Provider used for audit: `mock` unless noted otherwise  
> Security note: no API keys, bearer tokens, or database passwords are recorded in this report.

## Audit Tracks 总览

| Track | Scope | Pass | Warning | Fail |
|-------|-------|------|---------|------|
| A: M2 17-step smoke | Schema/task/dataset/session/submission/version binding | 13 | 4 | 0 |
| B: M3 AI provenance | AI review, idempotency hit, labeler visibility, cross-role access | 5 | 0 | 0 |
| C: M4 Quality Ledger | Reviewer detail, approve/reject, derived verdict, RBAC | 5 | 2 | 0 |
| D: M5 integration | Trusted Export, diff modal, AI + reviewer ledger coexistence | 6 | 3 | 0 |
| E: Boundary/error probes | Auth, pagination, invalid upload, duplicate claim, selected edge cases | 8 | 7 | 0 |
| **Total** | **53 audit checks** | **37** | **16** | **0** |

M6-P0 did not find a data-loss bug or a security boundary break. It did find one central lifecycle/status mismatch that affects multiple default user paths and should drive M6-P1.

## Baseline Lock

Baseline lock completed before audit:

- Git repo initialized.
- `.gitignore` added before first commit so `target/`, `dist/`, `node_modules/`, `.DS_Store`, local env files, logs, and local data directories stay out of baseline.
- First commit: `e4cd286 chore: M5-P7 baseline lock`.
- Tag: `m5-p7-baseline`.
- Working branch: `m6-engineering-hardening`.

Rollback paths:

- Return to sealed baseline branch: `git checkout main`
- Return to exact baseline tag: `git checkout m5-p7-baseline`

## P0 严重 Bug

### Bug #001: Submission status mismatch hides normal submissions from default reviewer queue and Trusted Export

- Severity: **P0/P1 boundary; treat as M6-P1 top priority**
- Reproduction:
  1. Owner publishes task #18 with dataset #8 and schema version #12.
  2. Labeler claims task and submits submission #6.
  3. Reviewer opens default `/reviewer/submissions`.
  4. Owner opens Trusted Export card and exports task #18 twice.
- Expected:
  - A normal newly submitted submission appears in the reviewer queue by default.
  - Trusted Export includes the task's submitted training facts unless intentionally scoped otherwise.
- Actual:
  - Submission #6 is persisted with `status = under_ai_review`.
  - Default reviewer queue filters `status=submitted`, so #6 does not appear in the default queue.
  - API check `/api/reviewer/submissions?status=under_ai_review` returns #6, confirming the row exists but default filter misses it.
  - Trusted Export generated reproducible snapshots #3 and #4, but record count/submissions was `0`.
- Physical evidence:
  - `SessionService.submit` writes `under_ai_review`.
  - `ReviewerQueueService` default status is `submitted`.
  - `ReviewerController` default query status is `submitted`.
  - `SubmissionMapper.selectSubmittedByTaskOrderedById` filters `status = 'submitted'`.
- Impact:
  - M4 reviewer default workflow misses the latest normal submission.
  - M5 Trusted Export reproducibility still works, but normal user flow produces an empty submission export.
  - UI exposes raw `under_ai_review` status in owner submitted-records table.
- M6-P1 direction:
  - First decide submission lifecycle semantics, then align reviewer queue defaults, export source-state collection, UI status labels, and regression tests.

## P1 中等 Bug

### Bug #002: Create task can 500 when `deadlineAt` is missing

- Reproduction:
  - In owner task creation modal, title/description/quota were filled and deadline text was visually present, but the request reached the API with `deadlineAt = null`.
- Expected:
  - Either frontend blocks submit with a clear validation message, or backend accepts a nullable deadline if the OpenAPI contract allows it.
- Actual:
  - API returned `Internal server error`.
  - Backend stack trace:
    - `TasksController.createTask` dereferences `request.getDeadlineAt().toLocalDateTime()` with no null guard.
- Contract evidence:
  - `CreateTaskRequest.required` only includes `title` and `quotaTotal`; `deadlineAt` is optional in the contract.
- M6-P1 direction:
  - Decide whether deadline is required. If yes, fix OpenAPI + frontend validation. If optional, fix controller and tests.

### Bug #003: New-task schema creation path is not discoverable in the UI

- Reproduction:
  - After creating task #18, the UI showed Schema 管理 and Designer entries, but no obvious route/button to create a first schema for the new task.
- Expected:
  - Owner can create and publish v1 schema from the task flow without direct API calls.
- Actual:
  - Audit used API workarounds to create schema #7 and publish schema versions #12 and #13.
- Impact:
  - M2 core invariant passes, but the end-to-end owner setup workflow is not fully self-service from the UI.
- M6-P2 direction:
  - Add or expose the missing entry point after the status/default-flow repair is done.

## P2 Polish / Product Boundary Items

### Polish #001: Login autofill state can look filled while React validation treats fields as empty

- Chrome visually autofilled credentials, but the form did not consider values present until fields were explicitly set/typed.
- Scope: UI polish; not a backend bug.

### Polish #002: Owner task list created-time column shows `-`

- The task list showed `-` in created-time-like columns even while tasks exist and APIs return task data.
- Scope: table mapping/date formatting.

### Polish #003: Owner submitted-records table shows raw `under_ai_review`

- Once the lifecycle decision is made, status labels should be localized and consistent with reviewer queue semantics.

### Product Boundary #001: Same labeler can claim the same task again if more dataset items are available

- API check created session #10 after session #9/submission #6.
- This may be intended when a task has multiple dataset items. It needs an explicit product decision:
  - one claim per task per labeler, or
  - one claim per dataset item per labeler.

### Product Boundary #002: Some destructive/failure probes were intentionally not run in P0

- MinIO-down export failure, AI provider timeout/401, expired token, 50-field schema, and 100-submission task were not fully executed in this audit to avoid disrupting the baseline run and because M6-P1 is already determined by the lifecycle mismatch.
- They should be explicit M6 hardening tasks, not silently assumed covered.

## Track A: M2 17-Step Smoke

| Step | Result | Notes |
|------|--------|-------|
| 1. owner_demo login | ✅ | Login succeeded. |
| 2. Create task | ⚠️ | UI path hit `deadlineAt` null 500. API workaround created task #18. |
| 3. Create v1 schema | ⚠️ | UI entry point not discoverable. API workaround created schema #7. |
| 4. Publish v1 | ⚠️ | API workaround published schema version #12. |
| 5. Upload dataset JSONL | ⚠️ | UI upload automation was unreliable; API upload created dataset #8 with 2 items. |
| 6. Set current dataset | ✅ | API set dataset #8 as current; task detail reflected it. |
| 7. Publish task | ✅ | UI publish succeeded; timeline showed publish reason. |
| 8. labeler_demo login | ✅ | Login succeeded. |
| 9. Marketplace shows task | ✅ | Task #18 visible with available data. |
| 10. Claim task/session | ✅ | Claimed session #9. |
| 11. Renderer renders v1 | ✅ | `Smoke Text` rendered. |
| 12. Fill and autosave | ✅ | Badge changed to saved after blur/wait. |
| 13. Submit | ✅ | Submission #6 created. |
| 14. My data | ✅ | Submission #6 visible with schema #12. |
| 15. Publish v2 | ⚠️ | API workaround published schema version #13 with added field `p0_field_b`. |
| 16. Open submitted detail | ✅ | Labeler detail loaded. |
| 17. Verify v1 binding | ✅ | Submitted detail still renders v1 only; v2 added field is absent. |

Bright-line result: **Highlight 1 still passes.** The submitted answer remains bound to schema version #12 even after v2 is published.

## Track B: M3 AI Provenance Smoke

| Check | Result | Notes |
|-------|--------|-------|
| Owner triggers AI review | ✅ | Drawer showed mock provider result for submission #6. |
| Metadata visible | ✅ | `mock / mock-v1`, cost, latency, summary, and field finding displayed. |
| Idempotency hit | ✅ | Second same-prompt trigger showed "复用历史结果" and did not call provider again. |
| Labeler shared provenance | ✅ | Labeler submission detail showed the AI check record read-only. |
| Cross-role access | ✅ | Labeler direct access to owner URL returned forbidden. |

## Track C: M4 Quality Ledger Smoke

| Check | Result | Notes |
|-------|--------|-------|
| Reviewer queue default | ⚠️ | Default queue omitted new #6 because status default is `submitted` while new row is `under_ai_review`. |
| Direct reviewer detail | ✅ | `/reviewer/submissions/6` loaded. |
| Approve | ✅ | Ledger #10 created; verdict tag changed to approved. |
| Reject | ✅ | Ledger #11 created; verdict tag changed to rejected. |
| Ledger history | ✅ | AI #9 + reviewer #10/#11 entries visible together. |
| Verdict filter | ⚠️ | Filter UI works, but default queue/status semantics need repair first. |
| Owner on reviewer route | ✅ | Returned forbidden. |

## Track D: M5 Integration Smoke

| Check | Result | Notes |
|-------|--------|-------|
| Trusted Export card visible | ✅ | Owner task detail showed Trusted Export card. |
| Export twice | ⚠️ | Snapshots #3/#4 generated and matched, but with `0` submissions due status mismatch. |
| Diff equal modal | ✅ | Green banner, three hash rows all matched, ten file-level matches all green. |
| AI ledger entry visible | ✅ | Ledger #9 `ai_field_finding` visible in reviewer detail. |
| Reviewer + AI entries coexist | ✅ | Ledger #9, #10, and #11 coexist on the same fact stream. |
| DB ledger shape | ✅ | AI row has `actor_type=ai`, `actor_id=NULL`, and `ai_call_id=4`; reviewer rows have `actor_type=reviewer`. |
| Current verdict source | ✅ | UI showed derived verdict from latest ledger entry. |
| Owner submitted-records table | ⚠️ | Shows raw `under_ai_review` status. |
| Export evidence quality | ⚠️ | Reproducibility is valid but over an empty submission set; fix lifecycle first, then re-smoke with non-zero records. |

## Track E: Boundary and Error Probes

| Probe | Result | Notes |
|-------|--------|-------|
| No-auth API access | ✅ | `/api/tasks/18` returned 401. |
| Owner on reviewer API route | ✅ | Returned 403. |
| Labeler on owner task API route | ✅ | Returned 403. |
| Invalid JSONL upload | ✅ | Returned 400 `INVALID_DATASET_FILE` with line number. |
| Ledger pagination | ✅ | `page=1&size=2` returned total 3 and 2 items. |
| Reviewer queue with `under_ai_review` | ✅ | Returned total 4 and included #6, confirming status mismatch root cause. |
| Empty verdict filter | ✅ | Empty state rendered for approved filter after #6 was rejected. |
| Duplicate claim | ⚠️ | Same labeler could claim the same task again as session #10; product decision needed. |
| Session interruption/resume | ⚠️ | Not fully executed; duplicate claim behavior suggests this needs a dedicated audit. |
| AI provider failure | ⚠️ | Not executed in P0; schedule under robustness hardening. |
| Export failure with MinIO down | ⚠️ | Not executed in P0; schedule under robustness hardening. |
| Browser back navigation | ⚠️ | Not fully executed. |
| Expired token | ⚠️ | Not executed. |
| Same-name schema field | ⚠️ | Not executed. |
| Large schema / large task | ⚠️ | Not executed; schedule under performance baseline. |

## 5 个关键观察

1. **Submission lifecycle semantics are the next critical engineering decision.** One status mismatch explains the reviewer default queue miss, the empty Trusted Export records, and raw status display.
2. **The four-highlight evidence chain is still structurally sound.** Schema version binding, AI provenance/idempotency, ledger append/derived verdict, Trusted Export hashing, diff modal, and AI/reviewer ledger coexistence all work mechanically.
3. **M6-P1 should be a bug-fix sprint, not cost/performance.** Cost/performance can wait until the core default reviewer/export path is semantically aligned.
4. **The UI still has owner setup gaps.** Task creation and schema creation should be made self-service and validation-safe before deeper operational hardening.
5. **Boundary/failure testing needs a formal hardening track.** Invalid upload, RBAC, pagination passed; provider failure, export failure, expired token, and large data sets remain intentionally unproven.

## M6 Engineering Hardening 主线 v2

Audit data changes the M6+ order. The status/default-flow mismatch is too central to defer.

### M6-P0: ✅ Baseline Lock + Full Smoke Audit + Strategy

- Baseline commit/tag/branch established.
- Full smoke audit completed with honest pass/warning/fail accounting.
- Strategy adjusted from audit data.

### M6-P1: Submission Lifecycle + Default Flow Repair

- Scope:
  - Decide and document legal submission statuses.
  - Align `SessionService.submit`, reviewer queue defaults, Trusted Export collection, and UI status labels.
  - Fix task creation `deadlineAt` null handling.
  - Add regression tests for:
    - newly submitted row appears in reviewer default queue,
    - Trusted Export over a normal task includes non-zero submissions,
    - create task missing deadline does not 500.
- Budget: about 500 functional lines plus tests.
- Completion criteria:
  - Full Track A/C/D default flow works without API workarounds for the fixed paths.
  - Existing 339+75 tests still pass, plus new regression tests.
- Estimated duration: 5-7 days.

### M6-P2: Owner Setup UX Repair

- Scope:
  - Make schema creation for a new task discoverable.
  - Harden task creation date validation.
  - Normalize/localize submitted-record status labels.
  - Decide duplicate claim semantics.
- Budget: about 400-500 functional lines plus typecheck/browser smoke.
- Estimated duration: 5-7 days.

### M6-P3: Cost and Performance Baseline

- Scope:
  - Measure idempotency hit ratio.
  - Replace fixed AI cost estimate with provider usage parsing if available.
  - Add large-schema and large-task export smoke fixtures.
  - Establish baseline metrics before optimization.
- Gated by M6-P1 because export performance is meaningless while normal submissions are excluded.
- Estimated duration: 7-10 days.

### M6-P4: Robustness and Failure-Mode Hardening

- Scope:
  - AI provider timeout/401/invalid JSON behavior.
  - MinIO-down export behavior and SQL/object-storage residue policy.
  - Retry/backoff/circuit-breaker decision.
  - Expired-token UX.
- Estimated duration: 10-14 days.

### M6-P5: Final Regression + Operational Verification

- Scope:
  - Re-run full M6 smoke audit.
  - Verify protected endpoint script, compile/test/typecheck/build.
  - Produce a short hardening acceptance checklist.
- Estimated duration: 3-5 days.

## M6-P1 元问题草稿

1. Should normal submit write `submitted` or `under_ai_review`?
2. If `under_ai_review` remains valid, should reviewer default queue include it by default?
3. Which submission statuses should Trusted Export include for trusted training data?
4. Should approved/rejected submissions be exportable, or only pending submitted work?
5. Does AI review transition submission status, or only append AI facts?
6. What are the legal submission statuses and which service owns transitions?
7. Should owner submitted-records display status codes directly or map to user-facing labels?
8. Is `deadlineAt` required by product policy, or optional by contract?
9. Should one labeler be able to claim multiple dataset items from the same task?
10. Which regression tests become non-negotiable before cost/performance hardening begins?

## M6-P0 裁决

M6-P1 is **not** Cost/Performance Baseline. It is **Submission Lifecycle + Default Flow Repair**.

Reason: audit exposed a central semantic mismatch that affects reviewer queue defaults and Trusted Export data inclusion. Optimizing cost/performance before repairing this would measure the wrong system.

## Appendix: M6-P1 Resolution Log

M6-P1 resolves the two audit bugs that were promoted into the submission lifecycle repair sprint.

### Resolved

- **Bug #001: submission lifecycle status mismatch** — resolved by V9 default normalization, `SessionService.submit` writing `submitted`, and real-submit regressions for reviewer queue and Trusted Export.
- **Bug #002: task create missing `deadlineAt` could reach null dereference** — resolved by OpenAPI `0.9.1` required `deadlineAt`, generated backend/frontend required types, and validation contract tests.
- **Polish #003: Owner submitted-records table showed raw `under_ai_review`** — resolved by user-facing status labels, with legacy `under_ai_review` mapped to the same submitted label during transition.

### Deferred To M6-P2

- **Bug #003: schema creation discoverability** — Owner setup UX should make schema creation reachable from the task setup path.
- **Polish #001: login autofill/manual credential friction** — keep as setup UX polish.
- **Polish #002: owner task created-time empty display** — normalize table copy/formatting.
- **Product Boundary #001: duplicate claim semantics** — M6-P0.5裁决 keeps claim semantics item-scoped; M6-P2 should clarify copy or UX affordances.

### Deferred To M6-P4

- **Product Boundary #002: destructive/failure probes** — provider failure, export failure with MinIO down, expired token, browser back navigation, same-name schema fields, and large-data stress remain robustness/performance work.

### Evidence Follow-Up

The M5 Trusted Export browser evidence remains structurally valid, but M6-P1 makes the real labeler submit path populate the `submitted` scope. M6-P2 or a final regression pass should refresh the Trusted Export screenshot with nonzero records.
