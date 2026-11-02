# M7-P4b2 C1 Scope Budget: AI Review Rule Entry And Hooks

## 1. Status

This is a pre-estimate gate only. C1 implementation must be frontend-only and
must not touch backend code, OpenAPI, migrations, generated types, or
humanpending.

Frozen anchors:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `b7df19fdb69f8d22b2f0dbdbc845d95d` |
| Backend tests | `540 / 88` |
| Frontend Vitest | `131` |
| Migrations | `17` |
| humanpending | `153` |

Expected at C1 end:

- OpenAPI MD5 remains `b7df19fdb69f8d22b2f0dbdbc845d95d`.
- Backend tests remain `540 / 88`.
- Migrations remain `17`.
- humanpending remains `153`.
- Generated churn is `0`.

## 2. C1 Intent

C1 prepares the P4b2 frontend surface without implementing the prompt/rule
form. It adds typed mutation hooks for the existing write endpoints and places
an Owner task-detail entry point where the future editor can mount.

Default reading-surface decision for C1: **Option B**. Version history and
existing-rule reading are deferred. C1 must not add or depend on a GET rules
endpoint.

## 3. In Scope

| Item | Scope | Notes |
|---|---|---|
| Save hook | In | Add a typed frontend hook for `POST /ai-review/rules`. |
| Publish hook | In | Add a typed frontend hook for `POST /ai-review/rules/{ruleId}/publish`. |
| Type adapter | In | Re-export/narrow generated `AiReviewRule`, `AiReviewRuleRequest`, and `AiReviewRuleStatus`; no hand-written schema duplication. |
| Task-detail entry | In | Add an Owner-visible AI review rule card/button/placeholder on `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx`. |
| Route/drawer mount point | In | Provide a local open state or callback surface for the future editor, but no form fields. |
| Owner visibility | In | Entry is on Owner task detail only; no labeler/reviewer surface. |

## 4. Out Of Scope

| Item | Scope | Reason |
|---|---|---|
| Prompt textarea | Out | C2 form body. |
| Dimension add/remove UI | Out | C2 form body. |
| Threshold control | Out | C2 form body. |
| Frontend validation replication | Out | C2 form body; C1 only records rules. |
| Publish/version history UI | Out | C3. |
| Reading existing rules | Out | Upstream A/B/C reading-surface decision remains open; C1 must not add GET dependencies. |
| Backend changes | Out | P4b2 C1 is frontend-only. |
| OpenAPI changes / regeneration | Out | Existing generated types already include the write endpoints. |

## 5. Expected File Set

Create:

- `apps/web/src/features/ai-review-rule/aiReviewRuleTypes.ts`
- `apps/web/src/features/ai-review-rule/useSaveAiReviewRuleMutation.ts`
- `apps/web/src/features/ai-review-rule/usePublishAiReviewRuleMutation.ts`
- `apps/web/src/features/ai-review-rule/AiReviewRuleEntryCard.tsx`
- `apps/web/src/features/ai-review-rule/useSaveAiReviewRuleMutation.test.ts`
- `apps/web/src/features/ai-review-rule/usePublishAiReviewRuleMutation.test.ts`
- `apps/web/src/features/ai-review-rule/AiReviewRuleEntryCard.test.tsx`

Modify:

- `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx`

This path was confirmed in the working tree. It exports `OwnerTaskDetailPage`,
uses `useTaskDetailQuery`, and renders the Owner task detail surface with
`className="task-detail-page"`.

No generated files are expected to change.

## 6. Caps

Caps are hand-authored frontend LOC only. Generated churn is tracked
separately and expected to be zero.

| Cap | Value | Meaning |
|---|---:|---|
| Soft file-count cap | `8` files | If implementation needs more than 8 touched files, stop and report before expanding. |
| Hard file-count cap | `10` files | Do not exceed without explicit adjudication. |
| Soft LOC cap | `350` net lines | Stop and report if implementation naturally exceeds this. |
| Hard LOC cap | `500` net lines | True ceiling for C1. |
| Generated churn | `0` | No OpenAPI/type regeneration in C1. |

## 7. Load-Bearing Constraints

### Save Is Append-Only

`POST /ai-review/rules` creates a new draft rule version. There is no
update-in-place. Frontend hooks and entry UI must not assume an editable draft
row can be patched.

### Active Rule Is Task Pointer

Publishing marks a rule published and sets `task.currentAiReviewRuleId`.
Multiple rules for one task may have `status = published`; "active" means the
task pointer, not the status alone. C1 does not display active state, but its
entry design must not encode `status=published` as the active invariant.

### Validation Boundary

C1 only records the future C2 validation contract:

- `promptTemplate` non-empty;
- `dimensions` non-empty;
- each dimension trims to non-empty;
- dimensions must be unique;
- `threshold` is within `[0, 1]`, inclusive;
- error wording must align with backend Chinese messages such as
  `评分维度不能重复` and `阈值必须在 0 到 1 之间`.

C1 does not implement the form validator.

### 4xx Handling

OpenAPI does not enumerate every save/publish 4xx, but the backend can return
validation and not-found errors. Hooks must map 4xx responses as expected
business failures, not as impossible states.

### Reading-Surface STOP Condition

If implementation requires current rule data or version history to complete
the C1 entry point, stop. Do not add a backend GET endpoint, mock history, or
invent read state.

## 8. Three-Viewport Entry Strategy

C1 should add a compact task-detail card that fits the existing owner detail
grid:

- 1440: normal card in the main grid below existing setup/export/submission
  sections.
- 1280: same card width as neighboring task-detail cards, with compact button
  text.
- 1024: single-column stacked card behavior inherited from existing task
  detail layout; button text must not overflow.

C1 should verify via screenshot or D-port if browser seeding is unavailable.
