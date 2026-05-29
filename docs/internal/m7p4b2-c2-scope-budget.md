# M7-P4b2 C2 Scope + Budget

## Status

C2 implements the owner-facing AI review rule editor form body and wires it to the C1 save hook. It is a pure frontend cluster under the C1.5 OpenAPI anchor.

Frozen before C2:

- OpenAPI MD5: `1acd96fb6c0fd0e7b084245d8ae3fa76`
- Frontend Vitest: `137`
- Migrations: `17`
- humanpending: `153`
- Backend full test status: targeted C1.5 tests passed; full backend remains D-port due sandbox/MySQL/socket errors.

Expected after C2:

- OpenAPI MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`.
- Frontend Vitest increases with editor form and validation tests.
- Migrations remain `17`.
- humanpending remains `153`.
- Generated churn remains `0`.

## Scope Anchor

C2 does **not** consume the C1.5 list endpoint. The editor is append-only: fill the form, save a new draft rule, and immediately show the returned `AiReviewRule`. Version history, current-rule display, publish controls, `isCurrent` display, and list-query consumption are all deferred to C3.

If implementation discovers that the form must read existing rules, current rule state, or version history to complete C2, stop and report. Do not add a list query, mock history, or move C3 behavior into C2.

## In Scope

| Item | Scope | Notes |
|---|---|---|
| Editor container | In | Replace the C1 placeholder toast with a drawer or modal opened from `AiReviewRuleEntryCard`. |
| Prompt field | In | Multiline owner business prompt textarea mapped to `AiReviewRuleRequest.promptTemplate`. |
| Dimensions field | In | Dynamic list mapped to `AiReviewRuleRequest.dimensions`; supports add, edit, remove. |
| Threshold field | In | Numeric input mapped to `AiReviewRuleRequest.threshold`. |
| Client validation | In | Replicate backend boundary for UX; backend remains authoritative. |
| Save submit | In | Call `useSaveAiReviewRuleMutation`; use returned `AiReviewRule` for immediate draft-version feedback. |
| Failure display | In | Display `AiReviewRuleMutationFailure.userMessage` for expected backend 4xx failures. |
| Three viewport behavior | In | 1440 / 1280 / 1024 editor usability and no layout breakage. |

## Out Of Scope

| Item | Scope | Reason |
|---|---|---|
| Publish UI or hook usage | Out | C3. |
| Version history list | Out | C3 consumes `listAiReviewRules`. |
| Current active rule display | Out | Requires list/isCurrent and belongs to C3. |
| Prefilling from an existing rule | Out | C2 is append-only new draft creation. |
| Backend changes | Out | C2 is frontend-only. |
| OpenAPI or generated type regeneration | Out | C1.5 already generated the needed types. |
| Task contract changes | Out | Not needed for C2 form. |

## Existing Interfaces

C2 consumes existing C1/C1.5 surfaces only:

- `apps/web/src/features/ai-review-rule/useSaveAiReviewRuleMutation.ts`
  - `useSaveAiReviewRuleMutation()`
  - `saveAiReviewRule(request: AiReviewRuleRequest): Promise<AiReviewRule>`
  - `AiReviewRuleMutationFailure`
- `apps/web/src/features/ai-review-rule/aiReviewRuleTypes.ts`
  - `AiReviewRule`
  - `AiReviewRuleRequest`
  - `AiReviewRuleStatus`
- `apps/web/src/features/ai-review-rule/AiReviewRuleEntryCard.tsx`
  - `onOpenEditor`
- `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx`
  - Current placeholder: `openAiReviewRuleEditor`

## Validation Boundary

Client validation mirrors backend messages and rules for UX only:

| Field | Rule | Client message |
|---|---|---|
| `promptTemplate` | Trimmed value must be non-empty. | `Prompt 模板不能为空` |
| `dimensions` | At least one dimension after trimming. | `评分维度不能为空` |
| `dimensions[]` | Every item must trim to non-empty. | `评分维度不能为空` |
| `dimensions[]` | Trimmed values must be unique. | `评分维度不能重复` |
| `threshold` | Numeric value must be within `[0, 1]`, inclusive. | `阈值必须在 0 到 1 之间` |

Backend 4xx still wins. If the client passes validation but the server returns an error, show `AiReviewRuleMutationFailure.userMessage`.

## Expected File Set

Create:

- `apps/web/src/features/ai-review-rule/aiReviewRuleFormModel.ts`
- `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx`
- `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx`

Modify:

- `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx`
- `apps/web/src/app/styles.css`

No generated files are expected to change.

## Caps

Caps are hand-authored frontend files and LOC only. Generated churn is tracked separately and expected to remain zero.

| Cap | Value | Meaning |
|---|---:|---|
| Soft file-count cap | `6` files | Stop and report if implementation naturally needs more than 6 touched files. |
| Hard file-count cap | `8` files | True ceiling for C2 without explicit adjudication. |
| Soft LOC cap | `500` net lines | Stop and report if implementation naturally exceeds this. |
| Hard LOC cap | `700` net lines | True ceiling for C2. |
| Generated churn | `0` | Do not regenerate OpenAPI types in C2. |

Estimated hand-authored files:

| File | Action | Estimated LOC | Purpose |
|---|---|---:|---|
| `apps/web/src/features/ai-review-rule/aiReviewRuleFormModel.ts` | Create | 100 | Form state, normalization, request building, validation, and message constants. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.tsx` | Create | 165 | Drawer/modal form UI, dynamic dimensions, submit flow, success/failure feedback. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEditorDrawer.test.tsx` | Create | 145 | Validation, submit mapping, backend-error display, and no-list/no-publish guard coverage. |
| `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx` | Modify | 30 | Replace placeholder toast with editor open state and drawer mount. |
| `apps/web/src/app/styles.css` | Modify | 30 | Scoped editor form spacing and responsive behavior. |

Estimated total hand-authored LOC: `470`, below the `500` soft cap. The hard cap remains `700`.

## Three-Viewport Strategy

- 1440: drawer may use a comfortable fixed width; dimensions list and threshold stay in one vertical flow.
- 1280: same drawer width or max-width; labels and controls must not overflow.
- 1024: drawer/modal should remain usable with vertical stacking; textarea and dimension rows must not force horizontal scroll.

If browser verification is unavailable, use D-port: scoped CSS only, no global layout changes, and server-render/component tests cover form rendering.

## Guardrails

- Save is append-only: the form creates a new draft rule version and does not edit an existing draft in place.
- Active state is not shown or inferred in C2.
- `isCurrent` from the returned DTO must not be used for "active" UI in this cluster.
- The editor is owner business prompt/rule configuration; ADR-011 provider prompt remains out of scope.
- Do not consume `listAiReviewRules`.
- Do not use `publishAiReviewRule`.

## Frozen Out-Of-Scope Files

C2 must not modify:

- `packages/contracts/openapi/labelhub.yaml`
- `apps/web/src/shared/api/generated/schema.d.ts`
- Any backend Java or migration file
- `humanpending.md`
- P3a/P3b/P4a logic unrelated to the owner task detail editor
