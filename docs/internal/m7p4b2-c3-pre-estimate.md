# M7-P4b2 C3 Pre-Estimate

## Goal

Implement the owner AI review rule version history and publish controls. C3 adds a frontend list query for the C1.5 `listAiReviewRules` endpoint, renders rule versions with backend-provided `isCurrent`, and publishes draft rules through the existing C1 publish mutation.

## Frozen Baseline

- OpenAPI MD5: `1acd96fb6c0fd0e7b084245d8ae3fa76`
- Frontend Vitest: `141`
- Migrations: `17`
- humanpending: `153`
- Generated churn: `0`

C3 must leave these anchors unchanged except frontend test count increasing.

## Scope Boundary

C3 is a read/publish UI cluster. It consumes existing backend surfaces and generated types.

In scope:

- `useListAiReviewRulesQuery(taskId)` frontend query wrapper.
- Version history list in the AI review rule editor area.
- Current marker sourced only from `AiReviewRule.isCurrent`.
- Publish action through `usePublishAiReviewRuleMutation`.
- List invalidation after publish.

Out of scope:

- backend, OpenAPI, generated types, migrations
- detail endpoint
- task contract changes
- save form validation changes
- editing or updating historical rule content
- optimistic current-state mutation

STOP condition: if history display requires a field not present in `listAiReviewRules`, stop and report. Do not add or mock a detail endpoint.

## Existing Interfaces

C3 consumes:

```ts
usePublishAiReviewRuleMutation(): UseMutationResult<
  AiReviewRule,
  AiReviewRuleMutationFailure,
  { ruleId: number }
>
```

Generated list operation:

```ts
operations['listAiReviewRules']
```

Expected list result shape:

```ts
AiReviewRule[] // includes isCurrent: boolean
```

Current-state semantics:

```ts
rule.isCurrent === true
```

is the only frontend current marker. Status is displayed as status only.

## File Plan

### `apps/web/src/features/ai-review-rule/useListAiReviewRulesQuery.ts` — create

Estimated LOC: `55`

Responsibilities:

- Wrap generated `GET /ai-review/rules?taskId={taskId}` with TanStack Query.
- Query key:
  ```ts
  ['ai-review-rules', taskId]
  ```
- Return `AiReviewRule[]`.
- Disable the query when `taskId` is missing or invalid.
- Map expected 400/401/403/404 responses to a typed failure class or reuse the C1 `AiReviewRuleMutationFailure` family if it fits without widening semantics.
- Export a small query-key helper so publish success can invalidate the same key.

No publish logic belongs in this file.

### `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.tsx` — create

Estimated LOC: `170`

Responsibilities:

- Props:
  ```ts
  type AiReviewRuleHistoryPanelProps = {
    taskId: number;
  };
  ```
- Use `useListAiReviewRulesQuery(taskId)`.
- Use `usePublishAiReviewRuleMutation()`.
- Render loading, error, empty, and list states.
- Render each rule with:
  - `v{versionNo}`
  - `status`
  - `createdAt`
  - current marker when `isCurrent === true`
  - publish action for draft rows that are not current
- Publish flow:
  1. call publish mutation with `{ ruleId }`
  2. on success invalidate the task list query
  3. display neutral success copy
  4. do not manually edit `isCurrent`
- Failure flow:
  - display `AiReviewRuleMutationFailure.userMessage`
  - leave list state as-is until refetch

The component must not compute current from `status`.

### `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.test.tsx` — create

Estimated LOC: `170`

Coverage:

- List rendering:
  - shows versions in ASC order as provided by the hook
  - shows status and createdAt text
  - shows current marker only for `isCurrent: true`
- Status/current separation:
  - a `published` rule with `isCurrent:false` must not show the current marker
  - a `draft` rule with `isCurrent:true` should show current marker if backend says so, proving frontend does not infer from status
- Publish flow:
  - publish button calls publish mutation with the selected rule id
  - successful publish invalidates the list query key
  - failed publish displays `userMessage`
- Scope guards:
  - no detail endpoint dependency
  - no save validation changes

Use the existing Vitest + React server-render/mock style. Do not introduce new testing dependencies.

### `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx` — modify

Estimated LOC: `55`

Responsibilities:

- Mount `AiReviewRuleHistoryPanel` as a separate section in the existing SideSheet.
- Keep C2 form state, validation, and save flow unchanged.
- Keep append-only save copy neutral.
- Do not pass list data into the form as prefill.

### `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx` — modify

Estimated LOC: `40`

Responsibilities:

- Update SSR smoke to expect a history section mount point.
- Preserve C2 assertions:
  - no publish/history/current wording in the save form itself can be replaced by assertions that publish/current are isolated in the history panel, not the form submit behavior
  - validation messages remain unchanged
  - save still uses `useSaveAiReviewRuleMutation`
- Add a guard that the editor still renders without needing preloaded list data.

### `apps/web/src/app/styles.css` — modify

Estimated LOC: `45`

Responsibilities:

- Add scoped classes for the history section, version rows, status/current badges, and publish action alignment.
- Add responsive behavior for 1024px layouts.
- Avoid global layout changes.

## List Query Design

Query key:

```ts
export const aiReviewRulesQueryKey = (taskId: number) => ['ai-review-rules', taskId] as const;
```

Query function:

```ts
client.GET('/ai-review/rules', {
  params: { query: { taskId } },
})
```

Return value:

```ts
AiReviewRule[]
```

Expected failure handling:

- 400: invalid or missing task id
- 401/403: auth failures
- 404: cross-owner or missing task hidden by backend
- fallback: generic list failure message

The hook should be a read hook only. Publishing remains in `usePublishAiReviewRuleMutation`.

## UI Mapping

| DTO field | UI use |
|---|---|
| `versionNo` | Display as `v{versionNo}`. |
| `status` | Display as status label only. |
| `createdAt` | Display creation timestamp; use existing date formatting if available, otherwise a compact raw/locale string. |
| `isCurrent` | Current marker. This is the only current-state source. |
| `id` | Publish mutation variable `{ ruleId: id }`. |
| `promptTemplate` | Optional short preview only if layout remains clean; not required for C3. |

Recommended display order: preserve backend ASC order. Rationale: it aligns with schema-version history and avoids client-side reordering. If implementation decides latest-first is better for UI ergonomics, reverse only in the component and document that choice in the implementation report.

## Publish Flow

1. Owner clicks publish on a listed draft rule.
2. Component calls:
   ```ts
   publishRule.mutateAsync({ ruleId: rule.id })
   ```
3. On success:
   ```ts
   queryClient.invalidateQueries({ queryKey: aiReviewRulesQueryKey(taskId) })
   ```
4. Success copy should be neutral, e.g. `已发布 AI 审核规则版本 v{versionNo}`.
5. Current marker updates only after the list refetches.

No optimistic local mutation of `isCurrent` is allowed.

## Current Marker Rules

Frontend must render current only from:

```ts
rule.isCurrent
```

Forbidden:

```ts
rule.status === 'published'
```

for current-state inference.

Tests must include a published non-current row to lock this behavior.

## Three-Viewport Strategy

- 1440: history panel stacks below the form inside the SideSheet with full metadata rows.
- 1280: rows keep actions aligned; long labels should wrap instead of overflowing.
- 1024: rows can stack metadata and publish action vertically; no horizontal scrolling.

If browser verification is unavailable, D-port is acceptable only with:

- scoped CSS diff only
- no global layout CSS changes
- component/server-render tests proving list/current/publish controls render

## Risks + STOP Conditions

| Risk | Handling |
|---|---|
| List lacks a field required by the history UI | STOP; do not add detail endpoint. |
| Multiple published rows confuse current display | Use `isCurrent` only; include test coverage. |
| Publish success does not update current marker | Invalidate list query; do not hand-edit local rows. |
| Query hook error mapping drifts from mutation failures | Keep user-facing failure mapping small and explicit. |
| History makes the editor too dense | Stack sections and keep prompt preview optional. |

## Budget

Hand-authored budget excludes generated files.

| Budget item | Soft cap | Hard cap |
|---|---:|---:|
| Hand-authored files | 7 | 10 |
| Hand-authored net LOC | 550 | 750 |
| Generated files | 0 | 0 |
| Generated net LOC | 0 | 0 |

Estimated hand-authored LOC:

| File | Estimated LOC |
|---|---:|
| `apps/web/src/features/ai-review-rule/useListAiReviewRulesQuery.ts` | 55 |
| `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.tsx` | 170 |
| `apps/web/src/features/ai-review-rule/AiReviewRuleHistoryPanel.test.tsx` | 170 |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx` | 55 |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx` | 40 |
| `apps/web/src/app/styles.css` | 45 |

Estimated total: `535`, below the `550` soft cap.

Caps match `m7p4b2-c3-scope-budget.md`.

## Verification Plan

Implementation should run:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \[" humanpending.md
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
git diff --check
git diff --stat
```

Expected anchors:

- MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`
- migrations remain `17`
- humanpending remains `153`
- frontend test count increases from `141`
- generated churn remains `0`

