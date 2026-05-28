# M7-P4a C2 Pre-Estimate: Prompt Version Service + Default Resolution

## Status

Pre-estimate gate for M7-P4a C2. This document adds no production code.

Current anchor is the approved C1 implementation in the working tree:
OpenAPI MD5 `c1ee4d213661b881344e59f0ab079f4a`, migrations `13`,
humanpending `141`, backend tests `504 / 81`, frontend Vitest `125`.

C2 should make prompt versions usable by backend and frontend callers without
switching the AI-review trigger contract yet.

## Baseline Evidence

### Existing Prompt Trigger

The frontend still uses a hard-coded string:

```ts
const DEFAULT_PROMPT_VERSION = 'm3-owner-review-v1';

await triggerAiReview.mutateAsync({
  submissionId,
  promptVersion: DEFAULT_PROMPT_VERSION,
});
```

This lives in `apps/web/src/pages/owner/OwnerSubmissionPage.tsx`.

C3 will hard-switch this path to `promptVersionId`. C2 therefore needs to
provide a way to resolve the default prompt-version id before C3/C4 can cleanly
switch the trigger request.

### Existing Hashing Utility

`services/api/src/main/java/com/labelhub/api/shared/canonical/Canonicalizer.java`
already exposes:

- `canonicalJson(Object value)`;
- `sha256Hex(String value)`.

`SchemaService.publishVersion` uses both because schema content is JSON:

```java
Map<String, Object> schemaJson = objectMapper.convertValue(schemaDocument, new TypeReference<>() {});
String canonicalJson = canonicalizer.canonicalJson(schemaJson);
version.setContentHash(canonicalizer.sha256Hex(canonicalJson));
```

Prompt content differs: `prompt_versions.content` is free-form text. C2 should
reuse `sha256Hex`, but it should not JSON-canonicalize prompt text.

### C1 Persistence Shape

C1 added:

- `PromptVersionEntity`;
- `PromptVersionMapper` with insert/select-by-id/select-by-hash;
- migrations `12` and `13`;
- additive `AiCall.promptVersionId` and `providerAdapterVersion`;
- `TriggerAiReviewRequest` remained `required: [promptVersion]`.

C2 can extend the mapper, but it must not change AI review execution.

## Decision 1: Content Hash Algorithm

### Options

| Option | Shape | Pros | Cons |
|---|---|---|---|
| A | `canonicalJson(content)` then `sha256Hex` | Reuses schema-version pattern literally | Hashes JSON string representation, not prompt text |
| B | `sha256Hex(content)` over UTF-8 text | Direct, portable, understandable | Slightly differs from schema JSON path |

### Recommendation

Choose **B: direct `sha256Hex(content)`**.

Reason:

- prompt content is text, not structured JSON;
- JSON canonicalization would add quoting/escaping semantics that do not help
  prompt immutability;
- direct SHA-256 over the prompt text is easy to reproduce in JavaScript,
  Python, shell tooling, or audits;
- it still reuses the existing `Canonicalizer.sha256Hex`, so there is not a
  second MessageDigest implementation.

Rejected:

- Option A gives a hash that is harder to explain and can surprise future
  reviewers comparing the stored content to the hash.

## Decision 2: Default Prompt Resolution

### Options

| Option | Shape | Assessment |
|---|---|---|
| A | Seed migration only; frontend hard-codes id | Predictable DB row, but front-end id coupling is brittle |
| B | Application startup seed | Avoids migration content hash, but startup writes are less auditable |
| C | Endpoint only; service creates default lazily | No migration, but endpoint mutates data or can 404 |
| D | Seed migration plus `GET /prompt-versions/default` | Stable row plus no hard-coded id |

### Recommendation

Choose **D: seed migration + endpoint**.

C2 should add a seed migration that guarantees a default published row exists,
then expose `GET /prompt-versions/default` so C3/C4 can fetch a prompt-version
id without hard-coding database ids.

Reason:

- migration history makes the default row auditable;
- the endpoint makes the frontend independent of auto-increment ids;
- C3 hard switch can rely on a stable backend lookup;
- C2 remains read-only from the frontend perspective.

Rejected:

- Seed-only would tempt frontend code to hard-code `1`.
- Startup seed hides provenance in application boot side effects.
- Lazy endpoint creation makes a GET endpoint mutate data.

## Decision 3: Seed Content

### Recommendation

Seed content should be the existing legacy label:

```text
m3-owner-review-v1
```

Seed row:

- `version_no = 1`;
- `content = 'm3-owner-review-v1'`;
- `content_hash = sha256Hex('m3-owner-review-v1')`;
- `status = 'published'`;
- `owner_id = NULL`;
- `published_at = CURRENT_TIMESTAMP(3)`.

Reason:

- it preserves continuity with the current frontend constant;
- it is honest that P4a is building evidence plumbing, not final prompt
  authoring;
- P4b will introduce Owner-managed prompt/rule editing and real prompt content.

C6 must record a watch entry:

```text
[M7-P4a watch] Default prompt content is a placeholder: P4a seeds the
published prompt version with m3-owner-review-v1 so promptVersionId-based
evidence can be wired. Real owner-defined prompt content and rule editing are
deferred to P4b.
```

Rejected:

- Copying a provider prompt from `services/agent` would blur the ADR-011
  boundary again.
- Writing a longer fake review prompt would look more real than it is.

## Decision 4: PromptVersionService Design

### Methods

Recommended service API:

```java
PromptVersionEntity create(String content, Long ownerId);
PromptVersionEntity findById(Long id);
PromptVersionEntity findByContentHash(String contentHash);
PromptVersionEntity resolveDefault();
```

`create` behavior:

1. Compute `contentHash = canonicalizer.sha256Hex(content)`.
2. Look up existing row by hash.
3. If found, return it.
4. Allocate next global `version_no`.
5. Insert a `draft` prompt version.
6. If the insert races:
   - duplicate `content_hash`: return existing row by hash;
   - duplicate `version_no`: retry bounded times.

This keeps immutable content de-duplicated and avoids creating multiple prompt
versions for the same text.

### Version Number Concurrency

Recommended approach:

- use `SELECT COALESCE(MAX(version_no), 0)` to compute the next value;
- insert within a transaction;
- retry on `DuplicateKeyException` for `version_no` up to a small bound, such
  as `3`;
- return existing row on `content_hash` duplicate.

Reason:

- C2 write volume is effectively one seed row plus rare future service calls;
- MySQL sequences or table locks would add infrastructure not used elsewhere;
- a bounded retry is enough and remains easy to test.

## Decision 5: resolveDefault Semantics

### Options

| Option | Shape | Assessment |
|---|---|---|
| Fixed seed id | Return id `1` or a named seed row | Simple but brittle and DB-id dependent |
| Latest published | Return highest `version_no` with `status='published'` | Natural future semantics |

### Recommendation

Choose **latest published**.

In C2 there is only one published seed row, so the behavior is deterministic.
In P4b, when real prompt publishing exists, the same method naturally returns
the current published default without changing endpoint semantics.

If no published prompt exists, return `404` from the endpoint and fail fast in
tests. The seed migration should make this impossible in normal deployments.

## Decision 6: Endpoint Contract

### OpenAPI

Add:

```yaml
/prompt-versions/default:
  get:
    tags: [PromptVersions]
    operationId: getDefaultPromptVersion
    responses:
      '200':
        description: Default prompt version used by AI review trigger defaults.
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PromptVersion'
      '404':
        $ref: '#/components/responses/ErrorNotFound'
```

This creates a dedicated generated `PromptVersionsApi` instead of expanding
`AiReviewController`.

### Controller

Create:

- `services/api/src/main/java/com/labelhub/api/module/ai/web/PromptVersionsController.java`
- optional mapper method/class to turn `PromptVersionEntity` into generated
  `PromptVersion`

The controller should only implement `GET /prompt-versions/default`.

### Auth

Recommended security:

- authenticated users may call `GET /prompt-versions/default`;
- `SecurityConfig` should add an explicit matcher before `anyRequest`.

Reason:

- the C2 default prompt is global and immutable;
- the owner trigger flow needs it immediately;
- P4b can revisit authorization once prompt versions become owner/task/rule
  scoped.

## File Plan

### Modify

- `packages/contracts/openapi/labelhub.yaml`
  - add `/prompt-versions/default`
- `apps/web/src/shared/api/generated/schema.d.ts`
  - generated change after `gen:api`
- `services/api/src/main/java/com/labelhub/api/config/SecurityConfig.java`
  - authenticated GET rule for `/prompt-versions/default`
- `services/api/src/main/java/com/labelhub/api/module/ai/mapper/PromptVersionMapper.java`
  - add `selectMaxVersionNumber`
  - add `selectLatestPublished`
- `services/api/src/test/java/com/labelhub/api/module/ai/PromptVersionMigrationContractTest.java`
  - assert seed migration exists and inserts published default row
- `services/api/src/test/java/com/labelhub/api/module/ai/mapper/PromptVersionMapperContractTest.java`
  - update allowed mapper method list

### Create

- `services/api/src/main/java/com/labelhub/api/module/ai/service/PromptVersionService.java`
- `services/api/src/main/java/com/labelhub/api/module/ai/web/PromptVersionsController.java`
- `services/api/src/main/resources/db/migration/V202612020920__seed_default_prompt_version.sql`
- `services/api/src/test/java/com/labelhub/api/module/ai/service/PromptVersionServiceTest.java`
- `services/api/src/test/java/com/labelhub/api/module/ai/web/PromptVersionsControllerTest.java`

No frontend business file should change in C2.

## Estimate

| Surface | Estimate |
|---|---:|
| OpenAPI endpoint + generated type churn | 35 hand-authored + generated |
| seed migration | 25 |
| PromptVersionService | 135 |
| PromptVersionMapper extensions | 45 |
| PromptVersionsController + mapping | 90 |
| SecurityConfig route | 10 |
| service tests | 115 |
| controller tests | 70 |
| migration/mapper contract updates | 45 |
| **Total hand-authored** | **570** |

Recommended cap:

- soft: 600
- hard: 700

This is slightly higher than the initial 400/600 suggestion because the default
id cannot be solved cleanly without one endpoint and its tests.

## Risk Register

| Risk | Mitigation |
|---|---|
| C2 leaks into AI review runtime | Keep `AiReviewService`, trigger controller, idempotency, and frontend trigger files diff-empty |
| Hash mismatch between service and seed | Test seed hash against `Canonicalizer.sha256Hex("m3-owner-review-v1")` |
| Default endpoint returns stale prompt after future publishing | Use latest-published semantics |
| Duplicate content creates multiple versions | Service returns existing row by content hash; test it |
| Concurrent create races on global version number | Bounded retry on unique `version_no` conflicts |
| Endpoint auth accidentally open to anonymous | Add SecurityConfig matcher and controller/security test |
| Seed placeholder mistaken for final prompt | C6 watch entry required |

## Verification Plan

C2 implementation should run:

- `mvn -pl services/api -Dtest=PromptVersionServiceTest,PromptVersionsControllerTest,PromptVersionMigrationContractTest,PromptVersionMapperContractTest test`
- existing named AI tests:
  `AiReviewServiceTest`, `AiReviewIntegrationTest`,
  `QualityLedgerIntegrationTest`, `FailedAiCallRecorderTest`,
  `AiReviewDtoMapperTest`
- `mvn -pl services/api test`
- `pnpm --filter @labelhub/web gen:api`
- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `pnpm --filter @labelhub/web test`
- `bash scripts/check-protected-endpoints.sh`

Expected baseline shifts:

- OpenAPI MD5 changes from `c1ee4d213661b881344e59f0ab079f4a`;
- migrations change from `13` to `14`;
- backend tests increase from `504 / 81`;
- frontend Vitest should remain `125` unless generated-type tests are added;
- humanpending remains `141`.

## Frozen Checks

C2 implementation report must confirm empty diff for:

- `AiReviewService`
- `FailedAiCallRecorder`
- idempotency key code
- `AiReviewController.triggerSubmissionAiReview`
- frontend trigger/provenance files
- P3a/P3b validators/evaluators/corpora/renderers
- `humanpending.md`

## Gate Questions

1. Approve hash algorithm: direct `canonicalizer.sha256Hex(content)` over
   prompt text.
2. Approve seed migration + `GET /prompt-versions/default`.
3. Approve placeholder seed content `m3-owner-review-v1` with C6 watch.
4. Approve latest-published default semantics.
5. Approve authenticated read access for default endpoint.
6. Approve caps: soft 600 / hard 700.
