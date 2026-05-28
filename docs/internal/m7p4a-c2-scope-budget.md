# M7-P4a C2 Scope-Budget: Prompt Version Service + Default Resolution

## Status

Pre-estimate gate for M7-P4a C2. No implementation code is included in this
cluster.

Current anchor is the approved C1 implementation in the working tree: OpenAPI
MD5 `c1ee4d213661b881344e59f0ab079f4a`, migrations `13`,
humanpending `141`, backend tests `504 / 81`, frontend Vitest `125`.

C2 wraps the C1 prompt-version storage in a small backend service and gives
later clusters a stable way to resolve the default prompt-version id. It should
not change AI review execution, idempotency keys, or the frontend trigger path.

## Phase Character

C1 created the immutable prompt-version storage surface:

- `prompt_versions` table;
- additive `ai_calls.prompt_version_id`;
- additive `ai_calls.provider_adapter_version`;
- generated `PromptVersion` and additive `AiCall` fields.

C2 makes that surface usable:

- a `PromptVersionService` computes prompt content hashes and reads immutable
  prompt versions;
- a seed migration creates the default published prompt-version row;
- a minimal `GET /prompt-versions/default` endpoint returns that row so C3/C4
  can hard-switch the trigger path to `promptVersionId` without hard-coding a
  database id.

C2 remains intentionally narrow. It does not implement owner editing,
AiReviewRule, or new AI-review runtime behavior.

## Approved Premises

From P4 research and C1:

- owner prompt versions are LabelHub DB assets;
- provider adapter version remains a service/agent-owned concept and is a C1
  constant placeholder: `agent-default-v1`;
- `TriggerAiReviewRequest` keeps `promptVersion` until the C3 vertical hard
  switch;
- `AiCall.promptVersion` stays required for legacy display;
- `promptVersionId` is nullable until C3 writes it for new calls;
- P4b owns AiReviewRule and the visual prompt/rule editor.

## Allowed Surfaces

Backend service:

- create `services/api/src/main/java/com/labelhub/api/module/ai/service/PromptVersionService.java`
- optionally create a small mapper/DTO helper for generated `PromptVersion`
  responses

Backend web/API:

- add `GET /prompt-versions/default` to `packages/contracts/openapi/labelhub.yaml`
- regenerate frontend and backend OpenAPI types
- create `PromptVersionsController` or equivalent generated-interface
  implementation
- update `SecurityConfig` for the new read endpoint

Database:

- add one seed migration under `services/api/src/main/resources/db/migration/`

Mapper support:

- extend `PromptVersionMapper` with read methods needed by service:
  `selectLatestPublished`, `selectMaxVersionNumber`, and any duplicate-content
  lookup/retry support required by the final implementation

Tests:

- service tests for hashing, duplicate content, lookup, and default resolution
- endpoint integration or controller tests for `GET /prompt-versions/default`
- migration contract test for the seed row

## Forbidden Surfaces

- `AiReviewService.review`
- idempotency key generation
- `FailedAiCallRecorder`
- `AiReviewController.triggerSubmissionAiReview`
- frontend trigger/provenance code:
  - `apps/web/src/features/ai/useTriggerAiReviewMutation.ts`
  - `apps/web/src/pages/owner/OwnerSubmissionPage.tsx`
  - `apps/web/src/features/ai/AiReviewDrawer.tsx`
  - `apps/web/src/features/ai/AiProvenanceCard.tsx`
- P3a/P3b validators, evaluators, corpora, renderers, and designer linkage code
- `humanpending.md`

## Scope

### 1. PromptVersionService

Create a small service around the C1 mapper:

- `create(content, ownerId)` computes a content hash and inserts an immutable
  draft prompt version;
- duplicate `content_hash` returns the existing row instead of creating a new
  version;
- `findById(id)` returns a prompt version or `null`;
- `findByContentHash(hash)` returns a prompt version or `null`;
- `resolveDefault()` returns the latest published prompt version.

The service must reuse the existing `Canonicalizer` component for SHA-256:

- prompt content is free-form text, not JSON;
- C2 should call `canonicalizer.sha256Hex(content)` directly;
- C2 should not call `canonicalizer.canonicalJson(content)` because JSON
  canonicalization would hash the quoted JSON string representation rather
  than the prompt text itself.

### 2. Default Prompt Seed

Add one seed migration after C1 migrations:

- insert version `1`;
- content: `m3-owner-review-v1`;
- content hash: SHA-256 over UTF-8 bytes of `m3-owner-review-v1`;
- status: `published`;
- owner: `NULL`;
- published timestamp: migration-time `CURRENT_TIMESTAMP(3)`.

This seed is an explicit placeholder, not the final business prompt template.
P4a C6 must record a watch item that the default prompt content remains a
placeholder until P4b introduces owner-managed prompt/rule editing.

### 3. Default Prompt Endpoint

Expose a single read endpoint:

```http
GET /prompt-versions/default
```

Response: `PromptVersion`.

No create/list/update endpoints are included in C2. P4b owns owner-facing prompt
management. C2 only supplies the id that C3/C4 need for a clean hard switch.

Recommended controller shape:

- use a new `PromptVersions` OpenAPI tag so generated code creates a separate
  `PromptVersionsApi`;
- implement it with a small `PromptVersionsController`;
- map `PromptVersionEntity` to generated `PromptVersion`.

### 4. Endpoint Auth

Recommended auth for C2:

- authenticated users may read `GET /prompt-versions/default`;
- the endpoint returns a global immutable default prompt version, not
  owner-specific rule configuration;
- P4b can narrow or replace this endpoint once prompt rules become
  task/owner-specific.

This requires `SecurityConfig` to allow authenticated GET access to
`/prompt-versions/default`.

### 5. Version Number Allocation

Prompt versions are global in P4a, so `version_no` is global.

Recommended allocation:

- service reads `MAX(version_no) + 1`;
- insert inside a transaction;
- on duplicate `version_no`, retry a small bounded number of times;
- on duplicate `content_hash`, return the existing row.

P4a writes prompt versions rarely, and C2 only seeds one row. A bounded retry is
enough without introducing database sequences or fake family locks.

## Budget

Hand-authored estimate:

| Surface | Estimate |
|---|---:|
| OpenAPI endpoint addition | 35 |
| seed migration | 25 |
| PromptVersionService | 130 |
| PromptVersionMapper extensions | 45 |
| controller + DTO mapper | 90 |
| SecurityConfig route | 10 |
| backend service/controller/migration tests | 170 |
| docs/report glue | 20 |
| **Total** | **525** |

- hand-authored soft cap: 600 lines;
- hand-authored hard cap: 700 lines;
- generated churn reported separately.

## Stop Conditions

- implementation requires touching `AiReviewService`, idempotency keys, or
  frontend trigger code;
- endpoint design grows beyond `GET /prompt-versions/default`;
- the seed prompt tries to become the real final prompt template;
- content hash implementation introduces a second hashing utility instead of
  using `Canonicalizer.sha256Hex`;
- duplicate-content handling creates multiple rows with the same content hash;
- hand-authored diff exceeds 650 lines.

## Verification Plan

C2 implementation must verify:

- OpenAPI MD5 changes from `c1ee4d213661b881344e59f0ab079f4a` to a recorded
  new value;
- migrations count changes from `13` to `14`;
- `GET /prompt-versions/default` returns the seed row;
- service tests pass for hash, duplicate content, lookup, and default
  resolution;
- existing AI review tests still pass because runtime behavior is unchanged;
- frontend typecheck/build remain green after generated type refresh;
- humanpending remains `141`;
- forbidden files have empty diff.

## Gate Questions

1. Approve direct `sha256Hex(content)` over UTF-8 prompt text, not
   `canonicalJson(content)`.
2. Approve seed migration plus `GET /prompt-versions/default` as the default-id
   resolution mechanism.
3. Approve `m3-owner-review-v1` as a placeholder seed content, with C6 watch
   documentation.
4. Approve latest-published semantics for `resolveDefault()`.
5. Approve authenticated read access for `GET /prompt-versions/default`.
6. Approve caps: soft 550 / hard 650.
