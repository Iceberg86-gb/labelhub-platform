# M7-P4b1 C3 Pre-Estimate: AI Review Rule Runtime Binding

## Status

Gate only. No production code is changed in this cluster until this gate is approved.

Current anchor:

- HEAD: `e762d81` (`M7-P4b1 C2`)
- OpenAPI MD5: `7c9358b2b2d5a1079de8f768a243841a`
- Backend tests: `531 / 84`
- Frontend tests: `131`
- Migrations: `16`
- humanpending: `146`

## Scope Summary

C3 should close the P4b research "half-binding" gap by binding AI review runtime calls to the active task AI review rule when one exists.

Runtime rule binding means:

1. Load the submission and task as `AiReviewService.review(...)` already does.
2. If `task.currentAiReviewRuleId` is set, load that rule.
3. Use the rule's `currentPromptVersionId` as the effective prompt version.
4. Persist `ai_calls.ai_review_rule_id = rule.id`.
5. Include the rule id in the idempotency key so changes to dimensions or threshold can produce fresh evidence.
6. If the task has no active rule, preserve the existing P4a behavior exactly: use the request `promptVersionId`, write `ai_review_rule_id = null`, and use the existing P4a idempotency key format.

C3 should not implement Owner UI, change `AiReviewRuleService`, make review rules required for task publish, or change the trigger request contract.

## Verified Current Seams

- `AiReviewService.review(Long submissionId, Long ownerId, Long promptVersionId)` already loads the task and resolves `PromptVersionEntity` from the request id.
- `TaskEntity.currentAiReviewRuleId` exists and is populated by C2 publish.
- `AiReviewRuleEntity.currentPromptVersionId` points to the prompt version that should drive runtime when the rule is active.
- `AiCallMapper` uses explicit SQL for insert/select, so every new `ai_calls` column must be added to SQL and result maps.
- `AiReviewDtoMapper.toAiCall(...)` already maps additive evidence fields such as `promptVersionId` and `providerAdapterVersion`.
- P4a C5 already established that old idempotency key formats are not queried for compatibility. C3 can introduce another new key shape for rule-bound calls without dual lookup.

## Decision 1: Keep Trigger Request Contract, Active Rule Wins

Recommendation: keep `TriggerAiReviewRequest` unchanged with required `promptVersionId`.

Runtime resolution:

```text
if task.currentAiReviewRuleId exists:
    effective rule = ai_review_rules[currentAiReviewRuleId]
    effective promptVersionId = rule.currentPromptVersionId
    aiReviewRuleId = rule.id
else:
    effective promptVersionId = request.promptVersionId
    aiReviewRuleId = null
```

Rationale:

- Review rules are optional by P4b research decision. Existing tasks without an active rule must continue to work with the P4a default prompt id.
- Keeping the request contract avoids another frontend vertical slice in C3. P4b2 can later simplify the UI if task-bound rule resolution becomes the only intended trigger mode.
- Active rule precedence is the only way to close half-binding. If a task has an active rule, runtime should use the rule's prompt, dimensions, and threshold evidence even if the frontend still sends the default prompt id.
- The trigger request `promptVersionId` becomes a fallback prompt id for tasks without active rules. This is a transitional but explicit P4b1 semantics.

Rejected alternatives:

- Remove `promptVersionId` from the trigger request: would force a frontend trigger migration in C3 and would make no-rule tasks depend on implicit default lookup inside runtime.
- Best-effort rule binding only when the request id equals the active rule prompt id: leaves half-binding open whenever the frontend still sends the default id.
- Add `useTaskRule` or a new endpoint: adds UX and API surface before P4b2.

OpenAPI impact:

- Do not change `TriggerAiReviewRequest`.
- Add nullable `aiReviewRuleId` to `AiCall` only.

## Decision 2: Add `ai_calls.ai_review_rule_id` In C3

Recommendation: add the column now, in the runtime integration cluster.

Migration:

```sql
ALTER TABLE ai_calls ADD COLUMN ai_review_rule_id BIGINT NULL;
ALTER TABLE ai_calls ADD INDEX idx_ai_calls_ai_review_rule (ai_review_rule_id);
ALTER TABLE ai_calls
  ADD CONSTRAINT fk_ai_calls_ai_review_rule
  FOREIGN KEY (ai_review_rule_id) REFERENCES ai_review_rules(id);
```

OpenAPI:

- `AiCall.aiReviewRuleId`: integer int64, nullable, not required.

Java:

- `AiCallEntity.aiReviewRuleId`
- `AiCallMapper` insert/select/result maps include `ai_review_rule_id`.
- `AiReviewDtoMapper.toAiCall(...)` sets `aiReviewRuleId`.
- `ExportArtifactBuilder` includes `aiReviewRuleId` in AI call evidence export.

Rationale:

- Prompt version alone cannot reproduce dimensions and threshold. The rule id binds evidence to the full rule version.
- Nullable keeps no-rule and legacy AI calls valid.
- This mirrors P4a's additive `promptVersionId` field.

Rejected alternative:

- Defer the column to C4: C4 is intended to deepen integration tests, not add production evidence schema.

## Decision 3: Include Rule Id In Idempotency Key Only When Rule-Bound

Recommendation: use two explicit key shapes.

No active rule:

```text
submission:{submissionId}:provider:{provider}:model:{model}:promptVersionId:{promptVersionId}:adapter:{providerAdapterVersion}
```

Active rule:

```text
submission:{submissionId}:provider:{provider}:model:{model}:promptVersionId:{promptVersionId}:adapter:{providerAdapterVersion}:ruleVersionId:{aiReviewRuleId}
```

Rationale:

- If the rule changes dimensions or threshold while prompt text is unchanged, the rule id changes but prompt version may not. A rule-bound idempotency key must therefore include rule id.
- No-rule tasks should keep exact P4a idempotency behavior. Adding a `ruleVersionId:none` segment would unnecessarily invalidate existing no-rule idempotency rows.
- This follows the P4a legacy-key decision: old or differently shaped keys are not dual-queried. A changed evidence shape intentionally creates new AI evidence.

Length check:

- The current P4a key is already guarded against `VARCHAR(160)` overflow.
- The added segment is `:ruleVersionId:` plus a numeric id. With 19-digit ids this adds 34 characters.
- Typical mock/default key with rule id is under 100 characters. The existing fail-fast length guard should remain and should cover the new key.

Rejected alternative:

- Keep key based only on promptVersionId: threshold-only edits would silently reuse stale AI evidence.

## Decision 4: No-Rule Fallback Preserves P4a

Recommendation: no active rule means P4a behavior is unchanged.

No-rule behavior:

- Resolve request `promptVersionId` through `PromptVersionService.findById(...)`.
- Persist `ai_review_rule_id = null`.
- Use the existing P4a idempotency key.
- Existing tests for promptVersionId, provider adapter version, failed recorder, and provenance should continue to pass.

Invalid data behavior:

- If `task.currentAiReviewRuleId` points to a missing rule, throw `AiReviewRuleNotFoundException`.
- If the active rule points to a missing prompt version, keep using the existing `PromptVersionNotFoundException`.

Rationale:

- Research Q4 made review rule optional. Optional must mean runtime remains usable before P4b2 UI configures rules.
- Failing on a dangling active pointer is safer than silently falling back to the request prompt id because the task claims to have an active rule.

## Decision 5: Do Not Modify AiReviewRuleService

Recommendation: `AiReviewService` should resolve the active rule through `TaskEntity.currentAiReviewRuleId` and `AiReviewRuleMapper.selectById(...)`.

Rationale:

- C2's `AiReviewRuleService` owns save and publish lifecycle. C3 only needs runtime lookup.
- Modifying C2 service would mix lifecycle and runtime responsibilities.
- The task is already owner-checked in `AiReviewService.review(...)`, so resolving the task's active pointer does not require repeating owner authorization inside `AiReviewRuleService`.

Rejected alternative:

- Add `findActiveForTask(...)` to `AiReviewRuleService`: cleaner on first glance, but violates the C3 guard that C2 lifecycle service should remain frozen.

## Files And Estimated Changes

| Area | Files | Estimate |
|---|---:|---:|
| OpenAPI additive `AiCall.aiReviewRuleId` + generated type churn | `packages/contracts/openapi/labelhub.yaml`, generated Java/TS | 25 hand-authored + generated |
| Migration | new `V20261203xxxx__alter_ai_calls_ai_review_rule.sql` | 25 |
| Entity/mapper/DTO/export evidence | `AiCallEntity`, `AiCallMapper`, `AiReviewDtoMapper`, `ExportArtifactBuilder` | 115 |
| Runtime resolution | `AiReviewService.java` | 160 |
| Tests | service, mapper contract, DTO mapper, migration/OpenAPI contracts | 310 |

Estimated hand-authored total: about 635 lines.

Recommended cap: soft 700 / hard 1000, generated churn separate.

Reasoning:

- C3 is the heaviest P4b1 cluster because it touches runtime evidence, idempotency, migration, mapper SQL, and existing AI tests.
- It is still narrower than P4a C3 because trigger request and frontend trigger code remain unchanged.

## Test Plan

Service tests:

- Active rule overrides request promptVersionId and uses `rule.currentPromptVersionId`.
- Active rule writes `aiReviewRuleId` on `AiCallEntity`.
- Active rule idempotency key includes `ruleVersionId:{id}`.
- No-rule task preserves existing P4a key and writes `aiReviewRuleId = null`.
- Dangling `currentAiReviewRuleId` fails with not-found instead of silently falling back.
- Existing idempotency hit tests pass under the no-rule path.

Mapper/DTO tests:

- `AiCallMapper` insert/select/result maps include `ai_review_rule_id`.
- `AiReviewDtoMapper` maps `AiCall.aiReviewRuleId`, including null fallback.
- `ExportArtifactBuilder` includes `aiReviewRuleId`.

Contract tests:

- OpenAPI `AiCall` contains nullable `aiReviewRuleId`.
- Migration contains `ai_review_rule_id`, index, and FK.
- Mapper contract allows the new column but no deletes.

Verification commands:

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

- OpenAPI MD5 changes.
- migrations become `17`.
- humanpending remains `146`.
- frontend tests should remain `131`.
- backend tests should rise modestly from `531 / 84`.

## Audit Notes For Implementation Review

Implementation audit should verify:

- `TriggerAiReviewRequest` remains required `[promptVersionId]`.
- No frontend trigger files change.
- `AiReviewRuleService`, save/publish endpoints, and `TaskService.canPublish` are unchanged.
- Active-rule AI calls persist both `promptVersionId` and `aiReviewRuleId`.
- No-rule AI calls preserve the exact P4a idempotency key.
- The new rule-bound idempotency key remains below 160 characters and retains fail-fast validation.
- Provenance and export evidence expose `aiReviewRuleId`.
