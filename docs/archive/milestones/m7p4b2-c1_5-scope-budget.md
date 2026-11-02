# M7-P4b2 C1.5 Scope + Budget

## Scope Status

C1.5 implements the P4b2 read-contract RESEARCH decision as the first backend-changing P4b2 cluster. This cluster retires the old P4b2 OpenAPI baseline and creates the next frozen anchor for later P4b2 clusters.

Frozen before C1.5:

- OpenAPI MD5: `b7df19fdb69f8d22b2f0dbdbc845d95d`
- Backend tests: `540 / 88`
- Frontend tests: `137`
- Migrations: `17`
- humanpending: `153`

Expected after C1.5:

- OpenAPI MD5: changes, value to be filled by the implementation report.
- Backend tests: increases with AI review rule list coverage.
- Frontend tests: may remain `137`; generated type churn is expected.
- Migrations: remains `17`.
- humanpending: remains `153`.

## Contract Anchor

The contract shape is fixed by `docs/internal/m7p4b2-read-contract-research.md`.

In scope:

- Add `GET /ai-review/rules?taskId={taskId}` with operationId `listAiReviewRules`.
- Return `200` as `array<AiReviewRule>` sorted by `versionNo ASC`, matching the `schema_versions` list convention.
- Add shared response refs for `400`, `401`, `403`, and `404`.
- Add required `AiReviewRule.isCurrent: boolean`.
- Add missing shared error response refs to:
  - `POST /ai-review/rules`: `400`, `401`, `403`, `404`.
  - `POST /ai-review/rules/{ruleId}/publish`: `401`, `403`; keep existing `200` and `404`.

Out of scope:

- No `GET /ai-review/rules/{ruleId}` detail endpoint.
- No task-contract change and no `Task.currentAiReviewRuleId` exposure.
- No migration. If an implementation-time index gap is discovered, stop and report.
- No frontend UI, hooks, or rule history display changes.
- No change to append-only save or publish semantics.

## Budget

Hand-authored budget excludes generated files.

| Budget item | Soft cap | Hard cap | Notes |
|---|---:|---:|---|
| Hand-authored files | 11 | 14 | OpenAPI YAML counts as hand-authored. Tests count. |
| Hand-authored net LOC | 550 | 750 | Generated churn excluded. |
| Generated files | 5 | 8 | Expected Java generated API/model plus `apps/web/src/shared/api/generated/schema.d.ts`; report exact churn. |
| Generated net LOC | report only | report only | Not capped, but must be listed separately. |

Estimated hand-authored files:

The soft file cap is `11` because the safe implementation naturally touches eleven focused files: OpenAPI, controller, service, view, DTO mapper, mapper, and five focused tests. Combining these files would reduce the count only cosmetically while making mapper/service/controller contract coverage less readable. The hard cap stays `14`, leaving room for codegen or test-organization surprises without relaxing the hand-authored LOC budget.

| File | Action | Estimated LOC | Purpose |
|---|---|---:|---|
| `packages/contracts/openapi/labelhub.yaml` | Modify | 45 | Add list endpoint, `isCurrent`, and error refs. |
| `services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java` | Modify | 20 | Add `listAiReviewRules` controller method. |
| `services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewRuleService.java` | Modify | 65 | Add task-owned list service and `isCurrent` computation; ensure save/publish responses carry current metadata. |
| `services/api/src/main/java/com/labelhub/api/module/ai/service/view/AiReviewRuleView.java` | Modify | 12 | Carry `isCurrent` response metadata. |
| `services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewRuleDtoMapper.java` | Modify | 18 | Map `isCurrent` into generated `AiReviewRule`. |
| `services/api/src/main/java/com/labelhub/api/module/ai/mapper/AiReviewRuleMapper.java` | Modify | 20 | Add a list-only ASC query; keep existing `selectByTaskId` DESC unchanged. |
| `services/api/src/test/java/com/labelhub/api/module/ai/web/AiReviewControllerTest.java` | Modify | 45 | Controller delegation and auth-owner path coverage for list. |
| `services/api/src/test/java/com/labelhub/api/module/ai/service/AiReviewRuleServiceTest.java` | Modify | 90 | Empty list, multi-version ordering, `isCurrent`, cross-owner 404, save/publish metadata. |
| `services/api/src/test/java/com/labelhub/api/module/ai/web/AiReviewRuleDtoMapperTest.java` | Modify | 40 | `isCurrent` DTO mapping. |
| `services/api/src/test/java/com/labelhub/api/module/ai/mapper/AiReviewRuleMapperContractTest.java` | Modify | 30 | Contract test for ASC ordering and allowed mapper methods. |
| `services/api/src/test/java/com/labelhub/api/module/ai/AiReviewRuleInfrastructureContractTest.java` | Modify | 25 | OpenAPI contract guard for list endpoint, `isCurrent`, and error refs. |

Estimated total hand-authored LOC: `410`.

This is below the soft LOC cap and leaves room for small implementation variance.

Generated churn:

- `apps/web/src/shared/api/generated/schema.d.ts`
- Generated Java API interface for `AiReviewApi`
- Generated Java model for `AiReviewRule`
- Any adjacent generated support file touched by the repo's codegen

Generated churn is expected and must be reported separately in the implementation report.

## Baseline Retirement + Re-freeze Procedure

C1.5 intentionally retires the old OpenAPI MD5:

- Retired baseline: `b7df19fdb69f8d22b2f0dbdbc845d95d`
- New baseline: unknown at gate time; the implementation report must fill it from a real `md5sum` or `md5 -q` run.

Implementation report must include:

```bash
md5sum packages/contracts/openapi/labelhub.yaml
# or on macOS:
md5 -q packages/contracts/openapi/labelhub.yaml

find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
git diff --stat
git diff --check
```

Once the implementation report confirms the new OpenAPI MD5, that value becomes the frozen P4b2 anchor for C2/C3/C4 and replaces `b7df19...`.

## D-Port Accounting

The audit sandbox may not have Maven Central or a working Maven codegen path. Therefore these C1.5 facts are D-port until the implementation report supplies raw command output:

- New OpenAPI MD5.
- Generated Java and TypeScript type churn.
- Full backend test count and summary.
- Any codegen side effects outside the expected generated files.

The implementation zip must include OpenAPI YAML and generated files so the reviewer can independently grep:

- `operationId: listAiReviewRules`
- `isCurrent` under `AiReviewRule.required`
- `GET /ai-review/rules` query parameter `taskId`
- shared error response refs

## Scope Guardrails

- `isCurrent` is task-pointer-derived response metadata, not stored state.
- `isCurrent` must compare each rule id with `tasks.current_ai_review_rule_id`.
- Rule history must use a new ASC mapper query; existing `selectByTaskId` DESC semantics stay frozen.
- `status=published` must not be used as "current"; a task may have multiple published rules.
- A new saved draft returns `isCurrent=false`.
- A published rule response returns `isCurrent=true` after the task pointer update.
- Cross-owner list access returns the existing not-found style, aligned with P4b1 ownership hiding.
- ADR-005 remains intact: `isCurrent` means current AI review configuration, not an automated verdict.
- ADR-011 remains intact: this read path exposes owner business rule configuration, not provider prompt internals.

## Frozen Out-Of-Scope Files

C1.5 must not modify:

- Any migration file or migration count.
- `humanpending.md`.
- Task OpenAPI schema.
- Frontend UI or owner pages.
- P3a/P3b/P4a logic except generated type churn caused by the OpenAPI change.
