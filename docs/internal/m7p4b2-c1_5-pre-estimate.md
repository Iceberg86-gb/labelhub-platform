# M7-P4b2 C1.5 Pre-Estimate

## Goal

Implement the P4b2 AI review rule read contract as a backend contract cluster:

- Add `GET /ai-review/rules?taskId={taskId}`.
- Add required `AiReviewRule.isCurrent`.
- Backfill error response refs for the existing save/publish rule endpoints.
- Regenerate API types.
- Keep migrations and task contract unchanged.

## Contract Mapping

The implementation must follow `docs/internal/m7p4b2-read-contract-research.md` exactly.

### OpenAPI

Modify `packages/contracts/openapi/labelhub.yaml`.

Required changes:

```yaml
/ai-review/rules:
  get:
    tags: [AIReview]
    operationId: listAiReviewRules
    parameters:
      - in: query
        name: taskId
        required: true
        schema:
          type: integer
          format: int64
    responses:
      '200':
        description: AI review rules for the task, ordered by version number.
        content:
          application/json:
            schema:
              type: array
              items:
                $ref: '#/components/schemas/AiReviewRule'
      '400':
        $ref: '#/components/responses/ErrorBadRequest'
      '401':
        $ref: '#/components/responses/ErrorUnauthorized'
      '403':
        $ref: '#/components/responses/ErrorForbidden'
      '404':
        $ref: '#/components/responses/ErrorNotFound'
```

Add `isCurrent` to `AiReviewRule.required` and properties:

```yaml
isCurrent:
  type: boolean
  description: >
    Server-derived. True when this rule id equals the task's current_ai_review_rule_id.
```

Backfill write endpoint errors:

- `POST /ai-review/rules`: add shared `400`, `401`, `403`, `404`; keep `201`.
- `POST /ai-review/rules/{ruleId}/publish`: add shared `401`, `403`; keep `200`, `404`.

Do not add:

- Detail endpoint.
- Task fields.
- Migration-driven contract fields.

### Backend Controller

Modify `services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java`.

Add generated-interface override:

```java
public ResponseEntity<List<AiReviewRule>> listAiReviewRules(Long taskId) {
    return ResponseEntity.ok(aiReviewRuleService.listRules(taskId, currentUserId()).stream()
        .map(aiReviewRuleDtoMapper::toRule)
        .toList());
}
```

Exact signature will follow regenerated `AiReviewApi`.

### Backend Service

Modify `services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewRuleService.java`.

Add `listRules(taskId, ownerId)`:

1. Load task via existing task ownership path.
2. If missing or cross-owner, throw `TaskNotFoundException(taskId)` to preserve hidden existence.
3. Read `task.currentAiReviewRuleId`.
4. Read task-scoped rules ordered by `versionNo ASC`.
5. For each rule, load its prompt version and return `AiReviewRuleView(rule, promptVersion, isCurrent)`.

Update existing response paths:

- `saveRule(...)` returns `isCurrent=false`.
- `publishRule(...)` returns `isCurrent=true` after setting `tasks.current_ai_review_rule_id`.

Do not change:

- Append-only save behavior.
- Publish behavior.
- Validation rules.
- PromptVersionService behavior.

### Backend View + DTO

Modify:

- `services/api/src/main/java/com/labelhub/api/module/ai/service/view/AiReviewRuleView.java`
- `services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewRuleDtoMapper.java`

Add `boolean isCurrent` to the view record and set it on generated `AiReviewRule`.

### Mapper

Modify `services/api/src/main/java/com/labelhub/api/module/ai/mapper/AiReviewRuleMapper.java`.

Current `selectByTaskId` orders by `version_no DESC`. C1.5 must not reverse that existing method. Add a new list-only method such as `selectByTaskIdOrderByVersionAsc(taskId)` that returns `ORDER BY version_no ASC`, and have `listRules` call that new method.

Keep existing `selectByTaskId` DESC behavior and its current contract surface intact. Do not add update/delete methods.

## Tests

### Service Tests

Modify `services/api/src/test/java/com/labelhub/api/module/ai/service/AiReviewRuleServiceTest.java`.

Required cases:

- Empty list for owned task returns `[]`.
- Multi-version list returns version numbers in ASC order.
- `isCurrent=true` only for rule whose id equals `task.currentAiReviewRuleId`.
- All rules return `isCurrent=false` when task pointer is null.
- Cross-owner or missing task returns the existing not-found exception.
- `saveRule` returns `isCurrent=false`.
- `publishRule` returns `isCurrent=true`.
- Prompt version missing during list fails with existing prompt-version not-found behavior.

### Controller Tests

Modify `services/api/src/test/java/com/labelhub/api/module/ai/web/AiReviewControllerTest.java`.

Required cases:

- `listAiReviewRules` delegates to service with current owner id.
- Response status is `200`.
- Returned body is mapped DTO list.

### DTO Mapper Tests

Modify `services/api/src/test/java/com/labelhub/api/module/ai/web/AiReviewRuleDtoMapperTest.java`.

Required case:

- `AiReviewRuleView(..., true)` maps to `AiReviewRule.isCurrent=true`.
- `AiReviewRuleView(..., false)` maps to `false`.

### Mapper Contract Tests

Modify `services/api/src/test/java/com/labelhub/api/module/ai/mapper/AiReviewRuleMapperContractTest.java`.

Required checks:

- Allowed method list includes only insert/select/markPublished.
- Existing `selectByTaskId` SQL still contains `ORDER BY version_no DESC`.
- New ASC list method SQL contains `ORDER BY version_no ASC`.
- No update/delete methods are added.

### OpenAPI Contract Test

Modify `services/api/src/test/java/com/labelhub/api/module/ai/AiReviewRuleInfrastructureContractTest.java`.

Required grep-style checks:

- `operationId: listAiReviewRules`
- `name: taskId` and `required: true`
- `isCurrent` in `AiReviewRule`
- `ErrorBadRequest`, `ErrorUnauthorized`, `ErrorForbidden`, `ErrorNotFound` refs for the list endpoint
- Save/publish error refs are present

## Budget

Hand-authored budget excludes generated files.

| Budget item | Soft cap | Hard cap |
|---|---:|---:|
| Hand-authored files | 11 | 14 |
| Hand-authored net LOC | 550 | 750 |
| Generated files | 5 | 8 |
| Generated net LOC | report only | report only |

The soft file cap is `11` intentionally. The implementation needs separate OpenAPI, controller, service, view, DTO mapper, mapper, and focused test changes. Collapsing tests or hiding DTO mapping coverage inside an unrelated file would make the gate less auditable; `11/14` keeps the estimate honest while preserving a narrow hard cap.

Estimated hand-authored LOC:

| File | Estimated LOC |
|---|---:|
| `packages/contracts/openapi/labelhub.yaml` | 45 |
| `AiReviewController.java` | 20 |
| `AiReviewRuleService.java` | 65 |
| `AiReviewRuleView.java` | 12 |
| `AiReviewRuleDtoMapper.java` | 18 |
| `AiReviewRuleMapper.java` | 20 |
| `AiReviewControllerTest.java` | 45 |
| `AiReviewRuleServiceTest.java` | 90 |
| `AiReviewRuleDtoMapperTest.java` | 40 |
| `AiReviewRuleMapperContractTest.java` | 30 |
| `AiReviewRuleInfrastructureContractTest.java` | 25 |

Estimated total: `410` hand-authored LOC.

Generated churn expected:

- `apps/web/src/shared/api/generated/schema.d.ts`
- Generated Java `AiReviewApi`
- Generated Java `AiReviewRule`
- Possible adjacent generated codegen files

Generated churn must be reported separately and does not count toward the hand-authored LOC cap.

## Baseline Retirement + Re-freeze

C1.5 intentionally changes OpenAPI.

Retired MD5:

```text
b7df19fdb69f8d22b2f0dbdbc845d95d
```

The new MD5 is unknown until implementation. The implementation report must include the raw command output:

```bash
md5sum packages/contracts/openapi/labelhub.yaml
# or macOS:
md5 -q packages/contracts/openapi/labelhub.yaml
```

The reported new MD5 becomes the P4b2 frozen OpenAPI anchor for later C2/C3/C4 work.

Expected anchor changes:

- OpenAPI MD5: changes.
- Migrations: remains `17`.
- humanpending: remains `153`.
- Backend tests: increase.
- Frontend tests: may remain `137`; generated type churn is expected.

## D-Port + Evidence Requirements

Because the audit sandbox may not have Maven Central or a working generated-code path, these are D-port until the implementation report supplies raw output:

- Full backend test summary.
- New OpenAPI MD5.
- Generated Java/TypeScript diff.

Implementation report must include:

```bash
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \\[" humanpending.md
git diff --stat
git diff --check
```

Review zip must include:

- Modified OpenAPI YAML.
- Generated files that changed.
- Hand-authored backend files and tests.

## Risks + STOP Conditions

### Risk 1: task pointer loading

`TaskEntity.currentAiReviewRuleId` exists, but C1.5 implementation must confirm the actual task load path used by `AiReviewRuleService` includes this field. If `taskMapper.selectById(...)` fails to populate the pointer in practice, do not silently compute all `false`; fix the load path or STOP if that requires broader mapper surgery.

### Risk 2: accidental migration

The RESEARCH decision says no migration. If implementation finds that existing indexes cannot support task-scoped ordering or ownership lookup, STOP and report before adding a migration.

### Risk 3: status/current confusion

Do not compute `isCurrent` from `status`. Published status means the rule was published at least once. Current means task pointer equality.

### Risk 4: generated churn blast radius

If codegen touches unrelated generated models or removes fields unexpectedly, report the churn explicitly. Do not hide generated churn inside hand-authored cap.

## Frozen Guardrails

C1.5 must not change:

- Any migration file or count.
- `humanpending.md`.
- Task OpenAPI schema.
- Frontend UI or owner pages.
- PromptVersionService behavior.
- Save append-only behavior.
- Publish markPublished + task-pointer behavior.
- P3a/P3b/P4a business logic except expected generated type churn.
