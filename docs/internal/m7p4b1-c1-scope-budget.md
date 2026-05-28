# M7-P4b1 C1 Scope-Budget: AI Review Rule Infrastructure

## Status

Pre-estimate gate for M7-P4b1 C1. No implementation code has landed for this
cluster.

Current anchor: `aaf02ea` (`docs: research M7-P4b AI review rule scope`).
OpenAPI MD5 remains `23a67e2cad632b3e9cfaff03c5d05dd7`; migrations `14`;
humanpending `146`; backend tests `516 / 84`; frontend Vitest `131`.

C1 is expected to change OpenAPI and add migrations. It should not implement
the `saveAiReviewRule` service, runtime AI review rule resolution, Owner UI, or
task publish policy changes.

## Phase Character

P4b1 builds the backend rule foundation deferred by P4a:

- `ai_review_rules` task-scoped rule versions;
- a nullable task pointer to the active/current rule;
- expanded `AiReviewRule` response shape;
- minimal persistence entity/mapper surfaces.

P4b1 does **not** reinterpret P4a `prompt_versions`. Prompt versions stay global
immutable assets. `ai_review_rules` points to them.

## Approved Research Premises

From `docs/internal/m7p4b-research.md`:

- `promptTemplate` remains the owner-facing request input for P4b v1.
- Backend service will convert prompt text to a `prompt_versions` row, then
  store `ai_review_rules.current_prompt_version_id`.
- `dimensions` and `threshold` are rule-level configuration, not prompt text.
- AI review rule is optional for task publish in P4b v1.
- P4b should split into P4b1 backend foundation and P4b2 Owner editing UI.

## Allowed Surfaces

OpenAPI and generated types:

- `packages/contracts/openapi/labelhub.yaml`
- generated frontend/backend types after regeneration

Database:

- new migration(s) under `services/api/src/main/resources/db/migration/`

Backend persistence:

- `AiReviewRuleEntity`
- `AiReviewRuleMapper`
- `TaskEntity` nullable `currentAiReviewRuleId` field if the pointer column is
  added in C1

Tests:

- migration contract tests;
- mapper/entity round-trip tests;
- generated type compile coverage through existing build.

## Forbidden Surfaces

- `AiReviewController.saveAiReviewRule` implementation; keep the 501 stub.
- `AiReviewService` runtime behavior.
- `PromptVersionService` behavior.
- `FailedAiCallRecorder`.
- Provider adapter code.
- Frontend Owner UI and trigger/provenance components.
- P3a/P3b validators, linkage evaluators, renderers, corpora, and designer code.
- `humanpending.md`.

## Scope

### 1. OpenAPI Contract

Keep `AiReviewRuleRequest` owner-facing:

```yaml
AiReviewRuleRequest:
  type: object
  required: [taskId, promptTemplate, dimensions, threshold]
  properties:
    taskId: int64
    promptTemplate: string
    dimensions: string[]
    threshold: number
```

Replace the current `AiReviewRule = allOf(AiReviewRuleRequest + id)` response
with an independent response schema:

```yaml
AiReviewRuleStatus:
  type: string
  enum: [draft, published]

AiReviewRule:
  type: object
  required:
    [id, taskId, versionNo, promptVersionId, promptTemplate,
     dimensions, threshold, status, createdAt]
  properties:
    id: int64
    taskId: int64
    versionNo: integer
    promptVersionId: int64
    promptTemplate: string
    dimensions: string[]
    threshold: number
    status: AiReviewRuleStatus
    createdAt: date-time
    activatedAt: nullable date-time
```

Do not add conclusion strategy fields in C1.

### 2. Database

Create `ai_review_rules`:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `task_id BIGINT NOT NULL`
- `version_no INT NOT NULL`
- `current_prompt_version_id BIGINT NOT NULL`
- `dimensions_json JSON NOT NULL`
- `threshold DECIMAL(8,4) NOT NULL`
- `status VARCHAR(32) NOT NULL DEFAULT 'draft'`
- `created_by BIGINT NOT NULL`
- `created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`
- `activated_at DATETIME(3)`
- FK to `tasks(id)`
- FK to `prompt_versions(id)`
- FK to `users(id)`
- unique `(task_id, version_no)`

Add `tasks.current_ai_review_rule_id BIGINT NULL` plus a post-table FK to
`ai_review_rules(id)`.

Do not add `ai_calls.ai_review_rule_id` in C1.

### 3. Migration Split

Recommended split:

1. `V20261203xxxx__add_ai_review_rules.sql`
2. `V20261203xxxx__alter_tasks_current_ai_review_rule.sql`

This moves migrations from `14` to `16`.

Reasoning:

- the table and active task pointer are separate review surfaces;
- the second migration makes the circular FK order explicit;
- it mirrors P4a's split between creating a new table and altering an existing
  evidence table.

### 4. Minimal Entity/Mapper

Add:

- `AiReviewRuleEntity`
- `AiReviewRuleMapper`

Minimum mapper methods:

- `insert`
- `selectById`
- `selectByTaskId`
- `selectMaxVersionByTaskId`
- optionally `selectCurrentByTaskId` if the tasks pointer is added and cheap to
  expose.

No `AiReviewRuleService` in C1.

## Key Decisions

| Decision | Recommendation |
| --- | --- |
| tasks current rule pointer | Add nullable `tasks.current_ai_review_rule_id` in C1 |
| ai_calls rule evidence FK | Defer `ai_calls.ai_review_rule_id` to runtime integration cluster |
| AiReviewRule response | Independent response schema, not `allOf` request |
| conclusion strategy | Defer; P4b1 v1 uses threshold-derived behavior only |
| migration count | `14 -> 16` |
| cap | soft `500`, hard `700` hand-authored lines; generated churn separate |

## Risk Controls

### Circular FK

Migration order must be:

1. create `ai_review_rules` with FK to `tasks`;
2. alter `tasks` with nullable `current_ai_review_rule_id`;
3. add FK from `tasks.current_ai_review_rule_id` to `ai_review_rules(id)`.

### Optional Publish Guard

Adding the nullable task pointer does not make AI review rule mandatory. C1 must
not change `TaskService.canPublish(...)`.

### Evidence Completeness

Deferring `ai_calls.ai_review_rule_id` is acceptable only because runtime rule
resolution is deferred. The C3 runtime cluster must revisit it before AI calls
start using task-bound review rules.

### Generated Type Impact

`saveAiReviewRule` is currently a 501 stub and frontend consumption is minimal.
The response schema can be changed now without breaking real behavior, but code
generation must be run and inspected.

## Estimate

| Area | Hand-authored estimate |
| --- | ---: |
| OpenAPI `AiReviewRuleStatus` + independent `AiReviewRule` schema | 70 |
| Migration 1: `ai_review_rules` | 40 |
| Migration 2: task pointer + FK | 25 |
| `AiReviewRuleEntity` | 65 |
| `AiReviewRuleMapper` | 80 |
| `TaskEntity` field | 10 |
| Mapper/migration tests | 150 |
| Generated-churn review notes | 20 |
| **Total** | **460** |

Recommended cap: soft `500`, hard `700`.

Generated OpenAPI Java/TS churn is recorded separately and does not count
against the hand-authored cap.

## Expected Frozen Checks After Implementation

- OpenAPI MD5 changes from `23a67e2cad632b3e9cfaff03c5d05dd7`.
- Migrations move from `14` to `16`.
- humanpending remains `146`.
- `saveAiReviewRule` still returns 501.
- `AiReviewService`, `PromptVersionService`, provider code, frontend UI, P3a,
  and P3b logic remain unchanged.
- Existing backend/frontend tests remain green.
