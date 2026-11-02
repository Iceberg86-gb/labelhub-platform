# M7-P4a Verification: Prompt Version Foundation

## 1. Status

M7-P4a closed on 2026-05-28.

Baseline after the task-to-schema hotfix:

| Anchor | Value |
|---|---|
| Start head | `4b85b12` |
| Start OpenAPI MD5 | `2482d531df39e9e12613bf964f3618ea` |
| Start backend tests | `494 / 81` |
| Start frontend Vitest | `125` |
| Start migrations | `11` |
| Start humanpending | `141` |

Final anchors after C5, before this closure cluster:

| Anchor | Value |
|---|---|
| Code head | `ec6a011` |
| OpenAPI MD5 | `23a67e2cad632b3e9cfaff03c5d05dd7` |
| Backend tests | `516 / 84` |
| Frontend Vitest | `131` |
| Migrations | `14` |
| humanpending | `146` after this C6 update |

Phase character: P4a is the prompt-versioning foundation. It upgrades AI
evidence from a plain string `ai_calls.prompt_version` label to an immutable
PromptVersion asset plus explicit provider adapter provenance. It does not
implement the Owner-facing AiReviewRule editor; that is deliberately deferred
to P4b.

## 2. Goal And Design Break

Before P4a, `ai_calls.prompt_version` carried too much meaning:

- an Owner business prompt/version label;
- a provider adapter/system-prompt provenance hint;
- an idempotency-key component.

The design baseline requires each Owner prompt configuration save to become a
fixed `prompt_version` and AI review to bind to that immutable version. ADR-011
also keeps provider-specific prompts, model names, and output adapters inside
`services/agent`.

P4a resolves the break by splitting provenance:

| Concept | Owner |
|---|---|
| Owner business prompt version | LabelHub DB, `prompt_versions` |
| Provider adapter version | `services/agent` concept; P4a writes `agent-default-v1` placeholder |
| Legacy display label | `ai_calls.prompt_version`, retained for old and new rows |
| Immutable evidence FK | `ai_calls.prompt_version_id` |

## 3. Commit Map

| Commit | Cluster | Purpose |
|---|---|---|
| `18a586f` | Research + C1 | P4 research record, OpenAPI `PromptVersion`, additive `AiCall` fields, `prompt_versions` table, `ai_calls` FK fields, entity/mapper infrastructure |
| `e887f89` | C2 | `PromptVersionService`, default prompt seed, `GET /prompt-versions/default`, direct content hash via `Canonicalizer.sha256Hex` |
| `5ca4e2d` | C3 | Trigger hard switch to `promptVersionId`, `AiReviewService` signature migration, new idempotency key, failed-call recorder, export evidence, frontend trigger path |
| `24e7ae2` | C4 | Frontend provenance display for `promptVersionId` and `providerAdapterVersion` with legacy null fallback |
| `ec6a011` | C5 | Legacy `ai_calls` old-key integration coverage and provenance compatibility test |
| (this commit) | C6 | Verification doc and humanpending closure records |

Note: in this branch history the research document is recorded in the C1
infrastructure commit. The research content remains the approved scope record
for the P4a/P4b split.

## 4. MD5 And Migration Chain

| Step | OpenAPI MD5 | Migrations | Why |
|---|---|---:|---|
| Start after task-schema hotfix | `2482d531df39e9e12613bf964f3618ea` | `11` | Pre-P4a baseline |
| C1 | `c1ee4d213661b881344e59f0ab079f4a` | `13` | PromptVersion schema, additive AiCall fields, prompt table + ai_calls FK migrations |
| C2 | `b58f005b7dbfecb487b35e7255bb36d5` | `14` | Default prompt endpoint and seed migration |
| C3 | `23a67e2cad632b3e9cfaff03c5d05dd7` | `14` | TriggerAiReviewRequest hard switch to `promptVersionId` |
| C4-C6 | `23a67e2cad632b3e9cfaff03c5d05dd7` | `14` | No further contract or DB changes |

## 5. Cluster Delivery

### 5.1 Research

The research record established:

- Prompt assets are split by semantic owner: LabelHub owns business prompt
  versions; `services/agent` owns provider adapter prompts and output adapters.
- P4 is split into P4a/P4b. P4a builds immutable prompt version infrastructure
  and AI call binding. P4b owns AiReviewRule and Owner editing UI.
- Storage migration is additive dual-write. Legacy `ai_calls.prompt_version`
  stays readable while new rows bind to `prompt_version_id`.
- Trigger requests hard-switch to `promptVersionId`; storage/response stays
  additive for legacy evidence.
- Legacy idempotency rows are not queried with both old and new key formats.
  New calls use the new key, so old rows remain readable but are not reused.

### 5.2 C1: Contract, Migrations, And Infrastructure

C1 added:

- OpenAPI `PromptVersion` and `PromptVersionStatus`;
- `AiCall.promptVersionId` nullable and `AiCall.providerAdapterVersion`
  required, while retaining required `promptVersion`;
- global `prompt_versions` table with globally unique `version_no` and
  `content_hash`;
- additive `ai_calls.prompt_version_id` nullable FK and
  `provider_adapter_version VARCHAR(80) NOT NULL DEFAULT 'agent-default-v1'`;
- `PromptVersionEntity` and `PromptVersionMapper`;
- `AiCallEntity` additive fields;
- mapper/DTO fallback so existing AI paths remained intact before C3.

C1 intentionally did not change `TriggerAiReviewRequest`, `AiReviewService`,
idempotency keys, or frontend trigger code.

### 5.3 C2: Default Prompt Version Service

C2 added:

- `PromptVersionService`;
- direct content hashing through `Canonicalizer.sha256Hex(content)`, not
  `canonicalJson(content)`, because prompt content is free-form text rather
  than JSON;
- duplicate content hash reuse for immutable prompt assets;
- `create` defaulting new service-created prompt versions to `draft`;
- latest-published default resolution;
- seed migration for the default published row with content
  `m3-owner-review-v1`;
- `GET /prompt-versions/default`, authenticated and read-only.

This gives the frontend a stable way to discover the default prompt version id
without hard-coding database ids.

### 5.4 C3: AI Review Hard Switch

C3 is the vertical hard-switch cluster:

- `TriggerAiReviewRequest` changed from `{ promptVersion }` to
  `{ promptVersionId }`;
- `AiReviewService.review` changed from `String promptVersion` to
  `Long promptVersionId`;
- the service resolves `PromptVersionEntity` at the boundary and throws
  `PromptVersionNotFoundException` for unknown ids;
- new AI calls write:
  - `prompt_version = "promptVersion#<versionNo>"`;
  - `prompt_version_id = <id>`;
  - `provider_adapter_version = "agent-default-v1"`;
- idempotency key changed to:

```text
submission:{submissionId}:provider:{provider}:model:{model}:promptVersionId:{promptVersionId}:adapter:{providerAdapterVersion}
```

- the key length is shorter and deterministic, closing the old M6-P4a
  unbounded string prompt-version key risk for new calls;
- `FailedAiCallRecorder` writes the same id/adapter evidence;
- `ExportArtifactBuilder` includes the additive evidence fields;
- frontend trigger path fetches the default prompt version and sends
  `promptVersionId`;
- provenance display was left to C4.

Provider-specific code stayed unchanged. The provider request still carries the
short legacy label for compatibility, and the Owner business prompt content is
not fed into provider adapters in P4a. That preserves the ADR-011 boundary:
provider-specific prompts remain in `services/agent`.

### 5.5 C4: Frontend Provenance Display

C4 made provenance visible:

- `AiProvenanceCard` shows Prompt, Prompt ID, and Adapter;
- `AiReviewDrawer` shows the same additive evidence;
- legacy rows with `promptVersionId = null` omit Prompt ID rather than showing
  `#null`;
- `providerAdapterVersion` displays when present and is not invented by the
  client when absent.

This is purely additive UI. It does not change trigger logic, backend writes,
or OpenAPI.

### 5.6 C5: Legacy Idempotency Integration Proof

C5 added the history-compatibility proof:

- seed a legacy `ai_calls` row with the old key format:

```text
submission:{id}:provider:mock:model:mock-v1:prompt:prompt-v1
```

- leave `prompt_version_id = NULL`;
- trigger a new AI review with `promptVersionId = 1`;
- prove the new key does not reuse the old row;
- prove both legacy and modern rows remain readable in provenance;
- prove the legacy row is not rewritten.

This closes the P4a research question about legacy idempotency behavior:
legacy rows remain readable, but new id-based calls do not query or reuse old
string-key evidence.

## 6. Final Architecture

```text
Frontend trigger:
  OwnerSubmissionPage
    -> useDefaultPromptVersionQuery()
    -> useTriggerAiReviewMutation({ promptVersionId })

Backend trigger:
  AiReviewController
    -> AiReviewService.review(submissionId, ownerId, promptVersionId)
    -> PromptVersionService.findById(promptVersionId)
    -> promptVersion label = "promptVersion#<versionNo>"
    -> providerAdapterVersion = "agent-default-v1"
    -> idempotency key uses promptVersionId + adapter
    -> ai_calls dual-write prompt_version + prompt_version_id + provider_adapter_version

Provenance:
  AiCall.promptVersion remains required display label
  AiCall.promptVersionId is nullable for legacy rows
  AiCall.providerAdapterVersion is required/defaulted
```

## 7. R8 Transparency Records

### Record A: Provider Adapter Version Placeholder

P4a records `provider_adapter_version` everywhere it matters: DB, DTOs,
provenance UI, failed-call recorder, export evidence, and idempotency keys.
However, the value is currently the constant `agent-default-v1`. It is a
future-proof evidence slot, not yet a real `services/agent` release/version
source.

This is intentional scope control, not a hidden implementation detail. A later
agent-versioning cluster must replace the constant with a real provider adapter
version source.

### Record B: Default Prompt Content Placeholder

The default published prompt version seeded by C2 has content
`m3-owner-review-v1`. That content is a continuity placeholder so P4a can bind
AI evidence to an immutable prompt version id before Owner prompt editing
exists.

Real Owner-authored prompt content and AiReviewRule editing are deferred to
P4b.

### Record C: P4b Rule/Editor Deferral

P4a does not implement `ai_review_rules`, Owner prompt/rule editing, prompt
version publish UI, or task publish guards for review-rule configuration. Those
belong to P4b.

P4a deliberately leaves `prompt_versions` as global immutable assets. P4b can
add rule/root containers and pointers without retroactively changing historical
P4a evidence rows.

### Record D: Docker D-口径

P4a includes Testcontainers integration coverage, including the C5 legacy
idempotency test and prompt-version API integration. In the local sandbox,
Docker is unavailable or mismatched, so those tests are committed as permanent
guards but skip under the local Docker D-口径. The non-Docker backend suite
still passes, and Docker-capable environments should execute the skipped
integration cases.

## 8. Verification Summary

| Check | Result |
|---|---|
| Backend tests | `516 / 84` in the C5 report |
| Frontend Vitest | `131` in the C4 report |
| OpenAPI MD5 | `23a67e2cad632b3e9cfaff03c5d05dd7` |
| Migrations | `14` |
| humanpending | `146` after this C6 update |
| C6 code changes | None |

Relevant C5 verification:

```text
mvn -pl services/api test
Tests run: 516, Failures: 0, Errors: 0, Skipped: 84
```

C6 is docs + humanpending only; it does not change code or test counts.

## 9. Final State

P4a is complete when this C6 commit lands:

- immutable prompt-version infrastructure exists;
- new AI calls bind to `promptVersionId`;
- provider adapter provenance has an explicit field and idempotency-key slot;
- legacy `promptVersion` display labels remain readable;
- legacy old-key rows are intentionally not reused by new id-based keys;
- C4 provenance UI surfaces the new evidence;
- P4b remains the planned phase for AiReviewRule and Owner prompt editing.
