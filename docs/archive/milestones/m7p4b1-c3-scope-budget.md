# M7-P4b1 C3 Scope Budget

## Objective

Bind runtime AI reviews to the active task AI review rule when present, while preserving the P4a promptVersionId fallback for tasks without an active rule.

C3 closes the research half-binding gap:

- `promptVersionId` identifies prompt text;
- `aiReviewRuleId` identifies dimensions and threshold;
- idempotency includes rule id when a rule is active.

## In Scope

### OpenAPI

Add nullable evidence field:

```yaml
AiCall:
  properties:
    aiReviewRuleId:
      type: integer
      format: int64
      nullable: true
      description: Active AI review rule version used for this call, when the task had one.
```

Keep:

- `TriggerAiReviewRequest.required: [promptVersionId]`
- `AiCall.promptVersion`, `promptVersionId`, and `providerAdapterVersion` shapes from P4a.

### Migration

Add one migration:

```sql
ALTER TABLE ai_calls ADD COLUMN ai_review_rule_id BIGINT NULL;
ALTER TABLE ai_calls ADD INDEX idx_ai_calls_ai_review_rule (ai_review_rule_id);
ALTER TABLE ai_calls
  ADD CONSTRAINT fk_ai_calls_ai_review_rule
  FOREIGN KEY (ai_review_rule_id) REFERENCES ai_review_rules(id);
```

Expected migration count: `16 -> 17`.

### Runtime

Modify `AiReviewService.review(...)`:

- load task as today;
- if `task.currentAiReviewRuleId != null`, resolve `AiReviewRuleEntity`;
- when a rule exists, use `rule.currentPromptVersionId` as the effective prompt id;
- when no rule exists, use request `promptVersionId` exactly as P4a does;
- persist `aiReviewRuleId` on new `AiCallEntity`;
- include rule id in idempotency key only when rule-bound.

### Persistence And DTO

Modify:

- `AiCallEntity` with `aiReviewRuleId`.
- `AiCallMapper` explicit insert/select/result SQL.
- `AiReviewDtoMapper` to set generated `AiCall.aiReviewRuleId`.
- `ExportArtifactBuilder` to include `aiReviewRuleId`.

### Tests

Add or extend:

- `AiReviewServiceTest`
- `AiReviewDtoMapperTest`
- `AiCallMapperContractTest`
- `AiCallPromptVersionAdditiveContractTest` or a new rule-binding contract test
- `AiReviewRuleInfrastructureContractTest`

## Out Of Scope

- Owner UI.
- Frontend trigger changes.
- `TriggerAiReviewRequest` contract changes.
- `AiReviewRuleService` lifecycle changes.
- `TaskService.canPublish` changes.
- Making AI review rule required for publish.
- P3a/P3b behavior.
- humanpending.

## Decision Summary

| Decision | Recommendation |
|---|---|
| Trigger contract | Keep `promptVersionId` required |
| Runtime prompt resolution | Active task rule wins; request id is fallback |
| No-rule behavior | Preserve exact P4a behavior |
| `ai_calls.ai_review_rule_id` | Add nullable FK in C3 |
| Idempotency | Add `ruleVersionId:{id}` segment only when active rule exists |
| Dangling active pointer | Fail with rule not found; do not silently fallback |
| Frontend | No business frontend changes; generated type churn only |

## Line Budget

| Item | Estimate |
|---|---:|
| OpenAPI additive field | 25 |
| Migration | 25 |
| `AiCallEntity` / `AiCallMapper` / DTO / export | 115 |
| `AiReviewService` runtime resolution | 160 |
| Tests | 310 |
| Total hand-authored | ~635 |

Cap proposal:

- Soft cap: 700 hand-authored lines.
- Hard cap: 1000 hand-authored lines.
- Generated Java/TS churn is reported separately.

## Risk Register

### Half-binding not fully closed

Risk: runtime writes `aiReviewRuleId` but idempotency still only keys on prompt version.

Mitigation: rule-bound idempotency key includes `ruleVersionId`.

### No-rule regression

Risk: optional review rules accidentally become required at runtime.

Mitigation: no active rule path uses existing request `promptVersionId` and exact P4a idempotency key.

### Trigger contract drift

Risk: changing `TriggerAiReviewRequest` would require frontend trigger work and break the C3/C4 split.

Mitigation: C3 explicitly keeps the request contract unchanged.

### Mapper SQL omissions

Risk: explicit `AiCallMapper` SQL forgets the new column in one select path.

Mitigation: update insert, `selectById`, `selectByIdempotencyKey`, `selectBySubmissionId`, and `selectBySubmissionIdsOrdered`; add contract tests.

### Legacy evidence behavior

Risk: pre-C3 AI calls have null `aiReviewRuleId`.

Mitigation: OpenAPI field is nullable; DTO maps null; C4 integration should later verify mixed legacy/new provenance.

## Verification Plan

Before implementation report:

```bash
mvn -pl services/api -Dtest=AiReviewServiceTest,AiReviewDtoMapperTest,AiCallMapperContractTest,AiCallPromptVersionAdditiveContractTest,AiReviewRuleInfrastructureContractTest test
mvn -pl services/api test
pnpm gen:api
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
bash scripts/check-protected-endpoints.sh
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
git status --short
```

Expected:

- OpenAPI MD5 changes from `7c9358b2...`.
- migrations `16 -> 17`.
- humanpending remains `146`.
- C2 lifecycle code remains frozen.
- frontend tests remain `131`.

## Audit Notes

Review should check:

- `AiReviewRuleService` diff is empty.
- `TaskService.canPublish` diff is empty.
- `TriggerAiReviewRequest` still requires `promptVersionId`.
- New `ai_calls.ai_review_rule_id` is present in all mapper select paths.
- Active-rule path ignores request prompt id and uses rule prompt id.
- No-rule path preserves P4a key exactly.
- Rule-bound key length is guarded by the existing 160-character check.
