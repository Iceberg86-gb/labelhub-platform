# M7-P4b Research: AiReviewRule and Owner Prompt Editing

## 1. Status

M7-P4a is closed at the prompt-version foundation layer. The current anchor is:

| Item | Value |
| --- | --- |
| OpenAPI MD5 | `23a67e2cad632b3e9cfaff03c5d05dd7` |
| Backend tests | `516 / 84` |
| Frontend tests | `131` |
| Migrations | `14` |
| humanpending count | `146` |

P4b is the deferred half of prompt versioning: `AiReviewRule` family/root semantics, Owner prompt and scoring-rule editing, real prompt content, publish UI, and any task-level guard around AI review configuration.

This is a research-only record. It does not change production code, OpenAPI, migrations, or humanpending.

## 2. Evidence Gathered

### Existing Contract

OpenAPI already exposes `POST /ai-review/rules` with operation id `saveAiReviewRule`, but the backend controller still returns a `501` stub. The existing `AiReviewRuleRequest` is:

```yaml
required: [taskId, promptTemplate, dimensions, threshold]
properties:
  taskId: integer int64
  promptTemplate: string
  dimensions: string[]
  threshold: number
```

Generated frontend types mirror that shape. This means P4b is not inventing a brand-new API idea. It is implementing and correcting an existing M3-era contract.

### Backend Stubs

`AiReviewController.saveAiReviewRule(...)` throws:

```java
new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "AI review draft endpoint is not implemented in M3")
```

There is no `ai_review_rules` table, Java entity, mapper, service, or owner-facing editor implementation today.

### P4a Prompt Version Foundation

P4a added global immutable `prompt_versions`:

```sql
CREATE TABLE prompt_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_no INT NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    owner_id BIGINT,
    published_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_prompt_versions_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    UNIQUE KEY uk_prompt_versions_no (version_no),
    UNIQUE KEY uk_prompt_versions_hash (content_hash)
)
```

P4a deliberately kept this table global. It did not add rule family ids or reinterpret existing evidence rows.

### Adjudication Rule Pattern

`adjudication_rules` is task-scoped and versioned:

```sql
CREATE TABLE adjudication_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    rule_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    activated_at DATETIME(3),
    CONSTRAINT fk_adjudication_rules_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_adjudication_rules_creator FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_adjudication_rules_task_version (task_id, version_no)
)
```

This is the closest local pattern for `ai_review_rules`.

### Task Publish Guard

`TaskService.canPublish(...)` currently checks quota, deadline, current schema version, and current dataset. It has a TODO for adjudication rule id, but no AI review rule guard. Existing tests and integration fixtures publish tasks without review-rule configuration.

### Design Documents

The complete design baseline says:

- Owner configures prompt template, variables, scoring dimensions, threshold, and conclusion strategy.
- Each configuration save becomes a fixed `prompt_version`.
- AI idempotency and explainability rely on fixed prompt version, model version, structured schema, and input hash.
- AI evidence is visible to reviewers, but ADR-005 says AI evidence does not directly own the final verdict.

ADR-011 says provider-specific prompts, model names, and output adapters stay in `services/agent`. P4a interpreted that as a split-source model:

- Owner business prompt assets live in LabelHub DB.
- Provider adapter prompt/version behavior remains an agent concern.

P4b must preserve that split.

## 3. Q1: AiReviewRule and PromptVersion Junction

### Options

| Option | Shape | Assessment |
| --- | --- | --- |
| A | `saveAiReviewRule` creates or reuses a `prompt_versions` row, then stores an `ai_review_rules.current_prompt_version_id` pointer | Recommended |
| B | `ai_review_rules` stores `promptTemplate` directly and does not use `prompt_versions` | Rejects P4a's foundation |
| C | Request accepts only `promptVersionId`; caller must create prompt version first | Too awkward for Owner editing v1 |

### Recommendation

Use option A.

P4b should keep `promptTemplate` as the owner-facing input in `AiReviewRuleRequest`, but the backend should immediately convert that text into an immutable `prompt_versions` row via `PromptVersionService.create(promptTemplate, ownerId)`. The `ai_review_rules` row then stores `current_prompt_version_id`.

That gives the Owner editor the natural UX: edit text, dimensions, and threshold in one form. It also preserves P4a's evidence model: AI calls can bind to immutable `prompt_version_id`.

### Contract Direction

P4b should evolve `AiReviewRule` response to expose at least:

- `id`
- `taskId`
- `versionNo`
- `promptVersionId`
- `promptTemplate` for display
- `dimensions`
- `threshold`
- `status`
- `createdAt`
- `activatedAt`

The request can retain `promptTemplate`, `dimensions`, and `threshold` for v1. Requiring clients to pre-create a prompt version would add an extra owner workflow that the current UI does not have.

### Why Not Store Prompt Only on Rule

If the rule stores prompt text directly, `prompt_versions` becomes an evidence-only side table and P4a's central invariant weakens. We would also need a second prompt hash/versioning story inside `ai_review_rules`. That duplicates P4a and risks drift.

### Why Not Request PromptVersionId Only

That would be clean for machines but poor for the first Owner editor. The owner thinks in prompt text, dimensions, and threshold. The backend can own the immutable conversion.

## 4. Q2: ai_review_rules Table and Prompt Family Semantics

### Recommendation

Add a task-scoped `ai_review_rules` table modeled after `adjudication_rules`, but do not add a family column to `prompt_versions`.

Suggested first-cut DDL shape:

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
    CONSTRAINT fk_ai_review_rules_prompt_version FOREIGN KEY (current_prompt_version_id) REFERENCES prompt_versions(id),
    CONSTRAINT fk_ai_review_rules_creator FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_ai_review_rules_task_version (task_id, version_no)
)
```

Add a nullable active pointer on `tasks` only if P4b includes runtime rule resolution:

```sql
ALTER TABLE tasks ADD COLUMN current_ai_review_rule_id BIGINT NULL;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_current_ai_review_rule
  FOREIGN KEY (current_ai_review_rule_id) REFERENCES ai_review_rules(id);
```

Because `ai_review_rules` also references `tasks`, this FK likely needs to be added after table creation.

### PromptVersion Relationship

Keep `prompt_versions` global. P4a explicitly recorded that P4b should add rule containers or pointers without retroactively reinterpreting P4a evidence rows. A pointer from `ai_review_rules.current_prompt_version_id` satisfies that.

### Runtime Evidence Gap to Close

Prompt text alone is not enough to reproduce AI review scoring. Dimensions and threshold also affect behavior. P4b should therefore consider adding a nullable `ai_calls.ai_review_rule_id` or `ai_calls.review_rule_version_id` FK when the runtime starts using rules.

Recommended:

- P4b1 adds `ai_review_rules`.
- The runtime integration cluster adds `ai_calls.ai_review_rule_id` if AI calls begin using task-bound rules.
- Legacy rows remain nullable, matching the additive style from P4a.

Without that FK, P4b would have prompt-version provenance but not full rule-version provenance.

## 5. Q3: Where Dimensions and Threshold Live

### Options

| Option | Shape | Assessment |
| --- | --- | --- |
| A | Put prompt text, dimensions, and threshold into `prompt_versions.content` as JSON | Strong immutability, but breaks P4a's content-as-text model |
| B | Store prompt text in `prompt_versions`, store dimensions and threshold in `ai_review_rules` | Recommended |
| C | Store everything only in `ai_review_rules` | Undercuts P4a prompt asset |

### Recommendation

Use option B.

`prompt_versions.content` should stay the owner business prompt text. `ai_review_rules` should own dimensions and threshold. The rule row is the complete review configuration version. It points to the immutable prompt text version and contains the scoring configuration.

Changing prompt text creates or reuses a `prompt_versions` row and creates a new `ai_review_rules` version. Changing dimensions or threshold also creates a new `ai_review_rules` version. It may reuse the same `prompt_version_id` if prompt text is unchanged.

This gives two stable layers:

- `prompt_versions`: immutable prompt text asset, globally deduplicated by content hash.
- `ai_review_rules`: task-scoped review configuration version, including dimensions, threshold, and prompt pointer.

### Baseline Compatibility

The baseline says each Owner configuration save becomes a `prompt_version`. P4a narrowed `prompt_versions` to prompt text. P4b should document that the full Owner configuration is represented by the `ai_review_rules` version, with `current_prompt_version_id` as the text component. If audit wording needs a single label, use "AI review rule version" for the full prompt + dimensions + threshold bundle.

## 6. Q4: Task Publish Guard

### Options

| Option | Shape | Assessment |
| --- | --- | --- |
| A | AI review rule required before task publish | Strong policy, high compatibility cost |
| B | AI review rule optional; AI review is opt-in | Recommended for P4b v1 |
| C | Required only for tasks with an explicit `ai_review_required` flag | Good later policy, needs more product surface |

### Recommendation

Use option B for P4b v1.

Do not add a hard `current_ai_review_rule_id` publish guard in the first P4b cut.

Reasons:

- ADR-005 says AI evidence does not own the final verdict. Human review remains accountable.
- Existing tasks, tests, and manual flows publish without AI review rules.
- The current publish guard chain already carries schema and dataset prerequisites. Adding an AI rule prerequisite would be a broad product policy change, not just implementation.
- P4b can still let tasks with rules use task-bound AI review, while tasks without rules continue with P4a default prompt behavior or no AI review.

### Compatibility Plan

If P4b adds `tasks.current_ai_review_rule_id`, keep it nullable. Do not reject publish when it is null.

Runtime resolution can use:

1. task current AI review rule when present;
2. P4a default prompt version fallback when absent;
3. explicit no-AI behavior if product later decides to make AI review opt-in per task.

Future work can add a boolean `ai_review_required` or task setting, then guard only when that flag is true.

## 7. Q5: Owner Editor Scope

### P4b v1 Minimum

The Owner editor should be practical but not overbuilt:

- Prompt template textarea.
- Dimensions editor with add/remove/reorder simple string inputs.
- Threshold numeric input.
- Save draft.
- Publish or activate current rule.
- Version summary/history list with version number, status, prompt version id, threshold, and created time.
- Basic validation: non-empty prompt, at least one dimension, threshold range.

### Defer

Defer these to P4b v2 or later:

- Rich text prompt editor.
- Variable picker or templating assistant.
- Prompt diff and rollback UI.
- Dimension weighting.
- Test-run prompt preview against a submission.
- Provider-specific adapter prompt editing.
- Full rule comparison tooling.

### Suggested UI Placement

Use the task detail area as the entry point, near schema/dataset/publish readiness. This matches the task-scoped rule design. A standalone global prompt admin page would imply global rule families, which is not the recommended P4b v1 shape.

### Schema Designer Analogy

Follow the schema designer's model at a smaller scale:

- draft editing happens in the page session;
- publish creates an immutable or versioned record;
- version history is visible but not necessarily full diff-capable in v1.

## 8. Q6: Cluster Split Recommendation

P4b is larger than P4a because it adds a real Owner editor plus a new task-scoped versioned domain object. Split P4b into P4b1 and P4b2.

### Recommended Split

#### P4b1: Backend Rule Foundation

| Cluster | Scope |
| --- | --- |
| RESEARCH | This document |
| C1 | OpenAPI contract + migrations for `ai_review_rules`, optional `tasks.current_ai_review_rule_id`, optional `ai_calls.ai_review_rule_id` |
| C2 | Backend `AiReviewRuleEntity/Mapper/Service`, save draft, publish/activate, prompt-version pointer creation |
| C3 | Backend runtime integration: AI review resolves task rule when present, binds `ai_review_rule_id`, preserves P4a default fallback |
| C4 | Backend integration tests, legacy behavior, publish optionality, provenance rule FK |
| C5 | P4b1 verification + humanpending |

#### P4b2: Owner Editing UI

| Cluster | Scope |
| --- | --- |
| C1 | Frontend API hooks/types and task detail entry |
| C2 | Rule editor form: prompt textarea, dimensions, threshold, save draft |
| C3 | Publish/activate UI and version history |
| C4 | Browser/manual checks and integration tests |
| C5 | Verification + humanpending |

### Why Split

A single P4b would combine a new versioned backend domain, runtime AI review binding, task page UX, version UI, and browser verification. That would be larger than P4a and more like P3b in risk. Splitting keeps the backend evidence semantics reviewable before UI pressure accumulates.

### If Kept as One P4b

If the team wants one continuous P4b, keep the same boundary order:

1. contract/migration;
2. backend rule service;
3. runtime integration;
4. frontend editor;
5. integration/browser tests;
6. verification.

Do not start with UI before the rule-to-prompt-version junction is locked.

## 9. Pattern Comparison

| Concern | `schema_versions` | `adjudication_rules` | P4a `prompt_versions` | Recommended P4b `ai_review_rules` |
| --- | --- | --- | --- | --- |
| Scope | schema family | task | global prompt text | task |
| Version number | family-scoped | task-scoped | global | task-scoped |
| Immutable content | schema JSON | rule JSON | prompt text | rule config row |
| Current pointer | `tasks.current_schema_version_id` | not wired yet | none | nullable `tasks.current_ai_review_rule_id` if runtime uses rules |
| Evidence binding | session schema version | current verdict/export FKs | `ai_calls.prompt_version_id` | proposed `ai_calls.ai_review_rule_id` |
| P4b rule | not applicable | pattern source | keep unchanged | pointer to prompt version |

## 10. P4a/P4b Junction Checklist

P4b should preserve these P4a decisions:

- Do not add family ids to `prompt_versions`.
- Do not reinterpret historical `ai_calls.prompt_version_id`.
- Keep `provider_adapter_version` as an agent-side concept.
- Keep `ai_calls.prompt_version` legacy label readable.
- If runtime starts using rule prompt text, record the rule version used.
- Keep tasks without review rules compatible unless a separate product decision makes AI review mandatory.

## 11. Key Risks

### Rule Version vs Prompt Version Confusion

P4a created prompt text versions. P4b creates review rule versions. Documentation, DTO names, and UI labels must distinguish them.

Recommended labels:

- "Prompt ID" for `promptVersionId`.
- "Review Rule Version" for `ai_review_rules.version_no`.
- "Adapter" for `providerAdapterVersion`.

### Evidence Half-Binding

If P4b only records `promptVersionId` but not the rule id, dimensions and threshold are not fully reproducible. Add rule evidence binding when runtime begins using task rules.

### Publish Guard Blast Radius

A hard rule guard would break many existing flows. Keep P4b v1 optional unless a separate adjudication says AI review is mandatory.

### Provider Boundary

Owner business prompt is not the same as provider-specific prompt. If passed to the LLM, it should be treated as business instructions/input context, not as the provider adapter's system prompt source.

### UI Scope Creep

Prompt editing invites rich editor, variable picker, preview, diff, and rollback. P4b v1 should stay text + dimensions + threshold + publish.

## 12. P4b Start Anchor Estimate

Expected changes after this research, subject to C1 gate:

| Item | Expected Movement |
| --- | --- |
| OpenAPI MD5 | Will change from `23a67e2cad632b3e9cfaff03c5d05dd7` |
| Migrations | `14 -> 15+` for `ai_review_rules`; likely `16+` if adding task/rule or ai_call rule FK columns |
| humanpending | `146 -> higher` at P4b verification |
| Backend tests | Will rise across P4b1 |
| Frontend tests | Will rise across P4b2 |

## 13. Recommended C1 Gate Starting Point

For P4b1 C1, ask the implementation agent to estimate:

- OpenAPI changes:
  - expand `AiReviewRule` response with `versionNo`, `promptVersionId`, `status`, timestamps;
  - decide whether `TriggerAiReviewRequest` remains `promptVersionId` or starts moving toward task-bound rule resolution;
  - add `aiReviewRuleId` to `AiCall` only if runtime binding is in P4b1.
- Migrations:
  - `ai_review_rules`;
  - optional `tasks.current_ai_review_rule_id`;
  - optional `ai_calls.ai_review_rule_id`.
- Backend entities/mappers.
- Guard decision explicitly set to optional.
- No Owner UI yet.

## 14. Research Conclusion

P4b should not mutate P4a's global prompt asset model. It should add a task-scoped `ai_review_rules` layer that points to prompt versions and owns dimensions, threshold, status, and task activation.

Recommended path:

1. P4b1 backend rule foundation and runtime evidence binding.
2. P4b2 Owner editor and publish UI.

This keeps P4a's evidence binding intact while finally giving Owners real prompt/rule configuration.
