# M7-P4b2 C1 Pre-Estimate: Frontend Rule Hooks And Task Entry

## 1. Gate Status

This is a docs-only pre-estimate gate. It proposes the C1 implementation shape
for the first P4b2 frontend cluster. It does not change code.

Frozen anchors:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `b7df19fdb69f8d22b2f0dbdbc845d95d` |
| Backend tests | `540 / 88` |
| Frontend Vitest | `131` |
| Migrations | `17` |
| humanpending | `153` |

C1 is anchored to reading-surface **Option B**: no version history and no rule
read endpoint in C1. Save/publish write hooks and the task-detail entry point
must work without reading existing rule lists.

## 2. File Plan

| File | Change | Estimate | Responsibility |
|---|---|---:|---|
| `apps/web/src/features/ai-review-rule/aiReviewRuleTypes.ts` | Create | 12 | Re-export generated `AiReviewRule`, `AiReviewRuleRequest`, and `AiReviewRuleStatus`; no hand-written duplicates. |
| `apps/web/src/features/ai-review-rule/useSaveAiReviewRuleMutation.ts` | Create | 52 | Wrap `apiClient.POST('/ai-review/rules')`, map expected 4xx failures, and return typed `AiReviewRule`. |
| `apps/web/src/features/ai-review-rule/usePublishAiReviewRuleMutation.ts` | Create | 42 | Wrap `apiClient.POST('/ai-review/rules/{ruleId}/publish')`, map expected 4xx failures. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEntryCard.tsx` | Create | 58 | Owner task-detail card/entry placeholder with CTA and future editor mount callback; no prompt/dimension/threshold form. |
| `apps/web/src/pages/owner/OwnerTaskDetailPage.tsx` | Modify | 24 | Import and render entry card on owner task detail. Add local placeholder open callback if needed. |
| `apps/web/src/features/ai-review-rule/useSaveAiReviewRuleMutation.test.ts` | Create | 36 | Verify body shape, success result, and expected 4xx error mapping. |
| `apps/web/src/features/ai-review-rule/usePublishAiReviewRuleMutation.test.ts` | Create | 32 | Verify path parameter, success result, and expected 4xx error mapping. |
| `apps/web/src/features/ai-review-rule/AiReviewRuleEntryCard.test.tsx` | Create | 34 | Server-render smoke for card copy, CTA, and no-form-body boundary. |

Estimated total: about `290` net lines. This is under the `350` soft LOC cap
without removing hook error-path coverage or the entry-card smoke test.

The modify target was verified in the working tree:
`apps/web/src/pages/owner/OwnerTaskDetailPage.tsx` exists, exports
`OwnerTaskDetailPage`, imports `useTaskDetailQuery`, and renders the Owner task
detail surface with `className="task-detail-page"`.

## 3. Caps

These caps must match `m7p4b2-c1-scope-budget.md`.

| Cap | Value | Meaning |
|---|---:|---|
| Soft file-count cap | `8` files | If implementation needs more than 8 touched files, stop and report before expanding. |
| Hard file-count cap | `10` files | Do not exceed without explicit adjudication. |
| Soft LOC cap | `350` net lines | Stop and report if implementation naturally exceeds this. |
| Hard LOC cap | `500` net lines | True ceiling for C1. |
| Generated churn | `0` | No OpenAPI/type regeneration in C1. |

The per-file estimate totals below the soft cap. If implementation naturally
exceeds `350` net lines without expanding scope, stop and report rather than
trimming the hook error-path coverage or the entry-card smoke test.

## 4. Scope Boundaries

### In

- `useSaveAiReviewRuleMutation`.
- `usePublishAiReviewRuleMutation`.
- Generated-type re-export/narrowing only.
- Owner task-detail AI review rule entry card/placeholder.
- Minimal route/drawer/open-state mount point for future form work.
- Expected 4xx error handling in write hooks.

### Out

- prompt textarea;
- dimensions editor;
- threshold control;
- frontend validation implementation;
- rule publish/history UI;
- reading current/previous rules;
- backend endpoints;
- OpenAPI changes;
- generated type changes;
- P3a/P3b/P4a/P4b1 backend changes.

## 5. Load-Bearing Constraints

### Append-Only Save Semantics

`saveAiReviewRule` is append-only. Every save creates a new draft rule version
for the task. C1 must not design UI state around update-in-place.

Implementation implication:

- the entry card can say "配置 AI 审核规则" or "开始配置";
- it must not say "编辑当前草稿" unless a read endpoint exists later;
- hooks should return the created `AiReviewRule` and let C2 decide how to keep
  the created rule in local form state.

### Active Rule Is A Task Pointer

Publishing a rule sets `tasks.current_ai_review_rule_id`. Multiple rules may
be `published`; active means the task pointer.

Implementation implication:

- C1 should not display "生效中" from `AiReviewRule.status` alone;
- any future status slot in the entry card should be neutral, such as "规则状态
  将在发布后显示".

### Validation Boundary For C2

C1 records but does not implement the form validation rules:

- `promptTemplate` non-empty;
- at least one dimension;
- dimensions trim non-empty;
- dimensions unique;
- `threshold` in `[0, 1]`;
- Chinese errors align with backend wording, including `评分维度不能重复` and
  `阈值必须在 0 到 1 之间`.

### Expected 4xx Paths

Hooks must handle backend 4xx as business failures:

- invalid input;
- task not found or cross-owner task hidden as not found;
- rule not found for publish;
- authorization failure.

OpenAPI does not fully enumerate these response paths, so hooks must not rely
on only `201` / `200` and network failures.

### Reading-Surface STOP Condition

If any C1 implementation step requires:

- list rules by task;
- fetch current active rule;
- fetch rule version history;
- infer active rule from task detail;

then C1 must stop and report. The implementation must not add a GET endpoint,
mock data, or silently rely on missing state.

## 6. Hook Design

### `useSaveAiReviewRuleMutation`

Input type:

```ts
type SaveAiReviewRuleVariables = AiReviewRuleRequest;
```

Return type:

```ts
type AiReviewRule = components['schemas']['AiReviewRule'];
```

Failure type:

```ts
class AiReviewRuleMutationFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    readonly userMessage: string,
  ) {
    super(userMessage);
  }
}
```

Expected mapping:

- `400` -> backend message or `AI 审核规则校验失败`;
- `403` -> `没有配置权限`;
- `404` -> `任务或规则不存在`;
- fallback -> backend message or `AI 审核规则保存失败`.

No query invalidation is required in C1 because there is no read query yet.
C2/C3 can add invalidation when a read surface exists.

### `usePublishAiReviewRuleMutation`

Input type:

```ts
type PublishAiReviewRuleVariables = { ruleId: number };
```

The hook should call:

```ts
apiClient.POST('/ai-review/rules/{ruleId}/publish', {
  params: { path: { ruleId } },
});
```

Failure mapping mirrors save, with fallback `AI 审核规则发布失败`.

## 7. Entry Card Design

Recommended file: `AiReviewRuleEntryCard.tsx`.

Props:

```ts
type AiReviewRuleEntryCardProps = {
  taskId: number;
  onOpenEditor: () => void;
};
```

Suggested copy:

- title: `AI 审核规则`
- description: `配置 Prompt、评分维度和阈值后,AI 检查会绑定到规则版本。`
- CTA: `配置规则`
- tertiary text: `保存会创建新的规则版本;发布后才会成为当前生效规则。`

No fields for prompt, dimensions, threshold, or version history in C1.

Owner visibility is inherited from `OwnerTaskDetailPage`, which is already
under the Owner route. No labeler/reviewer entry is added.

## 8. Three-Viewport Strategy

The entry is a regular task-detail card and should inherit the existing grid
behavior:

| Viewport | Strategy |
|---|---|
| 1440 | Card appears as a normal detail-grid item. CTA fits on one line. |
| 1280 | Same card, compact copy, no wide table/layout dependency. |
| 1024 | Card stacks with other task detail cards. CTA remains visible and text wraps naturally. |

Verification plan:

- use Browser screenshots if the app is seeded and running;
- otherwise D-port with proof that C1 does not alter global layout CSS and the
  entry is a standard card component with server-render smoke coverage.

## 9. Tests

C1 tests should use the existing frontend style and avoid new dependencies.

Hook tests:

- success saves exact `AiReviewRuleRequest` body;
- save maps 400 backend message to user-facing error;
- publish uses `ruleId` as path param;
- publish maps 404 to `任务或规则不存在`.

Entry card test:

- server-render card contains title/CTA;
- server-render card contains append-only/version wording;
- card output does not contain textarea, dimension input, threshold input, or
  publish-history UI.

## 10. Risks

| Risk | Handling |
|---|---|
| Reading surface needed earlier than expected | STOP. Do not add GET endpoint in C1. |
| Hooks duplicate error mapping | Accept small duplication or extract a tiny shared helper inside C1 if still under cap. |
| Entry card implies active state incorrectly | Use neutral copy; do not derive active from status. |
| C1 drifts into form implementation | Hard stop: form body belongs to C2. |
| Cap mismatch between docs | This doc and scope-budget both use file cap `8/10`, LOC cap `350/500`, generated churn `0`. |

## 11. Verification For Implementation

C1 implementation should report:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
pnpm --filter @labelhub/web test
git diff --stat
git diff --check
```

Expected:

- OpenAPI MD5 remains `b7df19fdb69f8d22b2f0dbdbc845d95d`;
- migrations remain `17`;
- humanpending remains `153`;
- backend files unchanged;
- generated `schema.d.ts` unchanged;
- frontend tests increase from `131`.
