# M7-P4 Research: Prompt Versioning

## 1. Status

Research-only cluster for M7-P4. No code, contract, migration, or
humanpending changes are included in this commit.

Current anchors at research start:

| Anchor | Value |
|---|---|
| HEAD | `4b85b12` |
| OpenAPI MD5 | `2482d531df39e9e12613bf964f3618ea` |
| Backend tests | `494 / 81` |
| Frontend Vitest | `125` |
| Migrations | `11` |
| humanpending | `141` |

P4 exists because the current AI evidence path records only a string
`promptVersion`, while the architecture baseline requires Owner-managed prompt
configuration to be saved as immutable prompt versions and bound to AI calls.

## 2. Evidence Summary

### 2.1 AiReviewRule Is Contract-Only

OpenAPI currently exposes draft AI rule shapes:

- `POST /ai-review/rules`
- `AiReviewRuleRequest { taskId, promptTemplate, dimensions, threshold }`
- `AiReviewRule`

Backend reality:

- `AiReviewController.saveAiReviewRule(...)` throws `501 NOT_IMPLEMENTED`.
- There is no `ai_review_rules` migration table.
- There is no backend `AiReviewRule` entity, mapper, or service.
- Local development DB confirms no `ai_review_rules` table.

Conclusion: there is no stored AiReviewRule data to migrate. P4 can choose the
proper model without preserving an earlier rule implementation.

### 2.2 ai_calls.prompt_version Is Deeply Integrated

The implemented AI path uses a plain string prompt version:

- DB: `ai_calls.prompt_version VARCHAR(80) NOT NULL`.
- Entity: `AiCallEntity.promptVersion`.
- Service signature: `AiReviewService.review(Long submissionId, Long ownerId, String promptVersion)`.
- Provider request: `AiCallRequest(String promptVersion, Map<String, Object> input, Duration timeout)`.
- Failed recorder: `FailedAiCallRecorder.recordFailedAttempt(..., String promptVersion, ...)`.
- Idempotency key: `submission:{id}:provider:{provider}:model:{model}:prompt:{promptVersion}`.
- Frontend trigger body: `{ promptVersion }`.
- UI/provenance/export surfaces display `aiCall.promptVersion`.

Local development DB query on 2026-05-28:

```text
SELECT COUNT(*) AS ai_call_count, COUNT(DISTINCT prompt_version) AS prompt_version_count FROM ai_calls;
ai_call_count = 0
prompt_version_count = 0
```

Integration/unit tests do create `ai_calls` rows with `prompt-v1`, so tests must
be updated even if the local dev DB has no stored rows.

### 2.3 Design Baseline Requires Immutable Prompt Versions

The complete baseline says:

- Section 7.1 / line 218: every configuration save becomes a `prompt_version`;
  review binds to a fixed version.
- Section 7.4 / line 242: idempotency key includes `submission_id +
  prompt_version + model_version`.
- Section 12 / line 420: Owner configures prompt template, variables, and
  scoring dimensions, saved as `prompt_version`.
- Section 12 / line 426: scoring stability depends on fixed prompt version,
  structured schema, and input hash.
- `docs/internal/m3-startup-overview.md` line 45: M3 needs fixed prompt
  versions or AI evidence cannot be reproduced.

### 2.4 ADR-011 Creates A Boundary

ADR-011 says:

```text
Provider-specific prompts, model names, and output adapters stay in services/agent.
```

This is about provider adapter prompts and provider-specific output adaptation,
not the Owner-defined business review rule template. Today both concepts are
collapsed into `ai_calls.prompt_version`, which is the design break P4 needs to
repair.

### 2.5 M6-P4a Idempotency Watch Still Applies

`docs/internal/m6p4a-scope-budget.md` records:

- current idempotency key limit is `VARCHAR(160)`;
- DeepSeek-style example with failed suffix remains within 160 chars;
- `TriggerAiReviewRequest.promptVersion` has no OpenAPI `maxLength`;
- future P4 work must not silently truncate overly long prompt versions.

If P4 moves idempotency to a numeric prompt version id, this watch is largely
closed for new calls. Legacy string compatibility still needs explicit max
length behavior.

## 3. Q1: Prompt Asset Semantic Boundary

### Options

| Option | Shape | Assessment |
|---|---|---|
| A | One shared prompt version namespace for owner templates and provider adapter prompts | Simple label, but repeats today's overload |
| B | Split owner business prompt version and provider adapter version | Clear boundary, aligns with ADR-011 |
| C | Store only owner prompt version; ignore provider adapter version | Fixes Owner template history but leaves provider prompt drift invisible |

### Recommendation

Choose **B: split source and split recording**.

Recommended `ai_calls` evidence fields:

- `prompt_version_id BIGINT NULL` or NOT NULL after transition: immutable Owner
  business prompt version.
- `prompt_version VARCHAR(80) NOT NULL`: retained legacy/display label during
  transition and for historical rows.
- `provider_adapter_version VARCHAR(80) NOT NULL`: provider/system prompt and
  output-adapter version owned by `services/agent`.

New idempotency keys should use the immutable business prompt id plus provider
adapter version:

```text
submission:{id}:provider:{provider}:model:{model}:promptVersionId:{id}:adapter:{providerAdapterVersion}
```

Rationale:

- Owner-defined prompt templates are LabelHub business assets, similar to schema
  versions and adjudication rules.
- Provider-specific system prompt/output adaptation is provider implementation
  detail and should remain in `services/agent` per ADR-011.
- Recording both versions is the smallest evidence shape that can explain
  future changes in either business scoring behavior or provider adapter
  behavior.

Rejected:

- Shared namespace hides which layer changed.
- Owner-only versioning leaves ADR-011 provider prompt drift untracked.

## 4. Q2: Physical Storage Location

### Options

| Option | Shape | Pros | Cons |
|---|---|---|---|
| A | Owner rule prompt versions in LabelHub DB; provider adapter versions in `services/agent` | Matches domain ownership and ADR-011 | Requires recording two versions on `ai_calls` |
| B | Store both business and provider prompts in LabelHub DB | One table family | Coupled to provider adapter internals; weakens ADR-011 |
| C | Store both in `services/agent`; LabelHub stores only ids | Keeps AI concerns outside API | Owner business configuration leaves LabelHub ownership and becomes hard to audit |

### Recommendation

Choose **A**.

LabelHub DB should own Owner-defined business prompt versions because they are
task-scoped governance assets. `services/agent` should own provider-specific
system prompts, model names, and output adapters. `ai_calls` records both
versions as provenance.

This is compatible with baseline Section 7.1 and T7: "Owner configures prompt
template, variables, and scoring dimensions, saved as prompt_version." The
baseline describes business configuration, not provider adapter internals.

## 5. Q3: AiReviewRule Scope

### Options

| Option | Shape | Workload | Risk |
|---|---|---:|---|
| A | Implement `ai_review_rules` plus `prompt_versions`; rule owns prompt families | Full baseline closure | High |
| B | Implement `prompt_versions` as independent immutable assets first; rules later | Strong provenance foundation | Medium |
| C | Only convert `ai_calls.prompt_version` string to FK with a minimal prompt_versions table | Smallest FK migration | Medium, but leaves Owner configuration mostly unimplemented |

### Recommendation

Split P4 into **P4a + P4b**.

P4a should implement the immutable prompt version foundation and wire AI calls
to it:

- `prompt_versions` table;
- `ai_calls.prompt_version_id`;
- `ai_calls.provider_adapter_version`;
- idempotency key migration;
- OpenAPI DTOs for prompt version read/selection;
- backend service path that resolves a submitted prompt version id and binds it
  to `ai_calls`.

P4b should implement `ai_review_rules` as the Owner-facing container:

- rule draft/current state;
- prompt version publishing from rule edits;
- frontend rule editor/version list;
- task publish guard integration if "review rule configured" becomes required.

Why not all at once:

- `AiReviewRule` has no backend storage today, so implementing it fully also
  means designing rule lifecycle, UI, ownership, publish semantics, and task
  guard behavior.
- P4a alone already changes DB, OpenAPI, idempotency, service signatures,
  failed-call recording, frontend trigger flow, and evidence DTOs.
- P3b showed that contract/type/storage changes can hide production-path
  hazards. Splitting keeps the first phase focused on provenance immutability.

## 6. Q4: ai_calls.prompt_version Migration Strategy

### Recommendation

Use an **additive dual-write transition**.

Migration shape for P4a:

1. Add `prompt_versions`.
2. Add `ai_calls.prompt_version_id BIGINT NULL`.
3. Add `ai_calls.provider_adapter_version VARCHAR(80) NOT NULL DEFAULT 'agent-default-v1'`
   or equivalent provider-adapter constant.
4. Keep `ai_calls.prompt_version VARCHAR(80) NOT NULL`.
5. New AI calls write all three fields:
   - `prompt_version_id`;
   - `prompt_version` display/snapshot label;
   - `provider_adapter_version`.
6. New idempotency keys use `prompt_version_id` and `provider_adapter_version`.
7. Historical rows with only `prompt_version` remain readable.

Why not hard cut:

- `prompt_version` is already present in provenance UI, export artifacts, tests,
  failed-call recorder, and idempotency keys.
- Hard cut requires backfilling every historical `ai_calls` row to a concrete
  prompt version record. The local dev DB has zero rows, but real deployments or
  reviewer smoke data may not.
- Additive transition keeps old evidence readable while new evidence becomes
  immutable.

Backfill policy:

- If P4a finds existing `ai_calls` rows, leave their `prompt_version_id` NULL
  unless a migration can create a clear "legacy imported" prompt version without
  pretending to know the original template text.
- The local dev DB currently has `0` `ai_calls` rows, so no local data migration
  is needed for the active workspace.
- Tests should include at least one legacy row with NULL `prompt_version_id` so
  DTO/provenance/export paths remain backward-compatible.

Idempotency:

- Legacy keys remain as stored.
- New keys should be version-id based:

```text
submission:{submissionId}:provider:{provider}:model:{model}:promptVersionId:{promptVersionId}:adapter:{providerAdapterVersion}
```

M6-P4a max-length watch:

- Numeric ids and short adapter version labels reduce idempotency key length
  risk for new calls.
- If legacy `promptVersion` remains accepted in an API transition path, OpenAPI
  must add `maxLength: 80` and the service must fail fast before key
  construction if a legacy label exceeds the DB/key budget.

## 7. Schema Versioning Analogy

| Schema versioning | Prompt versioning recommendation |
|---|---|
| `label_schemas` is mutable family/root | `ai_review_rules` should be mutable family/root in P4b |
| `schema_versions` are immutable published snapshots | `prompt_versions` should be immutable prompt snapshots |
| `(schema_id, version_no)` unique | `(rule_id or prompt_family_id, version_no)` unique |
| `content_hash` unique per schema family | `content_hash` or `template_hash` unique per prompt family |
| `label_schemas.current_version_id` points to current version | `ai_review_rules.current_prompt_version_id` in P4b |
| `tasks.current_schema_version_id` binds task publish | a future `tasks.current_prompt_version_id` or rule binding may be needed |
| `sessions` and `submissions` bind schema_version_id | `ai_calls.prompt_version_id` binds AI evidence to prompt version |
| historical submissions never reinterpret old schema | historical AI calls never reinterpret old prompt template |

Key difference:

- Schema versions are required for labeler answer rendering and validation.
- Prompt versions are required for AI evidence reproducibility. They do not
  change labeler form semantics, but they do change AI judgment and scoring
  explanation.

## 8. Proposed P4 Split

P4 should be split because the full baseline surface is larger than P3b.

### P4a: Prompt Version Foundation And AI Call Binding

| Cluster | Scope | Key risks |
|---|---|---|
| Research | This document | Scope split and storage boundary |
| C1 | OpenAPI + migrations: `prompt_versions`, `ai_calls.prompt_version_id`, `provider_adapter_version`, generated types | Contract churn; migration/backfill semantics |
| C2 | Backend prompt version entity/mapper/service + default prompt version seed/resolution | Immutable snapshot hash and ownership model |
| C3 | AiReviewService / FailedAiCallRecorder / DTOs use promptVersionId while preserving legacy promptVersion display | Idempotency key format, failed attempt evidence, backwards compatibility |
| C4 | Frontend trigger flow selects/sends promptVersionId; provenance displays id + label | Owner UI minimalism versus full rule editor creep |
| C5 | Integration/regression tests: idempotency hit, different promptVersionId creates new ai_call, legacy rows still render | Existing AI tests rely on `prompt-v1` strings |
| C6 | Verification doc + humanpending update | R8 trail for dual-write transition |

Expected size: medium-large, likely 1200-1700 hand-authored lines plus
generated/migration churn. Comparable to P3a, smaller than full P3b, but risky
because it touches idempotency and evidence contracts.

### P4b: AiReviewRule Owner Configuration

| Cluster | Scope | Key risks |
|---|---|---|
| C1 | OpenAPI + migration for `ai_review_rules` family/root and current prompt pointer | Rule lifecycle and task binding |
| C2 | Backend rule service: save draft/publish prompt version/list versions | Ownership and immutable prompt version creation |
| C3 | Frontend rule editor/list, likely minimal first | UI scope creep |
| C4 | Task setup/publish guard integration if rule becomes required | Existing task setup UX and migration for tasks without rules |
| C5 | Integration tests + verification | Browser evidence and prompt-version provenance |

Expected size: medium-large, 1000-1600 hand-authored lines. It should not be
merged into P4a unless the user accepts a P3b-sized phase.

## 9. If User Rejects Split

If P4 must remain one phase, use this sequence:

1. C1 contract + migrations for prompt versions and rules.
2. C2 backend prompt version service.
3. C3 AiReviewService/idempotency migration.
4. C4 backend AiReviewRule service.
5. C5 frontend prompt/rule editor.
6. C6 integration and compatibility tests.
7. C7 verification and humanpending.

This is likely P3b-scale or larger. The recommended safer route is P4a/P4b.

## 10. P4a Starting Anchors And Expected Baseline Shifts

P4a C1 should expect:

- OpenAPI MD5 changes from `2482d531df39e9e12613bf964f3618ea`.
- Migrations increase from `11` to at least `12`.
- If `prompt_versions` and `ai_calls` alterations are split for safety, the
  migration count may become `13`.
- humanpending remains `141` until the P4a closure cluster, unless a gate
  explicitly adds a watch entry earlier.
- Backend test count will rise from `494 / 81`.
- Frontend Vitest will rise from `125` if frontend trigger/provenance surfaces
  change.

## 11. Recommended C1 Pre-Estimate Questions

Before P4a C1 implementation, adjudicate:

1. Should `prompt_versions` be global/system-capable with nullable
   `task_id/rule_id`, or require an `ai_review_rules` root immediately?
2. Should P4a expose prompt version creation API, or seed only a default prompt
   version and postpone owner editing to P4b?
3. Should `TriggerAiReviewRequest` switch hard to `promptVersionId`, or accept
   both `promptVersionId` and legacy `promptVersion` during transition?
4. What should `provider_adapter_version` be named in OpenAPI and DB?
5. Should task publish require a prompt version in P4a, or wait for P4b rule
   integration?

## 12. Research Recommendation

Proceed with **P4a first**:

```text
PromptVersion foundation + ai_calls FK binding + idempotency migration,
without full AiReviewRule editor.
```

Then enter **P4b**:

```text
AiReviewRule family/root + owner rule editor + prompt version publishing UI.
```

This split keeps P4a focused on evidence reproducibility, closes the immediate
design break in `ai_calls.prompt_version`, and avoids turning one phase into a
combined DB, idempotency, AI service, and UI-rule-editor project.

## 13. Closed Open Questions Before P4a

This section closes the four questions that should not be left to the P4a C1
gate. Sections 1-12 remain the approved research baseline; where Section 11
still lists a question, the decisions below supersede it and C1 should treat
the question as already adjudicated.

### 13.1 Provider Adapter Version Placeholder

P4a should add and write `provider_adapter_version`, but it should not try to
derive a real adapter version from `services/agent` yet.

Recommended P4a behavior:

- Write a constant placeholder such as `agent-default-v1`.
- Store that value on every new `ai_calls` row.
- Include it in the new idempotency key.
- Keep the field explicit in DTOs/provenance so the evidence shape is ready for
  a future real adapter-version source.

Reasoning:

- ADR-011 keeps provider-specific prompts, model names, and output adapters in
  `services/agent`.
- P4a is the LabelHub evidence-binding phase, not an agent release/versioning
  phase.
- A fake dynamic integration would be worse than a named constant, because it
  would make the evidence field look stronger than it is.

Required P4a closure watch entry:

```text
- [M7-P4a watch] provider_adapter_version is a constant placeholder (`agent-default-v1`) in P4a. It is recorded on ai_calls and included in new idempotency keys so evidence has a future-proof slot for provider adapter provenance, but it is not yet linked to a real services/agent adapter release. A later agent-versioning cluster must replace the constant with a real adapter version source.
```

### 13.2 TriggerAiReviewRequest Compatibility Strategy

Recommendation: **hard switch the trigger contract to `promptVersionId` in P4a**.

Do not support both `promptVersion` and `promptVersionId` on the trigger request
unless C1 discovers a production compatibility requirement that is not visible
from the current codebase.

Expected changes:

- OpenAPI `TriggerAiReviewRequest.required` changes from `[promptVersion]` to
  `[promptVersionId]`.
- Frontend `useTriggerAiReviewMutation` variables change from
  `{ submissionId, promptVersion }` to `{ submissionId, promptVersionId }`.
- `OwnerSubmissionPage` stops using the hard-coded
  `DEFAULT_PROMPT_VERSION = 'm3-owner-review-v1'` string and sends a backend
  supplied default/current prompt version id instead.
- Backend `AiReviewController` passes `promptVersionId`.
- `AiReviewService.review(Long submissionId, Long ownerId, String promptVersion)`
  becomes id-based, for example
  `review(Long submissionId, Long ownerId, Long promptVersionId)`.
- The service resolves the immutable prompt version record, then writes both
  `prompt_version_id` and the display/snapshot `prompt_version` label on
  `ai_calls`.

Why not a dual-field request:

- The trigger path has one frontend mutation hook and one controller path, so a
  same-commit hard switch is tractable.
- Dual-field compatibility would force both frontend and backend to carry a
  long-lived precedence rule (`promptVersionId` wins? string fallback?) for a
  request that should be unambiguous.
- Accepting legacy strings after P4a weakens the evidence model: callers could
  still trigger AI reviews without binding to an immutable prompt version.
- The additive transition is still preserved at the storage/response layer:
  legacy `ai_calls.prompt_version` rows remain readable, but new trigger calls
  must bind to a prompt version id.

This decision removes Q3.3 from the P4a C1 gate.

### 13.3 AiCall Response And Frontend Workload

OpenAPI `AiCall.required` currently includes `promptVersion`, and the generated
frontend type exposes:

```ts
AiCall: {
  id: number;
  submissionId: number;
  promptVersion: string;
  providerName: string;
  modelName: string;
  // ...
}
```

Recommendation for P4a response shape:

- Keep `AiCall.promptVersion` required as the human-readable legacy/display
  label.
- Add `AiCall.promptVersionId` as a nullable id field (`number | null` in the
  generated frontend type) so legacy rows can still be represented without
  inventing a fake FK.
- Add `AiCall.providerAdapterVersion` as required because P4a will backfill or
  default it for every row through the migration/default.

This avoids making `promptVersion` optional and keeps existing provenance cards
from losing their display label. New UI can show both values when an id exists,
for example `Prompt: m3-owner-review-v1 (#12)`.

Frontend grep found these direct consumers:

| Surface | File | P4a work |
|---|---|---|
| Trigger request | `apps/web/src/features/ai/useTriggerAiReviewMutation.ts` | Change variables/body to `promptVersionId`. |
| Trigger caller | `apps/web/src/pages/owner/OwnerSubmissionPage.tsx` | Replace hard-coded `DEFAULT_PROMPT_VERSION` string with a backend supplied prompt version id. |
| Immediate review drawer | `apps/web/src/features/ai/AiReviewDrawer.tsx` | Continue showing `promptVersion`; optionally append `promptVersionId` and adapter version. |
| Provenance card | `apps/web/src/features/ai/AiProvenanceCard.tsx` | Continue showing `promptVersion`; optionally append `promptVersionId` and adapter version. |
| Provenance hosts | `OwnerSubmissionPage`, `LabelerSubmissionPage`, `ReviewerSubmissionPage` | No direct change if `AiProvenanceCard` owns the display update. |
| Contract typecheck | `apps/web/src/__typechecks__/m3p4-owner-ai.contract.tsx` | Update request shape and any sample `AiCall` expectations. |
| Generated types | `apps/web/src/shared/api/generated/schema.d.ts` | Regenerated in C1. |

Backend/export consumers also exist and should be handled outside the frontend
C4 estimate:

- `ExportArtifactBuilder` writes `promptVersion` today; P4a should keep that
  label and add `promptVersionId`/`providerAdapterVersion` to trusted export
  evidence if the export schema is updated in the backend cluster.

Workload judgment:

- Direct frontend prompt-version consumers are fewer than five code surfaces if
  display is centralized in `AiReviewDrawer` and `AiProvenanceCard`.
- P4a does **not** need to split frontend work into C4a/C4b based on current
  grep.
- C4 should cover both the trigger hard switch and provenance display update,
  with focused tests for the mutation body and display fallback for
  `promptVersionId: null`.

This closes the frontend scope question without changing the §10 test-count
forecast; frontend tests will rise, but one frontend cluster remains realistic.

### 13.4 Legacy Idempotency Key Query Behavior

Recommendation: **new P4a reviews should query only the new idempotency key
format. Do not add a fallback query for legacy string-format keys.**

New key format remains:

```text
submission:{submissionId}:provider:{provider}:model:{model}:promptVersionId:{promptVersionId}:adapter:{providerAdapterVersion}
```

Expected behavior:

- Existing legacy rows keep their stored idempotency keys.
- New P4a trigger calls compute the new id-based key and call
  `aiCallMapper.selectByIdempotencyKey(newKey)`.
- A legacy row keyed by
  `submission:{id}:provider:{p}:model:{m}:prompt:{promptVersion}` will **not**
  be reused by the new id-based query.
- If the same submission is reviewed after P4a with a real `promptVersionId`,
  the service creates a new `ai_calls` row under the new key.
- Both rows remain visible in provenance/export history.

Why this is acceptable:

- Reusing a legacy row under the new FK path would imply that the old string
  label is equivalent to a specific immutable prompt template. That is exactly
  the ambiguity P4a is meant to remove.
- Local development has zero existing `ai_calls` rows, so there is no local
  backfill pressure.
- Real deployments may have legacy rows, but those rows remain readable as
  historical evidence rather than being silently upgraded to immutable prompt
  evidence.

No compatibility query should be added:

- Do not compute both old and new keys in normal review flow.
- Do not query `selectByIdempotencyKey(oldKey)` as a fallback after the new key
  misses.
- Do not mutate old rows to attach a guessed `prompt_version_id`.

Required P4a C5 test coverage:

- Seed or insert a legacy `ai_calls` row with a legacy string idempotency key and
  `prompt_version_id = NULL`.
- Trigger review with a concrete `promptVersionId`.
- Assert the service does **not** return an idempotency hit from the legacy row.
- Assert a new row is created with `prompt_version_id`, `provider_adapter_version`,
  and the new idempotency key.
- Assert provenance/export still renders the legacy row's `promptVersion` label
  without requiring a prompt version FK.

This closes the P3b-C3.5-style production-path risk: tests must exercise the
real idempotency lookup path, not only hand-constructed DTOs.

With these decisions, the previously open C1-gate questions about trigger
compatibility, provider adapter naming, frontend AiCall response shape, and
legacy idempotency lookup behavior are closed for P4a.
