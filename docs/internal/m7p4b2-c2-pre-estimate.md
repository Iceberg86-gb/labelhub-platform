# M7-P4b2 C2 Pre-Estimate

## Goal

Implement the owner AI review rule editor form body. C2 replaces the C1 placeholder with a real form, validates inputs on the client, calls the existing save mutation, and shows the returned draft rule version. It remains frontend-only and does not consume the C1.5 list endpoint.

## Frozen Baseline

- OpenAPI MD5: `1acd96fb6c0fd0e7b084245d8ae3fa76`
- Frontend Vitest: `137`
- Migrations: `17`
- humanpending: `153`
- Generated churn: `0`

C2 must leave these anchors unchanged except frontend test count increasing.

## Scope Boundary

C2 is a write-form cluster, not a history/current-state cluster.

In scope:

- Open editor container from `AiReviewRuleEntryCard`.
- Capture prompt template, dimensions, and threshold.
- Validate client-side using the backend boundary and Chinese messages.
- Submit through `useSaveAiReviewRuleMutation`.
- Display success from the returned `AiReviewRule`.
- Display backend 4xx via `AiReviewRuleMutationFailure.userMessage`.

Out of scope:

- `listAiReviewRules` consumption.
- version history.
- current active rule display.
- publish action.
- prefill from existing rules.
- backend/OpenAPI/generated changes.

STOP condition: if implementation requires existing rule data or version history to complete the form, stop and report. Do not add a list query, mock current state, or silently expand into C3.

## Existing Interfaces

C2 consumes:

```ts
useSaveAiReviewRuleMutation(): UseMutationResult<
  AiReviewRule,
  AiReviewRuleMutationFailure,
  AiReviewRuleRequest
>
```

`AiReviewRuleRequest` fields:

```ts
{
  taskId: number;
  promptTemplate: string;
  dimensions: string[];
  threshold: number;
}
```

`AiReviewRule` returned from save includes `versionNo`, `status`, `isCurrent`, `promptVersionId`, and timestamps. C2 may use `versionNo` and `status` for neutral success copy, but must not use `isCurrent` to show active state.

## File Plan

### `apps/web/src/features/ai-review-rule/aiReviewRuleFormModel.ts` — create

Estimated LOC: `100`

Responsibilities:

- Define editor state shape:
  ```ts
  type AiReviewRuleFormState = {
    promptTemplate: string;
    dimensions: string[];
    threshold: string;
  };
  ```
- Provide default state.
- Normalize dimensions with trim.
- Build `AiReviewRuleRequest` from `taskId` + valid state.
- Validate:
  - prompt non-empty
  - dimensions non-empty after trim
  - every dimension non-empty
  - no duplicate trimmed dimension
  - threshold parses to number in `[0, 1]`
- Export backend-aligned message constants:
  - `Prompt 模板不能为空`
  - `评分维度不能为空`
  - `评分维度不能重复`
  - `阈值必须在 0 到 1 之间`

This file keeps validation pure and easy to test without mounting the drawer.

### `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx` — create

Estimated LOC: `165`

Responsibilities:

- Render a drawer or modal editor. Preferred shape: Semi `Modal` or `SideSheet` depending on existing dependency availability; implementation should pick the existing project pattern.
- Props:
  ```ts
  type AiReviewRuleEditorDrawerProps = {
    taskId: number;
    open: boolean;
    onClose: () => void;
  };
  ```
- Render:
  - prompt textarea
  - dimensions dynamic list
  - add dimension button
  - delete dimension button
  - threshold numeric input
  - save/cancel controls
- On submit:
  1. validate pure state
  2. if invalid, show inline errors and do not call save
  3. call `useSaveAiReviewRuleMutation`
  4. success: toast or inline success `已创建 AI 审核规则版本 v{versionNo}` and reset/close
  5. failure: show `error.userMessage`
- Must not call `usePublishAiReviewRuleMutation`.
- Must not call `listAiReviewRules`.

### `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx` — create

Estimated LOC: `145`

Coverage:

- Pure validation:
  - blank prompt -> `Prompt 模板不能为空`
  - empty dimensions / blank item -> `评分维度不能为空`
  - duplicate trimmed dimensions -> `评分维度不能重复`
  - threshold below 0 / above 1 / non-number -> `阈值必须在 0 到 1 之间`
  - boundary thresholds `0` and `1` are accepted
- Request mapping:
  - trims dimensions
  - passes `taskId`, `promptTemplate`, `dimensions`, `threshold`
- Submit behavior:
  - valid form calls save mutation
  - successful save can show returned version number
  - backend `AiReviewRuleMutationFailure.userMessage` is displayed
- Scope guard:
  - no test imports or calls `listAiReviewRules`
  - no publish control rendered

Use the existing frontend test style from C1: `react-dom/server` smoke or existing Vitest mocks. Do not introduce a new test dependency.

### `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx` — modify

Estimated LOC: `30`

Responsibilities:

- Replace `openAiReviewRuleEditor` toast placeholder with local editor open state.
- Mount `AiReviewRuleEditorDrawer` with `taskId={task.id}`.
- Keep owner-only placement unchanged.
- Do not read existing rules.
- Do not render active/current state.

### `apps/web/src/app/styles.css` — modify

Estimated LOC: `30`

Responsibilities:

- Add scoped classes for editor body spacing, dimension rows, validation text, and responsive layout.
- Avoid global layout changes.
- Do not alter task detail grid behavior except what the editor needs.

## Field Mapping

| UI field | Request field | Notes |
|---|---|---|
| Prompt textarea | `promptTemplate` | Preserve owner text except validation trims for emptiness. |
| Dimension rows | `dimensions` | Submit trimmed non-empty strings. Duplicate detection uses trimmed values. |
| Threshold input | `threshold` | Parse to number; accept `0` and `1`; reject non-finite values and outside range. |
| Task id | `taskId` | From `OwnerTaskDetailPage` loaded `task.id`; do not request it from the user. |

## Validation Rules

Client validation mirrors backend behavior for faster feedback:

1. `promptTemplate.trim().length === 0` -> `Prompt 模板不能为空`
2. no dimensions after trim -> `评分维度不能为空`
3. any dimension trims to empty -> `评分维度不能为空`
4. duplicate trimmed dimension -> `评分维度不能重复`
5. threshold missing, non-numeric, non-finite, `< 0`, or `> 1` -> `阈值必须在 0 到 1 之间`

Backend validation remains authoritative. If backend returns a 400 with a message, show the backend message via `AiReviewRuleMutationFailure.userMessage`.

## Submit Flow

1. Owner clicks `配置规则`.
2. Editor opens with empty state:
   - prompt empty
   - dimensions one empty row or no rows plus add button, implementation chooses the cleaner UX
   - threshold default may be empty or `0.8`; if defaulted, the UI must make it editable
3. Owner submits.
4. Client validates.
5. Save mutation posts `AiReviewRuleRequest`.
6. Success:
   - Show neutral success copy, e.g. `已创建 AI 审核规则版本 v{versionNo}`
   - Reset and close, or reset while staying open; implementation must choose one and test it
   - Do not show it as current/active
7. Failure:
   - Show `AiReviewRuleMutationFailure.userMessage`
   - Keep user input in the form

## Three-Viewport Strategy

Preferred editor container:

- Use a drawer/side sheet if the project already uses Semi drawer-like primitives.
- Use a modal if that is the existing stable pattern and requires less CSS.

Responsive behavior:

- 1440: comfortable width around 560-640px or existing Semi default.
- 1280: same flow, no label/control overflow.
- 1024: vertical layout; dimension rows wrap or keep delete button compact; no horizontal scroll.

If browser verification is unavailable, D-port is acceptable only with:

- scoped CSS diff only
- no global layout CSS changes
- component/server-render tests proving the form renders key fields

## Risks + STOP Conditions

| Risk | Handling |
|---|---|
| Form wants to edit existing draft | STOP. Append-only means C2 creates new drafts only. |
| UI needs current rule / version history to feel complete | STOP. That is C3. |
| Client validation drifts from backend | Keep message constants and pure tests aligned to backend strings. |
| Dynamic dimensions grow component size | Keep state helpers in `aiReviewRuleFormModel.ts`; avoid embedding all logic in JSX. |
| Backend 4xx has field-specific message | Display `userMessage` verbatim. |

## Budget

Hand-authored budget excludes generated files.

| Budget item | Soft cap | Hard cap |
|---|---:|---:|
| Hand-authored files | 6 | 8 |
| Hand-authored net LOC | 500 | 700 |
| Generated files | 0 | 0 |
| Generated net LOC | 0 | 0 |

Estimated hand-authored LOC:

| File | Estimated LOC |
|---|---:|
| `apps/web/src/features/ai-review-rule/aiReviewRuleFormModel.ts` | 100 |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx` | 165 |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx` | 145 |
| `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx` | 30 |
| `apps/web/src/app/styles.css` | 30 |

Estimated total: `470`, below the `500` soft cap. If staying under `500` would require reducing validation or submit coverage, stop and report rather than cutting coverage.

The cap values above exactly match `docs/internal/m7p4b2-c2-scope-budget.md`.

## Verification Plan For Implementation

C2 implementation report should include:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
git diff --stat
git diff --check
```

Expected:

- MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`
- migrations remain `17`
- humanpending remains `153`
- frontend tests increase beyond `137`
- no generated files change
