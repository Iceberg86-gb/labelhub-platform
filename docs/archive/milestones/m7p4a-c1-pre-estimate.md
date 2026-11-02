# M7-P4a C1 Pre-Estimate: Prompt Version Infrastructure

## Status

Pre-estimate gate for M7-P4a C1. No implementation code has landed for this
cluster.

Current anchor: `4b85b12` plus approved P4 research doc in the working tree.
OpenAPI MD5: `2482d531df39e9e12613bf964f3618ea`. Migrations: `11`.
humanpending: `141`. Backend tests: `494 / 81`. Frontend Vitest: `125`.

C1 is expected to change the OpenAPI MD5 and add migrations, but it should not
change AI review runtime behavior yet.

## Baseline Evidence

### Schema Versioning Reference

`schema_versions` is family-scoped:

```sql
CREATE TABLE schema_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schema_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    schema_json JSON NOT NULL,
    field_stable_ids JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    published_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_schema_versions_schema FOREIGN KEY (schema_id) REFERENCES label_schemas(id),
    UNIQUE KEY uk_schema_versions_no (schema_id, version_no),
    UNIQUE KEY uk_schema_versions_hash (schema_id, content_hash)
);
```

P4a differs because AiReviewRule family/root is deliberately deferred to P4b.
There is no parent id available in P4a.

### Current ai_calls Shape

`ai_calls` currently stores prompt evidence as a string and has a single
idempotency unique key:

```sql
prompt_version VARCHAR(80) NOT NULL,
idempotency_key VARCHAR(160) NOT NULL,
UNIQUE KEY uk_ai_calls_idempotency (idempotency_key)
```

It has one FK today:

```sql
CONSTRAINT fk_ai_calls_submission FOREIGN KEY (submission_id) REFERENCES submissions(id)
```

C1 adds nullable `prompt_version_id` plus required/default
`provider_adapter_version`, but it should not change the idempotency key format
or service behavior yet.

### Current OpenAPI Shape

`AiCall.required` currently includes `promptVersion` and does not have
`promptVersionId` or `providerAdapterVersion`.

`TriggerAiReviewRequest.required` currently includes `promptVersion`:

```yaml
TriggerAiReviewRequest:
  type: object
  required: [promptVersion]
  properties:
    promptVersion:
      type: string
```

### Existing Test Pressure

The existing AI review tests heavily use `"prompt-v1"` strings:

- `AiReviewIntegrationTest`
- `QualityLedgerIntegrationTest`
- `AiReviewServiceTest`
- `FailedAiCallRecorderTest`
- `AiReviewDtoMapperTest`

C1 should keep these green by leaving service/controller behavior untouched.
The promptVersionId hard switch should happen in a later vertical slice that
updates tests, service, controller, and frontend together.

## C1 Contract Recommendation

### Add PromptVersion Schema

OpenAPI:

```yaml
PromptVersionStatus:
  type: string
  enum: [draft, published]

PromptVersion:
  type: object
  required: [id, versionNo, contentHash, content, status, createdAt]
  properties:
    id:
      type: integer
      format: int64
    versionNo:
      type: integer
    contentHash:
      type: string
      minLength: 64
      maxLength: 64
    content:
      type: string
    status:
      $ref: '#/components/schemas/PromptVersionStatus'
    ownerId:
      type: integer
      format: int64
      nullable: true
    publishedAt:
      type: string
      format: date-time
      nullable: true
    createdAt:
      type: string
      format: date-time
```

No endpoints in C1. C2 can decide default prompt-version resolution and any
read endpoint after persistence is proven.

### Add AiCall Fields

OpenAPI `AiCall`:

- keep `promptVersion` required;
- add `promptVersionId` nullable integer int64;
- add `providerAdapterVersion` required string.

Recommended `required` update:

```yaml
required:
  [id, submissionId, purpose, promptVersion, providerAdapterVersion,
   providerName, modelName, inputHash, status, idempotencyKey, createdAt]
```

`promptVersionId` stays nullable and outside `required` so legacy rows remain
representable.

### Defer TriggerAiReviewRequest Hard Switch

Research §13.2 approves a P4a hard switch from `promptVersion` to
`promptVersionId`, but C1 should not perform it.

Reason:

- If C1 changes generated request types but not service/frontend behavior,
  backend compile and frontend typecheck will fail.
- If C1 adds temporary controller/frontend shims that coerce id to string, it
  creates misleading evidence and partially implements C3/C4 behavior.
- If C1 accepts both fields, it violates the approved hard-switch direction.

Recommendation:

- C1: additive infrastructure only.
- C3: perform the hard switch in one vertical backend service/controller slice.
- C4: update the frontend trigger and provenance display against the C3
  contract.

This creates one additional OpenAPI MD5 change later in P4a, but preserves the
stronger invariant that every implementation cluster remains independently
green and semantically honest.

## Migration Recommendation

### Migration 12: prompt_versions

Recommended DDL:

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Migration 13: ai_calls additive columns

Recommended DDL:

```sql
ALTER TABLE ai_calls
    ADD COLUMN prompt_version_id BIGINT NULL,
    ADD COLUMN provider_adapter_version VARCHAR(80) NOT NULL DEFAULT 'agent-default-v1',
    ADD CONSTRAINT fk_ai_calls_prompt_version
        FOREIGN KEY (prompt_version_id) REFERENCES prompt_versions(id),
    ADD INDEX idx_ai_calls_prompt_version (prompt_version_id);
```

Split migrations are recommended because they isolate the new immutable asset
table from the existing evidence table alteration. This also makes review and
rollback easier.

## Prompt Version Family Decision

Recommendation: **global P4a prompt versions, no nullable family id**.

Why not pre-reserve nullable `prompt_family_id`:

- MySQL unique indexes allow multiple `NULL` values, so
  `(prompt_family_id, version_no)` would not protect P4a rows while every family
  id is NULL.
- A fake family id or generated coalesce column would introduce family semantics
  before P4b has designed AiReviewRule.
- P4a evidence can safely use global immutable prompt version ids and global
  content hashes.

How P4b can extend this:

- Add `ai_review_rules.current_prompt_version_id`.
- Add a rule-to-prompt association or rule-local version display if needed.
- Keep historical `ai_calls.prompt_version_id` stable; do not reinterpret old
  evidence rows.

This differs from `schema_versions` because schema versions already have
`label_schemas` as a family root. P4a intentionally does not.

## Backend Persistence Plan

Add `PromptVersionEntity`:

- `id`
- `versionNo`
- `content`
- `contentHash`
- `status`
- `ownerId`
- `publishedAt`
- `createdAt`

Add `PromptVersionMapper`:

- `insert`
- `selectById`
- `selectByContentHash`

Defer higher-level default resolution and creation policy to C2.

Update `AiCallEntity`:

- `promptVersionId`
- `providerAdapterVersion`

Update `AiCallMapper`:

- Add `@Result` mappings for `prompt_version_id` and
  `provider_adapter_version`.
- Do not change `@Insert` columns in C1 unless tests prove MyBatis requires it.
  Existing insert SQL should continue to omit the new columns, letting
  `prompt_version_id` be NULL and `provider_adapter_version` use the DB default.

## Test Plan

Backend:

- `PromptVersionMapperTest`: insert/selectById/selectByContentHash round trip.
- Migration/DDL coverage through existing integration test startup or a focused
  mapper test that proves the table and FK columns exist.
- Existing `AiReviewServiceTest`, `AiReviewIntegrationTest`, and
  `QualityLedgerIntegrationTest` remain green.
- `AiReviewDtoMapperTest` can be extended only if mapper/model changes require
  explicit DTO field assertions; otherwise leave it to C3/C5.

Frontend:

- `pnpm --filter @labelhub/web gen:api`.
- Existing typecheck/build should remain green because the trigger request is
  not changed in C1.
- No frontend business tests added in C1.

## File Estimate

| File / surface | Estimate |
|---|---:|
| `labelhub.yaml` | 60 |
| migration 12 `prompt_versions` | 35 |
| migration 13 `ai_calls` alter | 30 |
| `PromptVersionEntity.java` | 70 |
| `PromptVersionMapper.java` | 80 |
| `AiCallEntity.java` / `AiCallMapper.java` additive fields | 45 |
| backend mapper tests | 150 |
| generated TS / Java churn | separate |
| **Hand-authored total** | **470** |

Recommended cap:

- soft cap: 550 hand-authored lines;
- hard cap: 700 hand-authored lines;
- generated churn reported separately.

## Risk Decisions For Adjudication

### 1. Trigger hard switch timing

Recommendation: defer from C1 to C3.

This is the main correction to the initial C1 prompt. It preserves the approved
P4a hard switch while avoiding a knowingly broken C1 commit.

### 2. Prompt family shape

Recommendation: global `prompt_versions` in P4a. Do not add
`prompt_family_id` until P4b has designed the family/root model.

### 3. Migration split

Recommendation: split into two migrations, creating migrations `12` and `13`.

### 4. C1 cap

Recommendation: soft 550 / hard 700 hand-authored lines, generated churn
separate.

## Stop Conditions

- C1 needs to touch `AiReviewService`, `FailedAiCallRecorder`, or idempotency
  key generation.
- C1 needs to change frontend trigger/provenance code.
- Existing AI review tests fail because the additive DB changes are not
  backward-compatible.
- OpenAPI changes include trigger request hard switch despite this gate's
  recommendation.
- Migration requires a fake prompt family/root.
- Hand-authored diff exceeds 700 lines.

## Verification Required For Implementation

- OpenAPI MD5 changes from `2482d531df39e9e12613bf964f3618ea` to a new value.
- migrations count changes from `11` to `13` if split migration is approved.
- backend compile/test pass; existing AI tests remain green.
- frontend `gen:api`, typecheck, and build pass.
- humanpending remains `141`.
- `AiReviewService`, `FailedAiCallRecorder`, frontend trigger/provenance code,
  P3a/P3b validators/evaluators/corpora/renderers all have empty diff.
- `git status --short` clean after commit.

## Gate Summary

C1 should be a clean infrastructure commit:

```text
PromptVersion schema + prompt_versions table + ai_calls additive FK fields +
minimal mapper/entity support.
```

The trigger hard switch remains approved P4a scope, but should land in the
later vertical cluster that can update backend service, controller, frontend,
tests, and idempotency semantics together.
