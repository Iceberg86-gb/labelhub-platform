# M7-P4b1 C2 Scope Budget

## Objective

Implement the backend AI review rule lifecycle surface:

- save owner rule input as a draft `ai_review_rules` version;
- convert `promptTemplate` into a P4a `prompt_versions` row;
- publish/activate a saved rule by updating status and `tasks.current_ai_review_rule_id`;
- return a complete `AiReviewRule` response with prompt text joined from `prompt_versions`.

No runtime AI review behavior changes in C2.

## Approved Research Inputs

- `AiReviewRuleRequest.promptTemplate` remains owner-facing input.
- Prompt text is stored in global `prompt_versions`.
- `ai_review_rules` stores dimensions, threshold, status, and current prompt version pointer.
- Review rule is optional for task publish.
- Conclusion strategy is threshold-derived in v1 and not stored as a separate field.

## In Scope

### OpenAPI

Add:

```yaml
/ai-review/rules/{ruleId}/publish:
  post:
    tags: [AIReview]
    operationId: publishAiReviewRule
    parameters:
      - name: ruleId
        in: path
        required: true
        schema:
          type: integer
          format: int64
    responses:
      '200':
        description: AI review rule published.
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AiReviewRule'
```

Keep:

- `AiReviewRuleRequest` unchanged.
- Existing `POST /ai-review/rules` response shape.

### Backend Service

Create:

- `services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewRuleService.java`
- optional view record under `service/view` or `web`, e.g. `AiReviewRuleView`
- `AiReviewRuleDtoMapper`
- `InvalidAiReviewRuleException`
- `AiReviewRuleNotFoundException`

Service methods:

- `saveRule(AiReviewRuleRequest request, Long ownerId)`
- `publishRule(Long ruleId, Long ownerId)`
- `findById(Long ruleId, Long ownerId)` if needed for tests/controller reuse

### Mapper Extensions

Extend `AiReviewRuleMapper` with:

- `selectByIdForUpdate(Long id)` if publish needs row lock.
- `markPublished(Long id)` or `updateStatusAndActivatedAt(Long id, String status)`.

Extend `TaskMapper` with:

- `updateCurrentAiReviewRuleId(Long taskId, Long ruleId)`.

### Controller

Modify:

- `AiReviewController.saveAiReviewRule(...)` to call service and mapper.

Add:

- `publishAiReviewRule(ruleId)`.

### Global Exception Handling

Map:

- invalid rule input to `400 INVALID_AI_REVIEW_RULE`;
- missing/cross-owner rule to `404 NOT_FOUND`.

### Tests

Add or extend:

- `AiReviewRuleServiceTest`
- `AiReviewRuleControllerTest` or contract-style controller test
- `AiReviewRuleMapperContractTest`
- `AiReviewRuleInfrastructureContractTest` for publish endpoint presence

## Out Of Scope

- Runtime AI review rule resolution in `AiReviewService`.
- `ai_calls.ai_review_rule_id`.
- Trigger request changes.
- Owner UI / frontend business code.
- Task publish guard requirement.
- PromptVersionService behavior changes.
- P3a/P3b behavior.
- humanpending.
- migrations.

## Decision Summary

| Decision | Recommendation |
|---|---|
| Save semantics | Save creates draft only |
| Publish semantics | Separate `POST /ai-review/rules/{ruleId}/publish` endpoint |
| Repeated save | Always creates new task-scoped rule version |
| Prompt conversion | `PromptVersionService.create(promptTemplate, ownerId)` |
| Prompt display | Join prompt version content at DTO mapping time |
| Validation | prompt nonblank, dimensions nonempty/nonblank/nonduplicate, threshold `0..1` |
| Authz | Task owner only; missing or cross-owner returns 404 |
| Version concurrency | Bounded retry on `(task_id, version_no)` duplicate |
| Active rule | `tasks.current_ai_review_rule_id` pointer defines current active rule |
| Conclusion strategy | Deferred; threshold-derived v1 only |

## Line Budget

| Item | Estimate |
|---|---:|
| OpenAPI endpoint | 45 |
| Generated type churn | not counted |
| `AiReviewRuleService` + view | 190 |
| `AiReviewRuleDtoMapper` | 90 |
| Controller wiring | 45 |
| Mapper extensions | 80 |
| Exceptions + handler | 65 |
| Tests | 250 |
| Total hand-authored | ~765 |

Cap proposal:

- Soft cap: 800 hand-authored lines.
- Hard cap: 1000 hand-authored lines.
- Generated Java/TS churn is reported separately.

## Risk Register

### Prompt template join

Risk: `AiReviewRule` response requires `promptTemplate`, but the rule table only stores prompt version id.

Mitigation: map response from a view containing both `AiReviewRuleEntity` and `PromptVersionEntity`.

### Version race

Risk: two saves for one task compute the same `version_no`.

Mitigation: bounded retry on duplicate key, mirroring `PromptVersionService`.

### Publish transaction

Risk: rule status updates but task pointer does not.

Mitigation: `@Transactional` publish service method.

### Overreach into runtime

Risk: implementing publish tempts runtime AI review resolution.

Mitigation: C2 does not touch `AiReviewService`, providers, `ai_calls`, failed-call recorder, or idempotency.

### Mapper contract adjustment

Risk: C1 mapper contract forbids update-like methods.

Mitigation: update the contract to allow the explicit publish status update method while still rejecting delete/remove methods.

## Verification Plan

Before final report:

```bash
mvn -pl services/api -Dtest=AiReviewRuleServiceTest,AiReviewRuleControllerTest,AiReviewRuleMapperContractTest,AiReviewRuleInfrastructureContractTest test
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
- migrations remain `16`.
- humanpending remains `146`.
- backend tests increase.
- frontend tests should remain `131`.

## Audit Notes For Implementation Review

Implementation audit should verify:

- `saveAiReviewRule` no longer throws `501`.
- Publish endpoint exists and updates `tasks.current_ai_review_rule_id`.
- Save does not update the task pointer.
- Same prompt text + changed threshold reuses prompt version id.
- Cross-owner save/publish returns 404.
- `TaskService.canPublish` still does not require AI review rule.
- `AiReviewService` diff is empty.
