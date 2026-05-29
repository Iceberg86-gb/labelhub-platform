# M7-P4b2 C3 Scope + Budget

## Status

C3 implements the owner-facing AI review rule version history and publish UI. It is the first P4b2 frontend cluster that consumes the C1.5 `listAiReviewRules` endpoint and the C1 publish hook.

Frozen before C3:

- OpenAPI MD5: `1acd96fb6c0fd0e7b084245d8ae3fa76`
- Frontend Vitest: `141`
- Migrations: `17`
- humanpending: `153`
- Generated churn: `0`

Expected after C3:

- OpenAPI MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`.
- Frontend Vitest increases with list-query, history UI, and publish-flow tests.
- Migrations remain `17`.
- humanpending remains `153`.
- Generated churn remains `0`.

## Scope Anchor

C3 consumes already-generated frontend types and existing backend endpoints. It does not modify the OpenAPI contract, regenerate `schema.d.ts`, add a detail endpoint, or change backend behavior.

C3 owns the full read/publish UI that C2 explicitly deferred:

- `listAiReviewRules` frontend query wrapper
- version history list
- `isCurrent` display
- publish action
- list invalidation after publish

If implementation discovers that the list response lacks a required field and a rule detail endpoint is needed, stop and report. Do not add a backend endpoint, mock a detail response, or silently expand the contract.

## In Scope

| Item | Scope | Notes |
|---|---|---|
| List query hook | In | Add `useListAiReviewRulesQuery(taskId)` around generated `listAiReviewRules`. |
| Version history UI | In | Show rule versions with version number, status, created time, and current marker. |
| Current marker | In | Use backend `AiReviewRule.isCurrent` only. |
| Publish action | In | Use existing `usePublishAiReviewRuleMutation`; invalidate list after success. |
| Editor integration | In | Extend the C2 editor area with version history and publish controls. |
| Failure display | In | Display `AiReviewRuleMutationFailure.userMessage` for expected publish/list failures. |
| Three viewport behavior | In | 1440 / 1280 / 1024 list and publish controls must not overflow or break layout. |

## Out Of Scope

| Item | Scope | Reason |
|---|---|---|
| Backend changes | Out | C1.5 already delivered the read contract. |
| OpenAPI or generated type regeneration | Out | C3 consumes existing generated types. |
| Rule detail endpoint | Out | Deferred by read-contract research. |
| Task contract changes | Out | Current state comes from `AiReviewRule.isCurrent`, not task DTO changes. |
| Editing historical rule content | Out | Append-only save remains C2 behavior. |
| Changing save validation | Out | C2 owns form validation and save mapping. |
| Optimistic current-state mutation | Out | Publish must invalidate/refetch list instead of hand-editing `isCurrent`. |

## Existing Interfaces

C3 consumes existing C1/C1.5 surfaces only:

- `apps/web/src/features/ai-review-rule/usePublishAiReviewRuleMutation.ts`
  - `usePublishAiReviewRuleMutation()`
  - `publishAiReviewRule({ ruleId }): Promise<AiReviewRule>`
  - `AiReviewRuleMutationFailure`
- `apps/web/src/features/ai-review-rule/aiReviewRuleTypes.ts`
  - `AiReviewRule`
  - `AiReviewRuleRequest`
  - `AiReviewRuleStatus`
- Generated OpenAPI operation:
  - `operations["listAiReviewRules"]`
  - `AiReviewRule.isCurrent: boolean`
- Existing C2 editor:
  - `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx`

## Current-State Semantics

`isCurrent` is the only current-state source in C3. It is computed by the backend from `tasks.current_ai_review_rule_id`.

Frontend must not infer current state from `status`. A task can have multiple `published` rule rows, but only one row can have `isCurrent=true`; when the task pointer is null, all rows can be false.

## Sorting Semantics

The list endpoint returns versions ordered by `versionNo` ASC. C3 should preserve ASC unless the UI explicitly chooses "latest first".

If the UI chooses latest-first display, it must reverse client-side and document the choice. It must not ask the backend to change the C1.5 ASC query.

## Publish Semantics

Publishing a rule changes task-level current state on the backend. After a successful publish:

1. invalidate the list query for the task
2. refetch or allow TanStack Query to refetch
3. render the returned list with backend-computed `isCurrent`

C3 must not optimistically set one item to `isCurrent=true` and the rest false in local state.

## Expected File Set

Create:

- `apps/web/src/features/ai-review-rule/useListAiReviewRulesQuery.ts`
- `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.tsx`
- `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.test.tsx`

Modify:

- `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx`
- `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx`
- `apps/web/src/app/styles.css`

No generated files are expected to change.

## Caps

Caps are hand-authored frontend files and LOC only. Generated churn is tracked separately and expected to remain zero.

| Cap | Value | Meaning |
|---|---:|---|
| Soft file-count cap | `7` files | Stop and report if implementation naturally needs more than 7 touched files. |
| Hard file-count cap | `10` files | True ceiling for C3 without explicit adjudication. |
| Soft LOC cap | `550` net lines | Stop and report if implementation naturally exceeds this. |
| Hard LOC cap | `750` net lines | True ceiling for C3. |
| Generated churn | `0` | Do not regenerate OpenAPI types in C3. |

Estimated hand-authored files:

| File | Action | Estimated LOC | Purpose |
|---|---|---:|---|
| `apps/web/src/features/ai-review-rule/useListAiReviewRulesQuery.ts` | Create | 55 | TanStack Query wrapper for generated list operation and typed expected failures. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.tsx` | Create | 170 | Version history list, current marker, publish controls, loading/error/empty states. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.test.tsx` | Create | 170 | List rendering, current marker, publish invalidation, no status-derived current, scope guards. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx` | Modify | 55 | Mount history panel and keep C2 save form intact. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx` | Modify | 40 | Extend render/scope tests for history panel mount without altering save tests. |
| `apps/web/src/app/styles.css` | Modify | 45 | Scoped history-list and responsive publish-control styles. |

Estimated total hand-authored LOC: `535`, below the `550` soft cap. The hard cap remains `750`.

## Three-Viewport Strategy

- 1440: editor drawer can show the form and history as stacked sections with comfortable spacing.
- 1280: history rows should keep version/status/current/publish actions readable without horizontal overflow.
- 1024: rows can stack metadata and actions vertically; long prompt preview is not required in C3, reducing overflow risk.

If browser verification is unavailable, use D-port: scoped CSS only, no global layout changes, and component/server-render tests cover history rendering, current marker, and publish controls.

## Guardrails

- Do not modify OpenAPI, generated types, backend, migrations, or humanpending.
- Do not consume or invent a rule detail endpoint.
- Do not change C2 save validation or request mapping.
- Do not infer current state from `status`.
- Do not optimistically hand-edit `isCurrent`.
- Do not ask backend to change ASC sorting.
- Do not break append-only semantics.

## Frozen Out-Of-Scope Files

C3 must not modify:

- `packages/contracts/openapi/labelhub.yaml`
- `apps/web/src/shared/api/generated/schema.d.ts`
- Any backend Java or migration file
- `humanpending.md`
- C1.5 backend read-contract implementation
- P3a/P3b/P4a logic unrelated to owner AI review rule UI
