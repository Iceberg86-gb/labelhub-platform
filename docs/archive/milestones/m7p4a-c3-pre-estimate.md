# M7-P4a C3 Pre-Estimate: AI Review Prompt Version Hard Switch

## Status

Pre-estimate gate for M7-P4a C3. This document adds no production code.

Current anchor: `e887f89`. OpenAPI MD5:
`b58f005b7dbfecb487b35e7255bb36d5`. Migrations: `14`.
humanpending: `141`. Backend tests: `514 / 83`. Frontend Vitest: `125`.

C3 is the first P4a cluster that changes AI review runtime behavior. It must be
a vertical slice because the request contract switch would otherwise leave
frontend generated types and backend controller code out of sync.

## Baseline Evidence

### Backend Call Chain

Current call chain:

```java
AiReviewController.triggerSubmissionAiReview(...)
  -> aiReviewService.review(submissionId, ownerId, request.getPromptVersion())
```

`AiReviewService.review` currently accepts:

```java
review(Long submissionId, Long ownerId, String promptVersion)
```

The idempotency key currently uses the prompt string:

```text
submission:{id}:provider:{provider}:model:{model}:prompt:{promptVersion}
```

Successful AI calls currently set only:

```java
aiCall.setPromptVersion(promptVersion);
```

`FailedAiCallRecorder` has the same string prompt-version input and only sets
`promptVersion` on failed rows.

### Mapper Shape

C1 made `ai_calls.prompt_version_id` and `provider_adapter_version` available,
and C1 result mapping already reads them. The insert statement still omits both
columns, so C3 must update the insert when the service starts explicitly
setting the new fields.

### Frontend Trigger

The frontend still sends a string:

```ts
const DEFAULT_PROMPT_VERSION = 'm3-owner-review-v1';

await triggerAiReview.mutateAsync({
  submissionId,
  promptVersion: DEFAULT_PROMPT_VERSION,
});
```

This means C3 must update at least `useTriggerAiReviewMutation` and
`OwnerSubmissionPage` in the same commit as the OpenAPI hard switch. Otherwise
frontend typecheck fails after generated types change.

### Provider Usage

Provider grep shows that `promptVersion` is not currently used to build the
provider prompt:

- `MockAiProvider` ignores it;
- `OpenAiCompatibleProvider` builds the provider prompt through
  `PromptTemplate.build(request.input(), objectMapper)`;
- `PromptTemplate` owns the provider-specific prompt text.

This preserves the ADR-011 boundary: provider-specific prompts remain in the
service/provider layer. P4a prompt versions are business evidence assets and
default-id plumbing; P4b owns owner-authored prompt editing.

## Decision 1: Legacy `ai_calls.prompt_version`

### Options

| Option | Shape | Assessment |
|---|---|---|
| A | Store `PromptVersionEntity.content` | Can exceed `VARCHAR(80)` and leaks prompt text |
| B | Store `versionNo` as a string | Short, but too terse for provenance display |
| C | Store `promptVersion#<versionNo>` | Short, deterministic, readable |
| D | Store seed content such as `m3-owner-review-v1` | Good for seed, inconsistent for future content |

### Recommendation

Choose **C: `promptVersion#<versionNo>`**.

Reason:

- it is stable and short enough for `VARCHAR(80)`;
- it avoids storing full prompt content in a legacy display field;
- it works for the seed row and future owner-authored rows;
- legacy rows keep their existing string labels unchanged.

Rejected:

- storing content risks truncation and makes prompt text appear in places that
  were historically only labels;
- special-casing the seed content would make the legacy label rule branchy and
  harder to explain.

## Decision 2: Missing Prompt Version Id

### Options

| Option | Shape | Assessment |
|---|---|---|
| A | Generic 400 bad request | Treats missing resource as request-shape error |
| B | Dedicated `PromptVersionNotFoundException` mapped to 404 | Matches immutable resource lookup style |

### Recommendation

Choose **B: `PromptVersionNotFoundException` mapped to existing 404 not-found
handling**.

Reason:

- unknown `promptVersionId` is equivalent to a missing schema/submission/task;
- the existing `GlobalExceptionHandler` already has a generic not-found
handler for domain not-found exceptions;
- C3 can add one small exception without adding a new error envelope.

## Decision 3: Provider Invocation Value

### Recommendation

Do **not** pass owner business prompt content into provider prompt composition
in C3.

Use the same short legacy prompt label (`promptVersion#<versionNo>`) when
building `AiCallRequest` so existing provider interfaces remain compatible.

Reason:

- providers currently ignore the prompt-version label for prompt construction;
- `OpenAiCompatibleProvider` uses `PromptTemplate.build(...)`, preserving the
  ADR-011 provider-specific prompt boundary;
- feeding business prompt content to provider prompts is a P4b design concern
  alongside owner prompt/rule editing.

Rejected:

- passing `entity.content` into the provider would make C3 silently change AI
  behavior while C3 is supposed to migrate evidence and idempotency plumbing.

## Decision 4: Legacy Idempotency Rows

### Recommendation

Do not query both old and new idempotency keys in C3.

New C3 reviews should use only the new key:

```text
submission:{id}:provider:{provider}:model:{model}:promptVersionId:{id}:adapter:{providerAdapterVersion}
```

Legacy rows remain readable, but the new key will not match old rows. Repeating
an old review can create a new C3-format `ai_call`. This is the approved
additive migration behavior and must be locked by a P4a C5 legacy-row test.

Rejected:

- querying both key formats would couple new idempotency semantics to legacy
  prompt string labels and make it unclear which evidence version won.

## Implementation Shape

### Backend

`AiReviewService`:

- inject `PromptVersionService`;
- change `review` to accept `Long promptVersionId`;
- resolve `PromptVersionEntity`;
- derive:
  - `promptVersionLabel = "promptVersion#" + versionNo`;
  - `providerAdapterVersion = "agent-default-v1"`;
- build the new idempotency key;
- set `promptVersion`, `promptVersionId`, and `providerAdapterVersion` on new
  successful `AiCallEntity` rows;
- keep input-hash mismatch behavior unchanged.

`AiCallMapper`:

- add `prompt_version_id` and `provider_adapter_version` to the insert column
  list and values list.

`FailedAiCallRecorder`:

- accept prompt-version id and provider-adapter version, or accept a small value
  object/record that contains label/id/adapter;
- build failed attempt rows with all three fields.

`AiReviewController`:

- read `TriggerAiReviewRequest.getPromptVersionId()`.

`ExportArtifactBuilder`:

- add `promptVersionId` and `providerAdapterVersion` to exported AI call
  evidence while keeping `promptVersion`.

Exception handling:

- add `PromptVersionNotFoundException`;
- include it in the existing 404 path.

### Frontend

`useTriggerAiReviewMutation`:

- change variables to `{ submissionId, promptVersionId }`;
- POST `{ promptVersionId }`.

New default prompt query:

- add a small `useDefaultPromptVersionQuery` hook that calls
  `GET /prompt-versions/default`;
- keep the hook close to AI features, for example under
  `apps/web/src/features/ai/`.

`OwnerSubmissionPage`:

- remove `DEFAULT_PROMPT_VERSION`;
- fetch the default prompt version;
- disable or guard the review trigger while the default prompt version is
  loading or unavailable;
- pass `defaultPromptVersion.id` to `triggerAiReview`.

C3 should not modify `AiReviewDrawer` or `AiProvenanceCard`. They can keep
displaying `aiCall.promptVersion` because C1 kept that field required.

## Idempotency Key Length

Representative key with current default values:

```text
submission:300:provider:mock:model:mock-v1:promptVersionId:1:adapter:agent-default-v1
```

This is under `160` characters.

C3 should add a focused unit assertion for the generated key length using the
configured/default provider name, model name, prompt version id, and adapter
version. If future configured provider/model values can make the key exceed
`160`, C3 should fail early with a controlled exception rather than letting the
database truncate or reject unexpectedly.

## Test Upgrade Plan

### Backend Test Families

| Test | C3 change |
|---|---|
| `AiReviewServiceTest` | Seed or mock `PromptVersionService.findById`, assert new key and dual-write |
| `AiReviewIntegrationTest` | Trigger through real DB/default prompt id path, assert prompt_version_id and adapter |
| `QualityLedgerIntegrationTest` | Update trigger input and ledger expectations |
| `FailedAiCallRecorderTest` | Assert failed rows dual-write id/adapter and new failed key |
| `AiReviewDtoMapperTest` | Assert additive fields still map, promptVersion label remains readable |
| `MockAiProviderTest` | Ensure provider remains compatible with label-only promptVersion request |
| `ExportServiceTest` / export coverage | Assert promptVersionId and adapter are exported |

### Frontend Tests

- mutation body test: `promptVersionId` is sent and `promptVersion` is absent;
- default prompt query hook or API helper test;
- `OwnerSubmissionPage` review trigger path test if existing test harness makes
  it cheap; otherwise the hook + mutation tests are the minimum.

## Budget

Hand-authored estimate:

| Surface | Estimate |
|---|---:|
| OpenAPI hard switch | 35 |
| generated call-site cleanup | 20 |
| `AiReviewService` and key derivation | 190 |
| `AiReviewController` + not-found exception | 55 |
| `AiCallMapper` insert update | 20 |
| `FailedAiCallRecorder` | 90 |
| `ExportArtifactBuilder` | 45 |
| frontend default prompt query + trigger path | 155 |
| backend test upgrades/additions | 380 |
| frontend tests | 90 |
| verification glue | 25 |
| **Total** | **1,105** |

- hand-authored soft cap: **1,000** lines;
- hand-authored hard cap: **1,200** lines;
- generated churn reported separately.

C3 is intentionally budgeted above prior P4a clusters because it updates the
contract, runtime service path, idempotency format, failed-call recorder,
backend tests, and the minimum frontend trigger path in one green vertical
slice.

## Stop Conditions

- implementation requires a new migration;
- provider prompt composition needs to consume prompt content;
- C3 needs to change `AiCall` response schema beyond the trigger request hard
  switch;
- frontend provenance display work expands beyond trigger type safety;
- legacy idempotency rows are silently reinterpreted as new-key rows;
- hand-authored diff exceeds `1,200` lines.

## Verification Plan

C3 implementation must report:

- OpenAPI MD5: `b58f005b7dbfecb487b35e7255bb36d5` -> new value;
- migrations remain `14`;
- humanpending remains `141`;
- `TriggerAiReviewRequest` generated types require `promptVersionId`;
- `AiCallMapper` insert includes `prompt_version_id` and
  `provider_adapter_version`;
- new idempotency key length evidence under `160`;
- backend full test result and focused AI test result;
- frontend typecheck, build, and Vitest result;
- protected endpoint check result;
- git diff confirming P3a/P3b logic and prompt-version seed/service semantics
  are unchanged except for normal reuse.
