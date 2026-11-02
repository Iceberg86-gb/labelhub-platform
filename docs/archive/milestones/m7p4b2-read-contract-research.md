# M7-P4b2 Read Contract Research

## Scope

This RESEARCH cluster locks the backend read contract for P4b2 AI review rules before any OpenAPI/backend implementation work.

It is docs-only. It does not modify OpenAPI, generated types, backend code, migrations, or humanpending.

Frozen baseline for this research phase:

- OpenAPI MD5: `b7df19fdb69f8d22b2f0dbdbc845d95d`
- Backend tests: 540 / skipped 88
- Frontend Vitest: 137
- Migrations: 17
- humanpending: 153

## Existing Facts

P4b1 established a two-layer version model:

- `prompt_versions`: global immutable prompt text assets.
- `ai_review_rules`: task-scoped rule versions containing dimensions, threshold, and `current_prompt_version_id`.

P4b1 write endpoints are already in OpenAPI:

- `POST /ai-review/rules`
  - `operationId: saveAiReviewRule`
  - Body: `AiReviewRuleRequest { taskId, promptTemplate, dimensions, threshold }`
  - Response: `201 AiReviewRule`
  - Current contract only lists `201`; it does not list 4xx responses.
- `POST /ai-review/rules/{ruleId}/publish`
  - `operationId: publishAiReviewRule`
  - Response: `200 AiReviewRule`
  - Current contract lists `404` only.

Behavior established by P4b1:

- Save is append-only: every save creates a new draft rule version.
- Publish marks the rule published and sets `tasks.current_ai_review_rule_id`.
- The active rule is the task pointer, not `AiReviewRule.status`.
- Multiple rules for one task may have `status = published`; only the task pointer is current.

Schema version read contract is the closest established versioned-resource pattern:

- `GET /schemas/{schemaId}/versions`
  - Parent id is path-scoped.
  - Returns `array<SchemaVersion>`.
  - Explicitly lists 401 / 403 / 404 via shared response refs.
- `POST /schemas/{schemaId}/versions`
  - Publishes a new immutable version under the same collection path.
  - Explicitly lists 400 / 401 / 403 / 404.
- `GET /schemas/{schemaId}/versions/{versionId}`
  - Reads one immutable version.
  - Explicitly lists 401 / 403 / 404.
- Current schema pointer is exposed on task response as `currentSchemaVersionId`; there is no schema-specific `current` endpoint.

Task current-rule implementation state:

- Backend entity/mapper already has `currentAiReviewRuleId` / `current_ai_review_rule_id`.
- OpenAPI `Task` currently exposes `currentSchemaVersionId` and `currentDatasetId`, but does not expose `currentAiReviewRuleId`.
- `TasksController.toDto` currently sets schema/dataset pointers, not the AI review rule pointer.

ADR constraints:

- ADR-005: AI evidence is not the final verdict. Any "current rule" display is configuration provenance, not automated adjudication.
- ADR-011: provider-specific prompts stay in `services/agent`; owner business prompt/rule configuration stays in LabelHub.

## Decision 1: URL Style

### Options

#### A1. Non-nested list

`GET /ai-review/rules?taskId={taskId}`

Pros:

- Aligns with the already-shipped write endpoints under `/ai-review/rules`.
- Avoids migrating or duplicating existing write paths.
- Keeps all AI review rule operations under one resource family and one OpenAPI tag.
- Requires only one new list endpoint for P4b2 version history UI.

Cons:

- Differs from the schema version list style, which is nested under its parent schema.
- Requires a required query parameter, and missing `taskId` must be documented as 400.

#### A2. Task-nested list

`GET /tasks/{taskId}/ai-review/rules`

Pros:

- Mirrors the schema version family style: parent id in the path, versions as a child collection.
- Makes ownership and task scoping obvious at the URL level.

Cons:

- Splits rule reads from rule writes: writes remain `/ai-review/rules`, publish remains `/ai-review/rules/{ruleId}/publish`.
- Migrating save/publish into nested URLs would be a larger breaking contract cleanup and is not necessary for P4b2.
- Keeping nested reads while writes stay non-nested produces two mental models for the same resource.

### Recommendation

Choose **A1: `GET /ai-review/rules?taskId={taskId}`** for P4b2 v1.

Reasoning:

- P4b1 already made AI review rules a top-level resource in the OpenAPI surface. The least surprising read path is to add the corresponding list operation to the same collection.
- P4b2 needs a task-scoped version history list, not a URL migration. The query parameter preserves task scoping without disturbing write paths.
- Schema versions remain the main versioned-resource precedent, but their write/read paths were designed nested from the beginning. Rule writes were not. For this phase, intra-resource consistency should weigh more than retrofitting cross-object symmetry.

Do not migrate the existing write endpoints in P4b2. A future API cleanup could introduce nested aliases, but it should not block the owner editor.

### Detail Endpoint

Do **not** add a detail endpoint in P4b2 v1.

The version history UI can be served by the list endpoint because `AiReviewRule` already includes prompt text, dimensions, threshold, status, timestamps, and promptVersionId. Save and publish both return the complete rule. A future deep-link/history detail UI can add `GET /ai-review/rules/{ruleId}` later without changing this list decision.

## Decision 2: Current Rule Exposure

### Options

#### 2a. Add `isCurrent` to each list item

`GET /ai-review/rules?taskId={taskId}` returns `AiReviewRule[]`, where each rule includes `isCurrent: boolean`.

Pros:

- Single request gives the UI both history and active marker.
- Keeps current semantics local to the rule history screen.
- Avoids broadening the `Task` contract for pages that do not need rule details.
- Directly encodes the established P4b1 rule: active means `task.current_ai_review_rule_id == rule.id`.

Cons:

- Adds a derived field to `AiReviewRule`.
- Save response for a new draft must return `isCurrent = false`; publish response should return `isCurrent = true`.

#### 2b. Add `currentAiReviewRuleId` to `Task`

Expose the existing backend task pointer in the OpenAPI `Task` schema.

Pros:

- Mirrors `currentSchemaVersionId`.
- Lets task detail compare rule ids without extra metadata on each rule.

Cons:

- Broadens every task DTO and generated frontend type.
- Still requires the list endpoint to render the rule history.
- Adds a task-contract concern for a UI that only needs the marker inside the rule history section.

#### 2c. Add a dedicated current endpoint

`GET /ai-review/rules/current?taskId={taskId}` or similar.

Pros:

- Very explicit.
- Can return 404/no content when no rule is active.

Cons:

- Adds another round trip for a UI that already needs the full list.
- Introduces a current endpoint even though schema versions do not have one.
- Creates more ambiguity around "AI current rule" than a list marker does; ADR-005 wants this to stay provenance/config, not verdict authority.

### Recommendation

Choose **2a: add `isCurrent` to the `AiReviewRule` response**.

Reasoning:

- It is the most efficient shape for P4b2 version history: one list call can render all rule versions and mark the active one.
- It preserves the task contract for now. Although the backend entity has `currentAiReviewRuleId`, OpenAPI currently does not expose it; adding it would affect more frontend surfaces than the rule editor needs.
- It reinforces the correct semantics: current is a task-pointer-derived marker, not `status = published`.

Contract impact:

- Extend `AiReviewRule` with required `isCurrent: boolean`.
- `saveAiReviewRule` should return `isCurrent: false` for a new draft.
- `publishAiReviewRule` should return `isCurrent: true` for the newly activated rule.
- `listAiReviewRules` computes `isCurrent` by comparing each rule id with `tasks.current_ai_review_rule_id`.

Do not add `currentAiReviewRuleId` to `Task` in this read-contract cluster. If a future owner dashboard needs the raw pointer outside rule history, add it in a separate task-contract cluster.

## Decision 3: Error Code Contract

### Read Endpoint Error Codes

`GET /ai-review/rules?taskId={taskId}` should explicitly list:

- `200`: array of `AiReviewRule`
- `400`: `ErrorBadRequest`
  - Missing or invalid `taskId`.
- `401`: `ErrorUnauthorized`
- `403`: `ErrorForbidden`
- `404`: `ErrorNotFound`
  - Task does not exist or is not owned by the current owner. Follow the existing cross-owner concealment style where applicable.

### Existing Write Endpoint Error Docs

Current write contracts under-document real backend behavior:

- `saveAiReviewRule` can fail with validation errors and task ownership/not-found errors.
- `publishAiReviewRule` can fail with unauthorized/forbidden/not-found paths.

### Recommendation

In the read-contract implementation cluster, add explicit shared response refs to the existing write endpoints as well:

- `POST /ai-review/rules`
  - keep `201`
  - add `400`, `401`, `403`, `404`
- `POST /ai-review/rules/{ruleId}/publish`
  - keep `200` and `404`
  - add `401`, `403`

Reasoning:

- The OpenAPI MD5 will change anyway for the read path and `isCurrent`; this is the right time to make AI review rule contracts match the schema version documentation standard.
- This does not require runtime behavior changes. It documents errors already handled by backend exceptions and already treated as expected business failures by C1 frontend hooks.
- It reduces the chance that future hooks assume only success/not-found paths.

Do not add speculative `409` or `422` responses in this cluster. P4b1 validation currently maps to bad request semantics; keep the contract aligned with current implementation.

## Contract Draft

This is the OpenAPI shape to use as the next implementation-gate anchor. It is not applied in this RESEARCH cluster.

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
    post:
      tags: [AIReview]
      operationId: saveAiReviewRule
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AiReviewRuleRequest'
      responses:
        '201':
          description: AI review rule saved.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AiReviewRule'
        '400':
          $ref: '#/components/responses/ErrorBadRequest'
        '401':
          $ref: '#/components/responses/ErrorUnauthorized'
        '403':
          $ref: '#/components/responses/ErrorForbidden'
        '404':
          $ref: '#/components/responses/ErrorNotFound'
  /ai-review/rules/{ruleId}/publish:
    post:
      tags: [AIReview]
      operationId: publishAiReviewRule
      parameters:
        - in: path
          name: ruleId
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: AI review rule published and activated for the task.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AiReviewRule'
        '401':
          $ref: '#/components/responses/ErrorUnauthorized'
        '403':
          $ref: '#/components/responses/ErrorForbidden'
        '404':
          $ref: '#/components/responses/ErrorNotFound'
```

Schema extension:

```yaml
    AiReviewRule:
      type: object
      required:
        [id, taskId, versionNo, promptVersionId, promptTemplate,
         dimensions, threshold, status, isCurrent, createdAt]
      properties:
        isCurrent:
          type: boolean
          description: True when this rule id equals the task current_ai_review_rule_id pointer.
```

No migration is needed for this contract.

## Cluster Split Recommendation

P4b2 C1 has already been scoped as frontend entry + save/publish hooks. With option A accepted, insert a backend read-contract cluster before the history UI depends on it.

Recommended sequence:

1. **C1: Frontend entry + save/publish hooks**
   - Already scoped.
   - Does not depend on read surface.
   - No backend/OpenAPI changes.

2. **C1.5: AI review rule read contract + backend list implementation**
   - Add `GET /ai-review/rules?taskId=...`.
   - Extend `AiReviewRule` with `isCurrent`.
   - Add write endpoint error refs.
   - Regenerate backend/frontend types.
   - Implement service/controller list path and tests.
   - No UI form work.

3. **C2: Editor form body**
   - Prompt textarea, dimensions editor, threshold field, client-side validation.
   - Uses C1 save hook.
   - Can optionally call list endpoint to reset or show latest state, but must not make list a save prerequisite.

4. **C3: Publish + version history UI**
   - Consumes `listAiReviewRules`.
   - Marks `isCurrent`.
   - Calls publish mutation.
   - Handles append-only version history.

5. **C4: End-to-end owner workflow tests and browser verification**
   - Save draft, publish, active marker, no-update-in-place behavior.
   - 1440 / 1280 / 1024 checks for task detail rule panel.

6. **C5: Verification + humanpending**
   - Close P4b2 docs and R8 entries.

Why not merge read implementation into C3:

- C3 would combine OpenAPI/backend/regeneration with version-history UI, making the visual cluster carry backend contract risk.
- C1.5 makes the read contract testable independently and gives C3 a stable generated type surface.

Why not move C2 after C3:

- The form can be built against save/publish contracts and does not need history first.
- Keeping form before history lets the UI become useful incrementally.

## Migration Assessment

No new migration should be required.

Reasoning:

- `ai_review_rules` already has `task_id` and `version_no`.
- The unique key on `(task_id, version_no)` supports ordered task-scoped history reads.
- `tasks.current_ai_review_rule_id` already exists and is loaded by backend `TaskMapper`; list can compare rules against that pointer.
- Exposing `isCurrent` is computed response metadata, not persisted state.

If implementation later finds a missing index, prefer a STOP report before adding migration. Based on the existing task-scoped unique key, no migration is expected.

Migration count should remain 17 for C1.5.

## Baseline Impact When Implemented

This RESEARCH cluster itself keeps the frozen baseline:

- OpenAPI MD5 remains `b7df19fdb69f8d22b2f0dbdbc845d95d`.
- Migrations remain 17.
- humanpending remains 153.

The future C1.5 implementation will intentionally change:

- OpenAPI MD5: required, due to new list endpoint, `isCurrent`, and error-response docs.
- Generated Java/TypeScript types: required regeneration.
- Backend test count: expected to increase for controller/service list coverage.
- Frontend test count: may change only if generated type contract tests are updated in that cluster.

No migration is expected.

## R8 / D-Port Notes

- Backend `TaskEntity` and `TaskMapper` already include `currentAiReviewRuleId`, but OpenAPI `Task` does not. This research therefore does not assume task detail can already expose active rule id to frontend.
- Schema version OpenAPI shape was inspected directly. The backend implementation of schema version current-pointer behavior was not fully audited in this cluster; the contract-level analogy is enough for read contract design, but it remains D-port for backend internals.
- Existing C1 implementation files are present in the working tree during this research, but this research scope adds only this document.
- ADR-005 remains active: exposing current rule only identifies active AI review configuration. It is not an AI verdict and does not weaken the human review accountability gate.
- ADR-011 remains active: this read path exposes owner business prompt/rule configuration. It does not move provider-specific prompts out of `services/agent`.

