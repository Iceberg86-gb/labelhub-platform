# M7-P4a C1 Scope-Budget: Prompt Version Infrastructure

## Status

Pre-estimate gate for M7-P4a C1. No implementation code has landed for this
cluster.

Current anchor: `4b85b12` plus approved P4 research doc in the working tree.
OpenAPI MD5 remains `2482d531df39e9e12613bf964f3618ea`; migrations `11`;
humanpending `141`; backend tests `494 / 81`; frontend Vitest `125`.

P4a implements prompt-version evidence binding before the larger P4b
AiReviewRule/editor work. C1 is the infrastructure cluster: it creates the
database and generated-contract footing that later clusters use. It should not
change AI review business behavior yet.

## Phase Character

M7-P4a closes the design break where `ai_calls.prompt_version` is only a string
label while baseline evidence requirements expect immutable prompt versions.

C1 establishes:

- immutable `prompt_versions` storage;
- additive `ai_calls.prompt_version_id` and `provider_adapter_version` columns;
- generated `PromptVersion` / `AiCall` contract fields;
- minimal backend entity/mapper access for future service clusters.

C1 does **not** switch AI review triggering to prompt-version ids. That vertical
hard switch belongs in C3 because doing it in C1 would either break the
single-cluster green rule or require a temporary id-as-string shim.

## Approved P4a Premises

From `docs/internal/m7p4-research.md`:

- Owner-defined review prompt versions are LabelHub business assets stored in
  the LabelHub DB.
- Provider-specific adapter prompts remain in `services/agent`.
- P4a writes `provider_adapter_version` as a constant placeholder
  `agent-default-v1`; real agent-version integration is deferred and must be
  recorded in P4a closure.
- P4a uses additive dual-write storage: keep `ai_calls.prompt_version`, add
  `prompt_version_id`, add `provider_adapter_version`.
- New P4a reviews eventually use a new idempotency key including
  `promptVersionId` and `providerAdapterVersion`.
- Legacy idempotency keys remain readable but are not used as fallback hits for
  new id-based reviews.
- P4b owns AiReviewRule and the owner prompt/rule editor.

## Allowed Surfaces

Contract and generated types:

- `packages/contracts/openapi/labelhub.yaml`
- `apps/web/src/shared/api/generated/schema.d.ts`
- backend generated OpenAPI models under `services/api/target/generated-sources`

Database:

- new migration(s) under `services/api/src/main/resources/db/migration/`

Backend minimal persistence:

- new `PromptVersionEntity`
- new `PromptVersionMapper`
- `AiCallEntity` fields for `promptVersionId` and `providerAdapterVersion`
- `AiCallMapper` result mapping for the new columns if needed

Tests:

- focused mapper/migration tests for the new table and additive ai_call columns

## Forbidden Surfaces

- `AiReviewService.review` behavior and idempotency key logic
- `FailedAiCallRecorder` behavior
- `AiReviewController` trigger request handling, unless the user explicitly
  rejects this gate's recommendation and chooses a vertical hard-switch cluster
- frontend trigger/provenance business code:
  - `useTriggerAiReviewMutation`
  - `OwnerSubmissionPage`
  - `AiReviewDrawer`
  - `AiProvenanceCard`
- P3a/P3b validators, linkage evaluators, renderers, corpora, and designer code
- `humanpending.md`

## Scope

### 1. OpenAPI Contract

Add `PromptVersion` schema with:

- `id: int64`
- `versionNo: integer`
- `contentHash: string`
- `content: string`
- `status: draft | published`
- `ownerId?: int64`
- `publishedAt?: date-time`
- `createdAt: date-time`

Update `AiCall` additively:

- keep `promptVersion` required as legacy/display label;
- add nullable `promptVersionId: int64`;
- add required `providerAdapterVersion: string`.

Do not change `TriggerAiReviewRequest` in C1. The research-approved hard
switch remains P4a scope, but C1 should not create an intentionally broken
intermediate state. C3 will change the trigger request and backend service in
one vertical slice.

No `/prompt-versions` endpoints in C1. C2 owns service/API exposure or default
resolution once the persistence model is approved.

### 2. Migration

Create `prompt_versions`.

Recommended C1 table shape:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `version_no INT NOT NULL`
- `content TEXT NOT NULL`
- `content_hash CHAR(64) NOT NULL`
- `status VARCHAR(32) NOT NULL DEFAULT 'draft'`
- `owner_id BIGINT`
- `published_at DATETIME(3)`
- `created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`
- `UNIQUE KEY uk_prompt_versions_no (version_no)`
- `UNIQUE KEY uk_prompt_versions_hash (content_hash)`

Alter `ai_calls`:

- `prompt_version_id BIGINT NULL`
- `provider_adapter_version VARCHAR(80) NOT NULL DEFAULT 'agent-default-v1'`
- FK from `ai_calls.prompt_version_id` to `prompt_versions.id`
- index on `prompt_version_id`

Recommended migration split:

1. Create `prompt_versions`.
2. Alter `ai_calls`.

This produces migrations `12` and `13`, keeps rollback/review smaller, and
separates the new immutable asset table from evidence-table alteration.

### 3. PromptVersion Family Decision

Research compared prompt versions to `schema_versions`, but P4a intentionally
does not create an AiReviewRule family/root. The initial C1 prompt suggested a
nullable `prompt_family_id` option; this gate recommends **not** adding it in
P4a.

Reason:

- MySQL unique indexes permit multiple `NULL` values. A nullable
  `prompt_family_id` would not enforce `(family, version_no)` uniqueness for
  P4a rows where every family is `NULL`.
- Adding a fake family id or generated coalesce column would introduce a
  family concept before P4b has defined it.
- A global immutable prompt-version asset is honest for P4a. P4b can attach
  prompt versions to AiReviewRule families through a rule pointer or future
  association without mutating historical AI evidence.

P4a should therefore use global `version_no` and global `content_hash`
uniqueness. If P4b needs rule-local display numbering, it should add a
rule-layer version number then, not reinterpret P4a evidence rows.

### 4. Minimal Backend Entity/Mapper

Add:

- `PromptVersionEntity`
- `PromptVersionMapper`

Minimum mapper methods:

- `insert`
- `selectById`
- `selectByContentHash`
- `selectLatestPublished` or equivalent only if C2 proves it needs default
  prompt-version resolution immediately; otherwise defer to C2.

Update `AiCallEntity` with nullable `promptVersionId` and
`providerAdapterVersion`.

Update `AiCallMapper` result mappings for the new columns. Do not update
existing insert SQL to set these columns in C1; DB defaults/nullability must
allow existing AI review tests to remain green. C3 will explicitly set the new
fields when it changes review behavior.

### 5. Tests

Add focused backend tests:

- migration/table presence test if the local test style supports it;
- `PromptVersionMapper` insert/select round trip;
- `AiCallMapper` remains compatible with existing insert shape and can read
  `prompt_version_id` / `provider_adapter_version`.

Existing AI review and quality-ledger tests must remain green because C1 does
not change behavior.

Frontend tests should not change in C1 because trigger/provenance UI is not
updated yet.

## Budget

Hand-authored estimate:

| Surface | Estimate |
|---|---:|
| OpenAPI schema additions | 60 |
| migrations | 90 |
| PromptVersion entity/mapper | 130 |
| AiCall entity/mapper additive fields | 45 |
| mapper/migration tests | 150 |
| docs/report glue | 25 |
| **Total** | **500** |

Recommended caps:

- hand-authored soft cap: 550 lines;
- hand-authored hard cap: 700 lines;
- generated code churn reported separately.

## Stop Conditions

- Any implementation requires changing AI review behavior or idempotency key
  logic in C1.
- The trigger request hard switch is attempted in C1 without user
  re-adjudication.
- Existing AI review tests break because additive columns/defaults are not
  backward-compatible.
- Migration design requires a fake prompt family/root before P4b.
- Hand-authored diff exceeds 700 lines.
- OpenAPI changes include endpoints or request hard-switch work beyond the
  additive `PromptVersion` / `AiCall` schema changes.

## Verification Plan

C1 implementation must verify:

- OpenAPI MD5 changes from `2482d531df39e9e12613bf964f3618ea` to a recorded
  new value.
- migrations count changes from `11` to `13` if split migrations are accepted.
- backend compile/test pass.
- frontend generated types regenerate; typecheck/build remain green because
  trigger request is not yet changed.
- existing AI review tests still pass.
- humanpending remains `141`.
- forbidden files have empty diff.

## Gate Questions

1. Approve deferring the `TriggerAiReviewRequest` hard switch from C1 to the
   later vertical service/frontend cluster C3.
2. Approve global `prompt_versions` uniqueness for P4a, without nullable
   `prompt_family_id`.
3. Approve split migrations: create table, then alter `ai_calls`.
4. Approve C1 caps: soft 550 / hard 700 hand-authored lines, generated churn
   separate.
