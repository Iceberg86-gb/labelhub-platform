# M7-P4b1 C1 Pre-Estimate: AI Review Rule Infrastructure

## Status

Pre-estimate gate for M7-P4b1 C1. No implementation code has landed for this
cluster.

Current anchor: `aaf02ea` (`docs: research M7-P4b AI review rule scope`).
OpenAPI MD5 remains `23a67e2cad632b3e9cfaff03c5d05dd7`; migrations `14`;
humanpending `146`; backend tests `516 / 84`; frontend Vitest `131`.

C1 is expected to change the OpenAPI MD5 and add migrations. It should not
change AI review runtime behavior, task publish behavior, or Owner UI.

## Baseline Evidence

### Current AiReviewRule Contract

OpenAPI already exposes `POST /ai-review/rules`, but it is not implemented.
The current request is:

```yaml
AiReviewRuleRequest:
  required: [taskId, promptTemplate, dimensions, threshold]
```

The current response is only:

```yaml
AiReviewRule:
  allOf:
    - AiReviewRuleRequest
    - { id }
```

This is too small for P4b because the response needs rule version identity and
the linked prompt version id.

### Controller Stub

`AiReviewController.saveAiReviewRule(...)` currently throws a `501` with
"AI review draft endpoint is not implemented in M3". C1 must not implement this
controller; C2 owns service behavior.

### Existing Rule Pattern

`adjudication_rules` is task-scoped and versioned:

```sql
task_id BIGINT NOT NULL,
version_no INT NOT NULL,
rule_json JSON NOT NULL,
status VARCHAR(32) NOT NULL DEFAULT 'draft',
created_by BIGINT NOT NULL,
activated_at DATETIME(3),
UNIQUE KEY uk_adjudication_rules_task_version (task_id, version_no)
```

P4b1 should reuse this task-scoped versioning pattern for `ai_review_rules`.

### P4a Prompt Versions

P4a `prompt_versions` is global:

```sql
version_no INT NOT NULL,
content TEXT NOT NULL,
content_hash CHAR(64) NOT NULL,
UNIQUE KEY uk_prompt_versions_no (version_no),
UNIQUE KEY uk_prompt_versions_hash (content_hash)
```

P4b1 must not add family columns to `prompt_versions` or reinterpret historical
P4a evidence rows.

### Task Pointer Pattern

`tasks.current_schema_version_id` is nullable. Its FK is added after the
referenced table exists. P4b1 should follow that pattern for
`tasks.current_ai_review_rule_id`.

## C1 Contract Recommendation

### Keep AiReviewRuleRequest Owner-Facing

Do not replace `promptTemplate` with `promptVersionId` in the request.

Reason:

- The Owner editor naturally edits prompt text.
- The backend can convert prompt text to a `prompt_versions` row in C2.
- Requiring a pre-created prompt version id would expose the infrastructure
  layer to the Owner UI too early.

### Replace AiReviewRule Response With Independent Schema

Recommended schema:

```yaml
AiReviewRuleStatus:
  type: string
  enum: [draft, published]

AiReviewRule:
  type: object
  required:
    - id
    - taskId
    - versionNo
    - promptVersionId
    - promptTemplate
    - dimensions
    - threshold
    - status
    - createdAt
  properties:
    id:
      type: integer
      format: int64
    taskId:
      type: integer
      format: int64
    versionNo:
      type: integer
    promptVersionId:
      type: integer
      format: int64
    promptTemplate:
      type: string
    dimensions:
      type: array
      items:
        type: string
    threshold:
      type: number
    status:
      $ref: '#/components/schemas/AiReviewRuleStatus'
    createdAt:
      type: string
      format: date-time
    activatedAt:
      type: string
      format: date-time
      nullable: true
```

Why independent schema:

- `allOf(request + id)` hides the response-only fields.
- C2/C3 will need `versionNo`, `promptVersionId`, status, and timestamps.
- Generated Java/TS types will be clearer when the response is explicit.

## Migration Recommendation

### Migration 15: ai_review_rules

```sql
CREATE TABLE ai_review_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    current_prompt_version_id BIGINT NOT NULL,
    dimensions_json JSON NOT NULL,
    threshold DECIMAL(8,4) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    activated_at DATETIME(3),
    CONSTRAINT fk_ai_review_rules_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_ai_review_rules_prompt_version
        FOREIGN KEY (current_prompt_version_id) REFERENCES prompt_versions(id),
    CONSTRAINT fk_ai_review_rules_creator FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_ai_review_rules_task_version (task_id, version_no),
    INDEX idx_ai_review_rules_task_status (task_id, status),
    INDEX idx_ai_review_rules_prompt_version (current_prompt_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Migration 16: tasks current rule pointer

```sql
ALTER TABLE tasks
    ADD COLUMN current_ai_review_rule_id BIGINT NULL,
    ADD CONSTRAINT fk_tasks_current_ai_review_rule
        FOREIGN KEY (current_ai_review_rule_id) REFERENCES ai_review_rules(id),
    ADD INDEX idx_tasks_current_ai_review_rule (current_ai_review_rule_id);
```

Split migrations are recommended because they make the circular FK direction
explicit and keep the existing-table alteration isolated from the new table.

## Decision 2: tasks.current_ai_review_rule_id

Recommendation: **add it in C1**.

Reasons:

- It is part of the infrastructure, not service behavior.
- It mirrors `tasks.current_schema_version_id`.
- It is nullable, so it does not change publish behavior or existing tasks.
- C2 can use it when publishing/activating a rule without adding another
  migration.

Rejected alternative: defer to C3. That would force runtime integration to also
own a schema migration and would blur C3's purpose.

## Decision 3: ai_calls.ai_review_rule_id

Recommendation: **defer to the runtime integration cluster**.

Reasons:

- C1 does not make runtime AI calls use review rules.
- A nullable column added now would remain empty and provide no evidence value.
- C3 is the cluster that must decide when an AI call is actually bound to a
  task's rule version.

Risk to carry forward:

- Once runtime begins using `ai_review_rules`, evidence is incomplete unless
  `ai_calls` records the rule id. C3 must add or explicitly re-adjudicate this.

Rejected alternative: add the column in C1. That gives a false sense of evidence
completeness while no runtime code writes it.

## Decision 4: Response Schema

Recommendation: independent `AiReviewRule` response schema plus
`AiReviewRuleStatus` enum.

Generated type impact should be small because `saveAiReviewRule` is not
implemented and frontend usage is not active. C1 must still regenerate types and
inspect compile output.

## Decision 5: Conclusion Strategy

Recommendation: **defer independent conclusion strategy**.

P4b1 v1 should model:

- prompt template;
- dimensions;
- threshold.

The baseline's three outcomes (pass, reject, manual review) describe AI output
classification: pass when score meets threshold and no high-risk issue exists,
reject for obvious failure, manual review for borderline or unstable output.
That can be derived by runtime scoring logic and does not need a separate C1 DB
field.

If future product work needs configurable three-zone thresholds or custom
conclusion mapping, add `conclusion_strategy_json` in a later cluster.

Suggested C5/C6 watch wording:

`[M7-P4b1 watch] Conclusion strategy remains threshold-derived in v1. P4b1 stores prompt text, dimensions, and threshold; richer configurable pass/reject/manual-review mapping is deferred until scoring calibration work requires it.`

## Minimal Backend Persistence

### AiReviewRuleEntity

Fields:

- `id`
- `taskId`
- `versionNo`
- `currentPromptVersionId`
- `dimensionsJson`
- `threshold`
- `status`
- `createdBy`
- `createdAt`
- `activatedAt`

Use the local entity naming style from `PromptVersionEntity` and other MyBatis
entities.

### AiReviewRuleMapper

Minimum methods:

- `insert(AiReviewRuleEntity entity)`
- `selectById(Long id)`
- `selectByTaskId(Long taskId)`
- `selectMaxVersionByTaskId(Long taskId)`
- `selectCurrentByTaskId(Long taskId)` if implementing through the task pointer
  is convenient, otherwise C2 can join later.

No service in C1.

### TaskEntity

Add `currentAiReviewRuleId` only if the migration adds the task pointer.

Do not update `TaskService.canPublish(...)`.

## Generated Types and Codegen

C1 must regenerate:

- frontend OpenAPI schema types;
- backend generated OpenAPI models.

Expected generated changes:

- `AiReviewRuleRequest` remains source input.
- `AiReviewRule` gains response-only fields.
- `AiReviewRuleStatus` enum appears.

There should be no runtime consumer break because the endpoint remains a stub.

## Tests

Recommended focused tests:

1. Migration contract test:
   - `ai_review_rules` table exists;
   - `tasks.current_ai_review_rule_id` column exists and is nullable;
   - FKs and indexes are present enough to prove migrations applied.
2. Mapper test:
   - insert rule with seeded/default prompt version id;
   - select by id;
   - select by task id;
   - max version by task id.
3. Guard regression:
   - existing task publish tests still pass with null `current_ai_review_rule_id`.

No controller/service behavior tests in C1 because `saveAiReviewRule` remains
501.

## Risk Register

### Circular FK

`ai_review_rules` references `tasks`, and `tasks.current_ai_review_rule_id`
references `ai_review_rules`. Migration order must be create table first, alter
tasks second.

### Optional Guard Drift

Adding a task pointer must not accidentally become a publish guard. Tests around
`TaskService.canPublish(...)` should remain unchanged.

### Generated Schema Drift

Changing `AiReviewRule` from `allOf` to independent object can affect generated
names. Since the endpoint is a stub, this is acceptable, but compile/typecheck
must verify it.

### Evidence Half-Binding

Deferring `ai_calls.ai_review_rule_id` is intentional for C1, but C3 must close
it before rule-based runtime AI calls ship.

## Estimate

| Area | Hand-authored estimate |
| --- | ---: |
| OpenAPI schemas | 70 |
| Migration 15 | 45 |
| Migration 16 | 30 |
| `AiReviewRuleEntity` | 65 |
| `AiReviewRuleMapper` | 90 |
| `TaskEntity` field | 10 |
| Migration and mapper tests | 160 |
| Gate/report glue | 20 |
| **Total** | **490** |

Recommended cap: soft `500`, hard `700`.

Generated churn is tracked separately.

## C1 Implementation Boundaries

Allowed:

- OpenAPI schema changes described above.
- Migrations 15 and 16.
- `AiReviewRuleEntity`.
- `AiReviewRuleMapper`.
- `TaskEntity.currentAiReviewRuleId`.
- Focused migration/mapper tests.
- Generated type regeneration.

Forbidden:

- Implementing `saveAiReviewRule`.
- Creating `AiReviewRuleService`.
- Changing `AiReviewService`.
- Changing `PromptVersionService`.
- Adding AI review rule publish guard.
- Adding `ai_calls.ai_review_rule_id`.
- Changing frontend business code.
- Changing P3a/P3b code or corpora.
- Editing `humanpending.md`.

## Verification Expectations for Implementation

- OpenAPI MD5 changes from `23a67e2cad632b3e9cfaff03c5d05dd7`.
- Migrations move `14 -> 16`.
- humanpending remains `146`.
- Existing backend tests remain green.
- Frontend typecheck/build remains green after regenerated types.
- `saveAiReviewRule` remains a 501 stub.
- `TaskService.canPublish(...)` behavior is unchanged.
- git diff shows no runtime AI review or frontend UI changes.
