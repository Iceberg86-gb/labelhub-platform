# Backend Batch A Scope-Budget: LLM Provider Config And Secret Vault

## Status

Pre-estimate gate for Backend Batch A. No implementation code has landed in
this batch.

Current branch: `codex/backend-llm-provider-config`.
Current anchor: `042e66d`.
OpenAPI MD5 remains `5102e4e97b9f842248aca651681b7b82`; migrations `21`;
humanpending `189`.

Batch A builds the LLM Provider "key cabinet": encrypted provider secret
persistence, Owner-only management API, frontend LLM connection page API wiring,
and test connection. It does **not** switch P-A AI review or field-assist
runtime provider resolution to the new DB-backed config. That source-of-truth
switch belongs to Batch B.

## Phase Character

Batch A is load-bearing and security-sensitive:

- adds migration `21 -> 22` for a provider configuration table;
- adds OpenAPI contract surface and generated types;
- adds Owner-only CRUD/test-connection APIs;
- stores provider secrets in the database, encrypted only;
- touches frontend Owner LLM settings to use real APIs;
- must close the research-discovered audit/log redaction gap.

## Research Premises To Re-Verify Before Implementation

- Current provider/key source is env/config driven through
  `labelhub.ai.active-provider` and `labelhub.ai.openai-compatible.*`.
- Existing provider abstraction exists: `AiProvider`, `MockAiProvider`,
  `OpenAiCompatibleProvider`.
- No provider table/entity/CRUD API/test-connection endpoint exists.
- No reusable DB secret encryption/master-key mechanism exists.
- `AiReviewService` receives an injected `AiProvider`; it does not directly
  read a key. `OpenAiCompatibleProvider` reads the env-backed key.
- `AuditLogServiceImpl` persists caller-provided payloads as-is. Provider API
  implementation must avoid passing secrets to audit and should add a redaction
  guard/test rather than assuming callers will remember.

## Allowed Surfaces

Contract and generated types:

- `packages/contracts/openapi/labelhub.yaml`
- generated TypeScript API types in `apps/web/src/shared/api/generated/`
- generated Java API/model sources, if this repository workflow regenerates
  them into tracked paths

Database:

- one new migration under `services/api/src/main/resources/db/migration/`

Backend provider config module:

- new entity/mapper/service/controller package under
  `services/api/src/main/java/com/labelhub/api/module/ai/` or a new
  `module/provider` area following existing module conventions
- security/config properties for a dedicated LLM secret master key
- redaction helper/guard where provider management audit/error payloads are
  assembled

Frontend Owner LLM settings:

- `apps/web/src/pages/owner/OwnerLlmSettingsPage.tsx`
- focused hooks under `apps/web/src/features/llm-provider/` or equivalent
- tests for the Owner LLM connection page and hooks

Tests:

- backend migration/entity/mapper/service/controller/security tests
- backend crypto/redaction tests
- frontend page/hook tests

## Forbidden Surfaces

Strictly do not change these in Batch A:

- `AiReviewService` runtime provider selection/call path
- `OpenAiCompatibleProvider` bean selection or env-backed key behavior used by
  P-A, except for non-behavioral reuse in a separate test-connection helper
  if no runtime path is changed
- `FieldAssistService` runtime provider source
- AI review idempotency key format, retry policy, failed-call recording, scoring,
  quality ledger, or review verdict behavior
- reviewer/labeler business flows outside frontend navigation to the LLM page
- ADR-011 default/fallback/source-of-truth semantics
- `humanpending.md`, unless the implementation gate is explicitly expanded

## Scope

### 1. Provider Table + Migration

Create one provider configuration table in migration `22`.

Required columns:

- `id BIGINT PRIMARY KEY AUTO_INCREMENT`
- `owner_id BIGINT NOT NULL`
- `provider_type VARCHAR(...) NOT NULL`
- `provider_name VARCHAR(...) NOT NULL`
- `base_url VARCHAR(...)`
- `model_name VARCHAR(...) NOT NULL`
- `secret_ciphertext TEXT`
- `secret_last4 VARCHAR(8)`
- `secret_updated_at DATETIME(3)`
- `secret_ref VARCHAR(...)`
- `enabled BOOLEAN NOT NULL DEFAULT TRUE`
- `created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`
- `updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)`

Hard ban:

- no `api_key_plain`, `api_key`, `secret_plain`, `raw_secret`, or equivalent
  plaintext secret column;
- no migration default that embeds a real secret;
- no seed containing a real provider key.

Recommended constraints:

- FK `owner_id -> users(id)`;
- index by `(owner_id, enabled)`;
- unique owner-scoped provider name if product expects names to be stable.

### 2. Secret Encryption And Write-Only Semantics

Add a dedicated secret encryption service.

Requirements:

- master key comes from an independent env-backed property, not `JWT_SECRET`;
- startup or first-use validation must fail clearly when the master key is
  missing or malformed;
- encrypt plaintext secret before writing `secret_ciphertext`;
- derive and store only `secret_last4` and `secret_updated_at` metadata;
- never expose a decrypt/display method through controller/service APIs;
- update secret means overwrite ciphertext + last4 + updatedAt;
- clear secret, if supported, must clear ciphertext + last4 + updatedAt while
  preserving config metadata and audit-safe payload.

### 3. Provider CRUD API + OpenAPI

Add Owner-only OpenAPI paths for provider management. Exact path names can be
chosen during implementation, but must be grouped and consistent, for example
`/llm/providers`.

Minimum operations:

- create provider config;
- list provider configs;
- get provider config detail;
- update provider config metadata and optional new secret;
- delete or disable provider config;
- test connection.

Response DTO hard rule:

- response may include `hasSecret`, `secretLast4`, `secretUpdatedAt`,
  `secretRef`;
- response must not include plaintext secret, ciphertext, encryption IV/tag,
  master-key identifiers, or request DTO echo containing the secret.

Request DTO:

- may accept plaintext `secret` for create/update/test;
- may accept `secretRef`;
- must make secret optional on metadata-only update so key is not required for
  non-secret edits.

OpenAPI churn must be listed separately from generated churn.

### 4. Owner Permission

Provider management API is Owner-only.

Required checks:

- `OWNER` succeeds;
- `LABELER`, `REVIEWER`, and `SENIOR_REVIEWER` receive forbidden/unauthorized
  according to existing auth conventions;
- unauthenticated access is rejected;
- service queries are owner-scoped so one owner cannot operate another owner's
  provider config.

### 5. Test Connection

Add a test-connection API that validates the config and makes a lightweight
provider request.

Requirements:

- validates required fields before network call;
- uses submitted secret for one-shot tests or stored encrypted secret for saved
  configs;
- does not write the submitted test secret unless the caller is explicitly
  saving/updating;
- response includes status, provider code/status, and latency-style diagnostic
  fields where useful;
- response/log/audit/error never include full secret.

The lightweight request may reuse OpenAI-compatible HTTP behavior, but must not
trigger P-A AI review business logic, write `ai_calls`, write ledger entries,
or change AI review idempotency/metrics.

### 6. Frontend LLM Settings Page

Replace local-only LLM settings behavior with real API integration.

Requirements:

- load provider configs from API;
- create/update/delete or enable/disable provider configs;
- test connection through backend API;
- display only `hasSecret`, `secretLast4`, `secretUpdatedAt`, `secretRef`;
- never render or cache the full saved secret after save;
- clear secret input after successful save/test where appropriate;
- preserve UI principle that AI assistance is evidence only and not final
  human adjudication.

### 7. Security Redaction Guard

This is a hard gate item because research found the audit service persists
caller payloads as-is.

Implementation must add one of:

- a small redaction helper used by provider management audit/error payload
  assembly; or
- focused tests proving provider management audit/error payloads never contain
  submitted secret values, even though the audit service itself is generic.

Required coverage:

- create with secret does not persist secret in audit payload;
- update with new secret does not persist secret in audit payload;
- test connection with one-shot secret does not persist secret in audit payload;
- provider failure/error response does not include secret;
- exception messages do not include request DTO/full secret.

## ADR-011 Relationship

Batch A is an **extension** of ADR-011 if it only adds provider configurability
and secret storage while leaving default/fallback/runtime AI review
source-of-truth unchanged.

Stop and report if implementation requires:

- changing `AiReviewService` to read DB provider configs;
- changing `OpenAiCompatibleProvider` runtime key source for P-A;
- changing default provider/fallback semantics;
- changing services/agent `LlmProvider` source-of-truth.

Any of those moves is Batch B or an ADR-011 revision requiring owner
adjudication and ADR backfill.

<!-- BACKEND-BATCH-A-CAP-BEGIN -->
## CAP Block

Baseline anchor: `042e66d`.
Baseline OpenAPI MD5: `5102e4e97b9f842248aca651681b7b82`.
Baseline migrations: `21`.
Expected migration count after Batch A implementation: `22`.
Baseline humanpending: `189`.

Hand-authored estimate:

| Surface | Estimate |
|---|---:|
| OpenAPI provider schemas and paths | 180 |
| Provider config migration `21 -> 22` | 90 |
| Provider entity/mapper/repository/service | 260 |
| Secret encryption config/service | 220 |
| CRUD controller and DTO mapping | 240 |
| Test connection service/client/endpoint | 220 |
| Owner authorization and owner scoping | 90 |
| Audit/log/error redaction guard | 150 |
| Frontend LLM settings API hooks/page wiring | 300 |
| Backend tests | 430 |
| Frontend tests | 180 |
| Docs/report updates | 60 |
| **Hand-authored total** | **2420** |

Generated churn:

| Generated surface | Budget |
|---|---:|
| OpenAPI generated Java/TS types | Report separately; not counted in hand-authored cap |

Recommended caps:

- soft cap: `2600` hand-authored lines;
- hard cap: `3200` hand-authored lines;
- generated churn must be reported separately;
- if hand-authored diff exceeds `3200`, stop and rescope before continuing.

OpenAPI churn:

- expected to change from MD5 `5102e4e97b9f842248aca651681b7b82` to a recorded new value;
- must be limited to provider config/test-connection schemas and paths;
- must not alter P-A AI review trigger/result contracts unless owner explicitly re-adjudicates Batch B.

Migration churn:

- expected `21 -> 22`;
- migration must create provider config storage only;
- migration must not add plaintext secret columns.

Regression guard:

- `AiReviewService` has empty diff;
- `FieldAssistService` has empty diff;
- P-A AI review idempotency, retry, scoring, ledger, and provider source-of-truth stay unchanged;
- existing auth, task, designer, labeler, reviewer, export, and config pages keep current routes unless only adding the LLM provider API wiring.

Security tests required:

- API responses never include plaintext secret or ciphertext;
- create/update/test connection audit payloads do not contain submitted secret values;
- exception messages and error responses do not contain submitted secret values;
- master key missing/malformed path fails predictably;
- secret update overwrites ciphertext/last4/updatedAt and provides no read-back plaintext;
- owner can manage providers; labeler/reviewer/senior reviewer cannot;
- test connection request/response/log/audit path does not leak full key.

D-口径:

- Browser verification for the LLM settings page may remain D-口径 if Browser is unavailable;
- full Maven backend suite may remain D-口径 if sandbox blocks DB/network resources, but focused tests must be identified and run where available;
- no screenshots or passing test claims may be fabricated.
<!-- BACKEND-BATCH-A-CAP-END -->

## Stop Conditions

Stop and ask for owner adjudication if:

- implementation needs to modify `AiReviewService`, `FieldAssistService`, or
  P-A provider source-of-truth;
- provider API response shape needs to include plaintext secret or ciphertext;
- audit/log/error redaction cannot be proven by tests;
- migration design introduces plaintext secret columns;
- master key is proposed to reuse `JWT_SECRET`;
- test connection writes `ai_calls`, ledger entries, or review metrics;
- OpenAPI changes touch AI review trigger/result contracts;
- hand-authored diff exceeds the hard cap in the CAP block.

## Verification Plan For Implementation

Required before implementation closure:

- record new OpenAPI MD5;
- record migrations `21 -> 22`;
- prove only one new provider-config migration was added;
- run focused backend tests for migration, crypto, provider service, CRUD,
  permissions, test connection, and audit redaction;
- run focused frontend tests for LLM settings API integration;
- run typecheck/build where available;
- prove forbidden P-A files have empty diff;
- prove `git status --short` clean after commit.

## Gate Questions

1. Approve Batch A as ADR-011 extension only, with no runtime AI review
   source-of-truth switch.
2. Approve dedicated LLM provider master key property, not `JWT_SECRET` reuse.
3. Approve response DTO safe fields:
   `hasSecret/secretLast4/secretUpdatedAt/secretRef`.
4. Approve audit/log/error redaction as a hard implementation requirement with
   dedicated tests.
5. Approve cap: soft `2600`, hard `3200` hand-authored lines, generated churn
   separate.
