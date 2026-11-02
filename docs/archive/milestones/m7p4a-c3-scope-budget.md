# M7-P4a C3 Scope-Budget: AI Review Prompt Version Hard Switch

## Status

Pre-estimate gate for M7-P4a C3. No production code is included in this
cluster gate.

Current anchor is the approved C2 implementation: `e887f89`. OpenAPI MD5:
`b58f005b7dbfecb487b35e7255bb36d5`. Migrations: `14`. humanpending: `141`.
Backend tests: `514 / 83`. Frontend Vitest: `125`.

C3 is the P4a vertical hard switch: AI review triggering moves from a legacy
prompt-version string to `promptVersionId`, and new AI calls start dual-writing
`prompt_version_id` plus `provider_adapter_version`.

## Phase Character

C1 and C2 created additive storage and default prompt resolution:

- `prompt_versions` table and seed row;
- additive `ai_calls.prompt_version_id`;
- additive `ai_calls.provider_adapter_version`;
- `PromptVersionService`;
- `GET /prompt-versions/default`.

C3 consumes that infrastructure. It changes the runtime AI-review path and the
frontend trigger request in one commit so the repository does not enter a
broken intermediate state.

## C3 / C4 Boundary

C3 owns the minimum vertical slice required by the contract switch:

- `TriggerAiReviewRequest` hard-switches from `promptVersion` to
  `promptVersionId`;
- backend controller and service resolve `PromptVersionEntity` by id;
- idempotency keys use prompt-version id plus provider-adapter version;
- successful and failed AI calls dual-write the new columns;
- frontend trigger code fetches the default prompt version id and sends it.

C4 owns additive provenance presentation:

- `AiReviewDrawer` can display `promptVersionId` and
  `providerAdapterVersion`;
- `AiProvenanceCard` can display the same evidence fields;
- export/provenance polish that is not required for C3 type safety can be
  finalized there if C3 leaves the response additive fields readable.

C3 may update export serialization when the backend evidence object is built,
but it should not perform broad frontend provenance UI redesign.

## Allowed Surfaces

OpenAPI:

- update `TriggerAiReviewRequest` to require `promptVersionId`;
- remove `promptVersion` from the trigger request schema;
- regenerate frontend and backend generated types.

Backend:

- `AiReviewService`;
- `AiReviewController`;
- `AiCallMapper` insert columns;
- `FailedAiCallRecorder`;
- `ExportArtifactBuilder` for backend export evidence;
- a small `PromptVersionNotFoundException`;
- `GlobalExceptionHandler` registration for the new not-found exception;
- AI review tests and mapper/export tests affected by the signature change.

Frontend:

- `useTriggerAiReviewMutation`;
- a small default prompt-version query hook;
- `OwnerSubmissionPage` trigger path;
- focused frontend tests for the trigger body and default prompt lookup.

## Forbidden Surfaces

- `PromptVersionService` semantics from C2, except normal reuse;
- prompt-version seed migration;
- prompt-version endpoint contract from C2;
- `AiCall` response schema from C1;
- P3a/P3b validators, evaluators, renderers, corpora, and designer linkage code;
- `humanpending.md`;
- migrations.

## Runtime Decisions

### Legacy `ai_calls.prompt_version` Label

New rows should continue to fill the legacy `prompt_version` string column with
a short display label. C3 should not store prompt content there because the
column is `VARCHAR(80)` and future prompt content may be long.

Recommended label:

```text
promptVersion#<versionNo>
```

Example: `promptVersion#1`.

Reason:

- it is deterministic;
- it fits the legacy column;
- it does not leak or truncate prompt content;
- legacy rows keep their existing labels.

### Invalid Prompt Version Id

Recommended behavior:

- add `PromptVersionNotFoundException`;
- map it through the existing not-found handler to HTTP 404 and code
  `NOT_FOUND`.

Reason: an unknown prompt-version id references a missing immutable resource,
matching existing `SchemaNotFoundException` and `SubmissionNotFoundException`
style.

### Provider Invocation

C3 should not feed owner business prompt content into provider-specific prompts.

Provider grep evidence:

- `MockAiProvider` ignores `AiCallRequest.promptVersion`;
- `OpenAiCompatibleProvider` builds the provider prompt with
  `PromptTemplate.build(request.input(), objectMapper)`;
- `PromptTemplate.DEFAULT_PROMPT_VERSION` remains provider/service-owned.

Therefore C3 should pass the short legacy prompt label into `AiCallRequest` for
compatibility, but provider prompt composition remains unchanged. Owner-authored
prompt content becomes a LabelHub evidence asset in P4a and is applied to real
business prompt editing in P4b.

### Legacy Idempotency Rows

New reviews use the new key format:

```text
submission:{id}:provider:{provider}:model:{model}:promptVersionId:{id}:adapter:{providerAdapterVersion}
```

Existing old-key rows are not queried by the new key. Re-triggering a legacy
review can create a new AI call under the new idempotency key. That is the
approved additive migration behavior; legacy rows remain readable because
`promptVersion` stays required in `AiCall`.

P4a C5 should add an explicit legacy-row test for this behavior.

## File Map

| Surface | Planned work |
|---|---|
| `packages/contracts/openapi/labelhub.yaml` | Hard-switch `TriggerAiReviewRequest` to `promptVersionId` |
| generated OpenAPI types | Refresh backend/frontend generated models |
| `AiReviewService.java` | Resolve prompt version id, build new key, dual-write call fields |
| `AiReviewController.java` | Read `getPromptVersionId()` |
| `AiCallMapper.java` | Include `prompt_version_id` and `provider_adapter_version` in insert |
| `FailedAiCallRecorder.java` | Accept prompt-version id/adapter and dual-write failed rows |
| `ExportArtifactBuilder.java` | Include prompt-version id and provider-adapter evidence |
| `PromptVersionNotFoundException.java` | New small exception |
| `GlobalExceptionHandler.java` | Add exception to existing 404 path |
| `useTriggerAiReviewMutation.ts` | Send `promptVersionId` |
| `useDefaultPromptVersionQuery.ts` | Read `GET /prompt-versions/default` |
| `OwnerSubmissionPage.tsx` | Fetch default prompt id and pass it to trigger |
| AI review tests | Upgrade six string-prompt test groups to prompt ids |

## Budget

Hand-authored estimate:

| Surface | Estimate |
|---|---:|
| OpenAPI hard switch | 35 |
| backend generated type call-site cleanup | 20 |
| `AiReviewService` + idempotency key | 180 |
| controller + exception handler | 55 |
| `AiCallMapper` insert update | 20 |
| `FailedAiCallRecorder` | 90 |
| export evidence update | 45 |
| frontend default prompt query + trigger path | 150 |
| backend tests upgrade/additions | 360 |
| frontend tests | 85 |
| verification glue | 25 |
| **Total** | **1,045** |

- hand-authored soft cap: **1,000** lines;
- hand-authored hard cap: **1,200** lines;
- generated churn reported separately.

This is higher than the earlier 800/1100 suggestion because the actual grep
shows the trigger hard switch touches both backend and frontend, plus six
backend test families and export evidence.

## Stop Conditions

- C3 requires changing migrations;
- C3 requires changing prompt-version seed behavior;
- provider invocation needs to consume owner prompt content directly;
- idempotency key length can exceed `160` for normal configured provider/model
  values and no guard is added;
- any P3a/P3b logic changes;
- hand-authored diff exceeds the hard cap.

## Verification Plan

C3 implementation must verify:

- OpenAPI MD5 changes from `b58f005b7dbfecb487b35e7255bb36d5` to a recorded
  new value;
- migrations remain `14`;
- humanpending remains `141`;
- `TriggerAiReviewRequest` has `promptVersionId` and no `promptVersion`;
- new idempotency key format is under `160` chars for configured provider,
  model, default prompt id, and adapter version;
- existing AI test families are upgraded and green:
  `AiReviewServiceTest`, `AiReviewIntegrationTest`,
  `QualityLedgerIntegrationTest`, `FailedAiCallRecorderTest`,
  `AiReviewDtoMapperTest`, `MockAiProviderTest`;
- frontend typecheck/build/test are green after the trigger path switch;
- P3a/P3b tests remain untouched and green.
